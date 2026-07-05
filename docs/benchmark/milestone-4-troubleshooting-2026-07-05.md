# M4 재배포·§4 측정 세션 — 트러블슈팅 리포트 (2026-07-05)

> 단일 EC2 blue-green 무중단 배포의 **첫 라이브 검증**과 §4 유실률 측정을 시도한 세션의 기록.
> 지표 나열보다 **"증상 → 무엇을 의심했고 → 어떻게 갈랐는가"의 과정**을 중심으로 서술한다.
> 실측 수치는 짝 문서 `milestone-4-run-report.md`, 상태·다음 계획의 정본은 루트 `m4-worklog.md`.

## 0. 이 세션이 한 일 (한눈에)

인프라를 0에서 다시 세워(apply → RDS 재시드 → EC2 재프로비저닝) **SSM Run Command 기반 배포를 처음으로 라이브에서 돌렸다.** 그 과정에서 예상 못 한 결함 두 개를 만났고, 둘 다 **이전 세션들은 배포 자체가 실패해 도달하지 못했던 지점**에서 처음 드러났다.

- 결함 1 — **nginx→Tomcat 프록시 400** (수정·커밋 완료, `8dc97eb`)
- 결함 2 — **Redis 크로스컨테이너 캐시 역직렬화 → 401** (진단 완료, 수정은 다음 세션)

부수적으로 **Neo4j→MySQL CTE 마이그레이션의 정확성을 프로덕션 규모 실데이터로 증명**했다.

배포 파이프라인 자체(test skip → 이미지 빌드·push → OIDC → `aws ssm send-command` → `runuser -l ec2-user` → `switch-backend.sh`)는 **green 으로 완주**했고 blue-green 전환 로그도 교과서적으로 깨끗했다. 문제는 전환 "메커니즘"이 아니라 전환 이후 **새 컨테이너가 서비스하는 데이터 경로**에 있었다.

---

## 1. 첫 번째 추적 — 배포는 성공했는데 왜 400인가

### 증상
SSM 배포가 `deploy=success`로 끝나고 `mmt-backend-blue`가 떴다. fragment도 blue를 가리켰다. 그런데 **외부 smoke가 전부 400** — 의존성 없는 `/api/v1/health`조차 400. 다만 **응답이 빨랐다**(타임아웃이 아니라 즉답 400).

### 무엇을 의심했나
"빠른 400"은 백엔드가 죽어서가 아니다(죽었으면 502 또는 지연). 무언가가 요청을 **받고 즉시 거부**하고 있었다. 경계는 둘 중 하나 — nginx 자체, 또는 nginx가 프록시한 백엔드.

### 어떻게 갈랐나
같은 요청을 **두 경로로** 쳤다:
- **docker 네트워크 내부에서 백엔드 직접** `http://mmt-backend-blue:8080/...` → **`/health` 200, `/concepts/nodes/7925` 200 + 실데이터.** 백엔드는 완벽히 정상.
- **외부에서 nginx 경유** → **400**(Tomcat 에러 페이지).

→ 경계가 **nginx↔백엔드 사이**로 좁혀졌다. 백엔드는 정상이므로 nginx가 백엔드를 부를 때 뭔가를 잘못 보내고 있다.

### 확진
백엔드 로그가 정확히 말해줬다:
```
The host [mmt_backend] is not valid
java.lang.IllegalArgumentException: The character [_] is never valid in a domain name.
    at ...HttpParser.readHostDomainName (tomcat-embed-core-10.1.16)
```
`nginx.conf`의 `upstream mmt_backend { ... }`에서 `proxy_pass http://mmt_backend`는 **upstream 이름을 그대로 Host 헤더로** 흘려보낸다. 그 이름의 **언더스코어**를 Tomcat 10.1의 엄격한 Host 파서가 RFC 위반으로 보고 400을 냈다.

### 왜 이제서야 드러났나 (맥락)
`nginx.conf`는 그동안 `nginx -t`(문법)와 fragment 형태까지만 검증돼 있었다. **실제 프록시 Host 동작은 라이브 배포가 성공해야 처음 실행되는데, 이전 세션들은 배포가 그 앞(테스트 게이트·DockerHub 인증)에서 죽어 여기까지 온 적이 없었다.** 이 결함은 "첫 성공한 배포"에서 비로소 표면화됐다.

### 수정
`server` 블록에 `proxy_set_header Host $host`(+ `X-Real-IP`/`X-Forwarded-For`/`X-Forwarded-Proto`) 추가로 원래 Host 보존. reload 후 재검증 → `/health` 200, `/nodes/7925` **200 + 노드 36개**. 커밋 `8dc97eb`. (EC2는 bind-mount라 이미지 재빌드 없이 reload로 반영.)

---

## 2. 곁가지 수확 — CTE 마이그레이션 정확성 (라이브 증명)

인프라가 살아있는 김에 destroy하면 못 얻을 데이터를 확보했다: **Neo4j→MySQL CTE 마이그레이션이 프로덕션 규모 실데이터에서 정확한가.**

`shared/benchmark/neo4j-snapshot-20260622.json`(Neo4j 시절 오라클)의 `concept_ids`를 라이브 RDS 재귀 CTE로 재현·대조:

| 쿼리 | 기대 unique 집합 | 라이브 CTE 결과 |
|---|---|---|
| conceptId 6646, depth 2 | 10개 | **10개, 완전 일치** ✅ |
| conceptId 6646, depth 3 | 14개 | **14개, 완전 일치** ✅ |
| conceptId 7595, depth 2 | 4개 | **4개, 완전 일치** ✅ |

`EXPLAIN`도 `idx_knowledge_space_composite`를 **"Using index"(커버링)**로 타는 걸 확인 — M1 인덱스 산출물이 실제로 먹는다.

> **stale 문서 발견:** `api/CLAUDE.md`의 `Optional<MysqlConceptRepository>` "스텁"은 **존재하지 않는다.** 실제 재귀 CTE는 `JdbcTemplateConceptRepository.findPrerequisitesWithDepth / findPrerequisiteConcepts`(`WITH RECURSIVE`). `/nodes/{id}` depth는 school_level로 결정(초등 3, 그 외 5). 스냅샷의 `count`(37/105/…)는 Neo4j 경로 multiset이라 CTE(`GROUP BY` unique)로 재현 불가하며, 동치성 게이트도 **Set 비교만** 하도록 설계돼 있다. → 문서 정정 필요.

---

## 3. 두 번째 추적 — After가 100% 실패하다 (이 세션의 핵심)

### 측정 설계와 RATE 보정 (선행 맥락)
§4는 "구식 in-place 재배포 vs blue-green"의 유실률을 같은 부하로 비교한다. 대표 GET(`/concepts/nodes/7925`, 실제 DB+CTE를 태우는 30KB 응답)을 부하 대상으로 골랐다.

RATE 보정에서 이미 하나가 걸렸다: **30rps에서 붕괴**(VU가 cap까지 치솟고 처리량 폭락) — t3.micro(1 vCPU)의 천장. 10rps는 p95 137ms로 깨끗. **페어니스상 steady가 실패 0인 RATE(=10)**를 써야 Before의 실패가 "과부하"가 아니라 "재배포 갭"에서만 나온다.

- **① steady (10rps):** 실패 0%. 기준 정상성 확인.
- **② Before (구식 in-place `docker restart`):** **유실 60.3%**(900건 중 543건 502). 단일 백엔드 재기동 동안 nginx가 502를 쏟았다 → 다운타임 명백, 비교 기준 성립.

### 충격 — After가 60%가 아니라 100% 실패
blue-green으로 컷오버하며 같은 부하를 걸었더니 **1301건 전부 실패.** Before(60%)보다 나쁘다. 첫 반응: "전환이 파국적으로 깨졌나?"

### 방향을 튼 두 관찰
서둘러 결론 내리지 않고 숫자의 **모양**을 봤다:
1. **지연이 정상**(p95 135ms) — 타임아웃/무응답이 아니라 **빠른 non-2xx**. 전송이 끊긴 게 아니라 서버가 빠르게 거부.
2. **100%(전 구간)** — 컷오버 "순간"에 몰린 게 아니라 창 전체. 컷오버 갭이라면 부분 실패여야 한다. 전 구간 실패는 **컷오버가 아니라 상태(state)** 문제를 시사.

### 좁혀가기
- 라이브 상태: 외부 `/health` **200**, `/nodes/7925` **401** → 엔드포인트별로 갈림.
- green **직접**(docker 내부) `/nodes/7925`도 **401** → nginx가 아니라 **green 백엔드 자체**.
- 그런데 blue는 같은 이미지·같은 env-file로 200이었다. 같은 것이 왜 다르게 행동하는가?

### "401"이라는 미끼
green 로그의 진짜 원인은 인증이 아니었다:
```
java.lang.ClassCastException: class java.lang.String cannot be cast to class java.util.List
    at ...ExceptionTranslationFilter...
```
요청 처리 중 **String→List 캐스팅 실패**가 나고, Spring Security의 `ExceptionTranslationFilter`가 이 예외를 **401로 변환**해 내보냈다. 즉 401은 증상이지 원인이 아니었다.

### 가설과 결정적 실험
`/nodes`는 CTE 결과 List를 Redis(`graph:prerequisites:objs:<id>:<depth>`)에 캐시한다. "String→List"는 **캐시에서 읽은 값이 List가 아니라 String**이라는 뜻. blue가 캐시한 값을 green이 잘못 읽는 게 아닐까?

결정적으로 갈랐다:
- Redis에 `graph:prerequisites:objs:7925:5`가 **실제로 존재**(blue가 smoke 때 심음).
- `FLUSHALL` → green `/nodes/7925` **즉시 200.**
- flush 후 green을 10rps로 20초 → **991건 전부 200**(green은 자기가 쓴 캐시는 정상 read).

→ **크로스컨테이너 캐시 역직렬화 결함 확정.** 같은 인스턴스는 자기 write를 정상 read하지만, **다른 컨테이너가 남긴 캐시 값을 String으로 읽어** ClassCastException → 401.

### 재프레이밍 — "무중단"의 정의로 되돌아가기
중요한 재해석. k6의 `http_req_failed`는 4xx도 실패로 센다. 하지만 **무중단의 본질은 "요청이 응답 없이 드롭되는가"(502/커넥션 리셋 = 전송 갭)**이지, **401(서버가 응답함)**이 아니다.

- Before의 실패 = **502**(백엔드 부재 = 진짜 전송 다운타임).
- After의 실패 = **401**(green이 응답함, 전송은 안 끊김).

blue-green 전환 로그도 실제로 깨끗했다:
```
활성=blue → green 기동 → 헬스 OK(5/30) → fragment→green → nginx reload → blue 드레인 → 완료
```
blue가 green이 헬시해질 때까지 서비스하다 fragment를 넘기고 reload 후 drain했다. **어느 순간에도 정상 백엔드 1개 이상 존재 → 전송 갭 0.** After의 "100% 실패"는 컷오버가 요청을 흘린 게 아니라 **전환된 green이 stale 캐시로 401을 응답한** 것이다.

### 이 결함이 진짜 무서운 이유 (메타 발견)
`switch-backend.sh`의 헬스 게이트는 `/api/v1/health`를 폴링한다. `/health`는 캐시·DB를 안 건드리므로 **green이 stale 캐시로 데이터 엔드포인트를 401내는 상태에서도 "헬시"로 통과**한다. → **헬스체크가 데이터 경로를 커버하지 못해, 망가진 컨테이너로 트래픽이 전환된다.** blue-green 스모크 게이트 전반의 공백이다.

---

## 4. 결론 — 무엇이 증명됐고, 무엇이 아직인가

| 항목 | 상태 |
|---|---|
| SSM→runuser 무중단 배포 파이프라인 | ✅ 라이브 첫 완주 |
| blue-green 컷오버 메커니즘(전송 갭 0) | ✅ 로그·정황상 성립(전환 실패는 401=앱, 502=전송갭 아님) |
| nginx→Tomcat 프록시(Host 헤더) | ✅ 결함 수정·커밋 |
| CTE 마이그레이션 정확성 | ✅ 라이브 3/3 일치 + 커버링 인덱스 |
| §4 Before(구식) 유실 | ✅ 60.3% 측정(전송 다운타임) |
| **§4 After 클린 0% 숫자** | ⛔ **Redis 캐시 결함에 막힘 — 다음 세션** |

**정직한 유보:** After의 클린 "유실 0%" 수치는 아직 얻지 못했다 — 캐시 결함이 green의 데이터 응답을 401로 만들어 측정을 오염시켰다. Before도 상태코드 분해 없이 측정돼(502로 추정) apples-to-apples 비교를 위해선 함께 재측정이 낫다.

## 5. 다음 세션 할 일

1. **Redis 크로스컨테이너 캐시 역직렬화 수정** — 의심 지점은 RedisTemplate value serializer(`util/RedisUtil`·RedisConfig)의 인스턴스 간 round-trip. green 로그에 `SPRING_PROFILES_ACTIVE=secure`인데도 `securelocal`이 함께 활성인 점도 점검. **로컬 Testcontainers(백엔드 2인스턴스+redis, 한쪽 warm→다른쪽 read)로 재현·검증 — AWS 불필요.**
2. **스모크 게이트 강화** — `switch-backend.sh` 헬스 폴을 `/health`가 아니라 **대표 데이터 엔드포인트**로(또는 병행). 헬시=데이터 경로 정상이 되게.
3. **Before/After 재측정** — 캐시 수정 후 `loss-probe2.js`(정본 지표 `status_502 + transport_err == 0`)로 둘 다.
4. **PR #45(SSM)** — 라이브 검증 통과 → nginx 커밋 포함 Ready→머지 판단. 캐시 결함을 M4 범위에 넣을지 별도 이슈로 뺄지 결정.
5. **문서 정정** — `api/CLAUDE.md`의 `MysqlConceptRepository 스텁` 서술 stale.
