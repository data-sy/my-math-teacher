# [백로그] 프로덕션 401 — `/error` 디스패치가 모든 오류를 401 로 마스킹 (M7 A4)

> 원 제목: "백엔드가 자기 발급 access 토큰을 401". 2026-07-28 세션 2 에서 **그 전제가 기각**되고
> 진짜 원인이 특정돼 제목을 교체했다. 원문 진단은 §"백로그 원문에 대한 정정" 참조.

- **상태:** 근본원인 2건 확정 / **결함② 코드 완료(미배포)** · **결함① 재런치로 연기** /
  **심각도: 런치 블로커 — 단 2026-07-31 인프라 티어다운으로 프로덕션이 내려가 현재 활성 장애 아님**
- **인증 문제가 아니다.** ① M7 진단 테이블이 프로덕션 RDS 에 미적용(→ 500) + ② `/error` 마스킹이
  그 500 을 401 로 위장. 둘 다 고쳐야 한다. §"진짜 근본원인" 참조.
- **남은 일 (전부 재런치 시점, 사람이 실행):**
  ① 복원된 RDS 에 DDL 적용(`api/sql/m7-apply-diagnosis-ddl-prod.sql`) ·
  ② 백엔드 **재빌드**·배포(구 이미지 `889390a` 는 마스킹 수정·하드닝 **미포함** — 재배포 없이는 무효) ·
  ③ 수동 검증(스크립트는 mothball 때 삭제 — §재현/검증 자산 참조).
  실행 순서 정본 = [`🤖-M7-티어다운-실행시퀀스.md`](../handoff/🤖-M7-티어다운-실행시퀀스.md) **§재런치 런북**.
- **발견:** 2026-07-28, M7 런치 A4(OAuth 401 트리아지) 중
- **영역:** 백엔드 JWT 인증 (`api/`, `com.mmt.api.jwt`). **M7 프론트 브랜치(`feat/m7-item-selection`)와 무관.**
- **관련:** 이 문제로 web-v2 저장/큐/재로그인 등 모든 인증 기능이 프로덕션에서 실패. OAuth가 프론트의 유일 로그인 경로라 폴백 없음(비밀번호 로그인은 UI 미노출).

## 증상

네이버 OAuth 로그인 후 진단 저장 `POST /api/v1/diagnosis` 401. 확대 조사 결과 **모든 인증 필수 엔드포인트가 401**(`/api/v1/learning-queues/me`, `/api/v1/diagnosis`).

## 격리 결과 (확정된 사실)

프론트·OAuth·이 브랜치를 전부 걷어내고 **비밀번호 로그인으로 백엔드 JWT 왕복만** 프로덕션에서 직접 테스트 (curl):

1. `POST /api/v1/auth/signup` (신규 유저) → 200
2. `POST /api/v1/auth/authentication` → 200, **access 토큰 발급**(Authorization 헤더) + refresh 쿠키 세팅
3. **그 fresh 토큰으로 `GET /api/v1/learning-queues/me` → 401**, `POST /api/v1/diagnosis` → 401 (400 아님 = 인증 거부)
4. 대조군(무토큰) → 401 (정상)

→ **백엔드가 방금 자기가 발급한 fresh 토큰조차 거부.** 생판 새 유저(reissue 한 번도 안 부른 토큰)로도 재현 → 토큰 히스토리·OAuth·프론트 전부 무관, **시스템적**.

### 배제된 원인 (진단 근거)

- **시크릿 분리 아님:** 호스트 백엔드 컨테이너 **1개**(blue만), 컨테이너 `JWT_SECRET` sha256 지문 = envfile 지문 **동일**(`1e2c4de11946`). nginx upstream 단일 슬롯.
- **서명/키 정상:** `POST /api/v1/auth/reissue` (refresh 쿠키 검증) → **200** + 새 access 반환. refresh 토큰은 같은 키로 서명검증 통과. 위변조 토큰은 401(기준선 정상).
- **프론트 캡처 정상:** web-v2 `/login?token=` 캡처·`Authorization: Bearer` 주입 모두 배포 번들에서 확인됨.

### 국소화된 결함 지점

`reissue`(=refresh 검증)는 통과하는데 access 토큰 `validateToken`만 실패 → 결함은 **`TokenProvider.validateToken(access)` 경로**로 좁혀짐. 두 경로 차이는 (a) 블랙리스트 조회 `hasKeyBlackList`, (b) 토큰 클레임 구조뿐:

- access 토큰 payload = `{"sub","auth","exp"}` — **`jti`·`iat` 없음**(무작위성 없음 → 같은 유저·같은 초면 토큰 문자열 동일).
- `RedisConfig`에 `RedisTemplate<String,Object>` 빈이 **하나뿐**인데 `RedisUtil`은 `redisTemplate`·`redisBlackListTemplate` **둘**을 주입받음 → 후보 단일이라 **둘 다 같은 빈**(블랙리스트가 캐시·refresh와 DB0 공유). *(단, reissue의 슬롯 조회가 정상 동작하므로 메인 템플릿 자체는 정상 — 블랙리스트 오탐이 왜 나는지는 미확정.)*

> ⚠️ **코드 정독상 fresh 토큰은 통과해야 정상**(서명 OK·미블랙리스트). 그런데 프로덕션에서 401 → **프로덕션 런타임/상태 조건**일 가능성(예: Redis 블랙리스트 상태, 프로파일/설정)이 있음. 코드 단독 결함인지 환경 조건인지는 **로컬 재현으로 갈림**.

## 🔴🔴 진짜 근본원인 (2026-07-28 06:22Z 프로덕션 로그) — **M7 테이블이 프로덕션에 없다**

`diagnose-backend-jwt-2.sh` [A] 로그. 인증과 **전혀 무관**했다:

```
ERROR o.h.engine.jdbc.spi.SqlExceptionHelper : Table 'mmt.self_report_answers' doesn't exist
ERROR o.h.engine.jdbc.spi.SqlExceptionHelper : Table 'mmt.learning_queues' doesn't exist
ERROR o.a.c.c.C.[dispatcherServlet] : ... InvalidDataAccessResourceUsageException
      [insert into self_report_answers (concept_id,created_at,known,user_test_id) values (?,?,?,?)]
      [select l1_0.queue_id,... from learning_queues l1_0 where l1_0.user_id=? ...]
```

**인과 사슬 (2단):**

1. **결함 ①(런치 블로커):** M7 spec-01 의 additive DDL — `self_report_answers`, `learning_queues`,
   `learning_queue_items` + `probabilities.user_test_id` 컬럼 — 이 **프로덕션 RDS 에 미적용**이다.
   DDL 정본은 `api/sql/create.sql:161-206` 에 있고 **로컬에는 적용돼 있다**(로컬 재현이 green 이었던 이유).
   `spring.jpa.hibernate.ddl-auto: none` 이라 Hibernate 가 만들지 않는다.
   그 상태에서 `MMT_DIAGNOSIS_ENABLED=true` 로 신규 경로만 켜졌다 → 진단 저장·큐 조회가 **전부 500**.
2. **결함 ②(증폭기):** 그 500 이 `/error` 마스킹으로 **401 로 위장** → 앞 세션이 "인증 불능"으로 오진,
   프론트(`client.ts`)는 401 을 "토큰 만료"로 해석해 reissue 시도 후 **토큰 삭제 + 강제 재로그인**.
   백로그 원문의 "저장·큐·재로그인 전부 불능"은 이 두 결함의 합작이다.

**따라서 수정은 2건이고 둘 다 필요하다** — ①만 고치면 다음 오류도 401 로 위장되고, ②만 고치면 500 이
정직하게 보이지만 기능은 여전히 죽어 있다.

### 부수 확인 — signup 401 은 진단 스크립트 버그였다 (프로덕션 결함 아님)

06:03:55Z 의 signup 401 은 `verify-prod-jwt-isolation.sh` 1차 버전의 인용 버그였다:
`$( )` 안에서 `-d "{\"k\":\"v\"}"` 중첩 인용이 브레이스 확장으로 쪼개져 **요청 3개**로 갈라졌고,
각각 바디가 JSON 객체가 아닌 문자열(`"userEmail"` 등)이었다:

```
WARN DefaultHandlerExceptionResolver : Resolved [HttpMessageNotReadableException:
     JSON parse error: Cannot construct instance of UserDTO ... from String value ('userEmail')]
```

Spring 은 이를 **400** 으로 resolve 했는데 클라이언트는 **401** 을 받았다 →
**마스킹의 3차 독립 확증**(400→401). 스크립트는 수정됨(바디를 변수에 먼저 담고 `--data-binary`).

### 미해결 관측 (우선순위 낮음)

- 04:43:19Z `io.jsonwebtoken.io.DeserializationException: Invalid UTF-8 start byte 0xbf` —
  깨진/절단된 JWT 파싱. 로테이션 이전 토큰 잔존 가설과 정합. 마스킹 제거 후 재평가.
- `[B]` JWT 실패 건수 · `[C]` Redis 인벤토리는 출력 truncate 로 미확보. 필요 시 재실행.

## 근본원인 확정 #1 (2026-07-28, 세션 2) — **이 앱의 401 은 "인증 거부"가 아니다**

**증상 판독 자체가 틀렸다.** 처리 핸들러가 없는 예외·404 는 Spring 의 `/error` 디스패치를 타는데:

1. `/error` 는 `SecurityConfig` 의 `permitAll` 목록에 **없다** → `anyRequest().authenticated()` 적용.
2. `JwtFilter` 는 `OncePerRequestFilter` 기본값(`shouldNotFilterErrorDispatch()=true`)이라
   **ERROR 디스패치에서 아예 실행되지 않는다** → SecurityContext 가 비어 있다.
3. Spring Boot 3 은 시큐리티 필터체인을 `REQUEST,ASYNC,ERROR` 디스패치 전부에 적용한다.

→ **원래 상태코드(404·500·모든 미처리 예외)가 전부 401 로 마스킹된다.**
원래 경로의 `permitAll` 도 무의미하다 — ERROR 디스패치의 URI 는 `/error` 이기 때문.

### 실증 (로컬·프로덕션 동일 동작)

| 요청 | 로컬 | 프로덕션 | 기대값 |
|---|---|---|---|
| `GET /api/v1/no-such-path-xyz` | **401** | **401** | 404 |
| `GET /api/v1/hello/no-such-sub` (permitAll 하위) | **401** | — | 404 |
| `GET /api/v1/chapters` (permitAll) | **401** | **401** | 200 or 404 |
| `GET /api/v1/concepts` (permitAll) | **401** | **401** | 200 or 404 |
| `GET /api/v1/health` (permitAll) | 200 | 200 | 200 |
| `GET /api/v1/tests/school-level/1` (permitAll) | 200 | 200 | 200 |
| `POST /api/v1/auth/signup` (permitAll) | 200 | **401** | 200 |

즉 **`permitAll` 인 signup 이 프로덕션에서 401** 이다 — 인증과 무관한 무언가가 실패하고 401 로 마스킹된 것.

### 이 앱은 이미 이 함정을 알고 있었다 (근본 미수정)

`GlobalExceptionHandler:38-40` 주석: *"ResponseStatusException 은 /error 디스패치를 타 익명(permitAll)
요청의 4xx 가 401 로 마스킹되므로 전용 타입을 직접 응답으로 처리"*. → M7 진단 경로만 `DiagnosisException`
전용 타입으로 **우회**했고, `/error` 자체는 여전히 permitAll 이 아니다(코드베이스 전체에 `"/error"` 매처 0건).
따라서 우회 대상이 아닌 모든 경로는 계속 마스킹된다.

### 백로그 원문에 대한 정정

- 원문 §격리결과 3번의 **"401 (400 아님 = 인증 거부)"** 추론은 **무효**다. 401 은 이 앱에서
  인증 거부와 내부 오류를 구분하지 못한다.
- 원문 §국소화 "결함은 `TokenProvider.validateToken(access)` 경로로 좁혀짐" 도 **무효**.
  `reissue`(200) vs 인증필수(401) 의 차이는 `validateToken` 이 아니라 **"예외가 났는지"** 일 수 있다.
- fresh 토큰은 로컬에서 정상 인증된다(§2차 진단 1번). 프로덕션 401 의 실제 원인은
  **응답이 아니라 백엔드 로그**에만 남아 있다 → `diagnose-backend-jwt-2.sh` [A].

## 2차 진단 (2026-07-28, 세션 2) — 전제 흔들림

### 확정된 새 사실

1. **로컬 재현 실패 = 코드 단독 결함 아님.** `securelocal` 백엔드(표준 포트)에 동일 4단계 절차
   (signup→login→fresh 토큰) 실행 결과:
   - `POST /api/v1/auth/validation` → **200** (인증 성공)
   - `GET /api/v1/learning-queues/me` → **404** (= 인증 성공 + 아직 큐 없음. 무토큰 대조군은 401)
   → `validateToken(fresh)` 는 현행 코드에서 **정상 통과**. 위 "코드 정독상 통과해야 정상" 관찰이 실측으로 확인됨.

2. **프로덕션 로그 분기 = 세 갈래 중 두 개가 동시에 관측됨** (`diagnose-backend-jwt.sh` [5], 05:30:17Z,
   66ms 간격 별개 스레드):
   - `Invalid JWT Token. 잘못된 JWT 서명입니다.` + `io.jsonwebtoken.security.SignatureException`
   - `로그아웃한 상태입니다` (= `hasKeyBlackList` true)
   → 백로그 1항의 "세 갈래로 확정"이 성립하지 않음. **두 실패 모드가 공존**하며, 어느 쪽도
   fresh-토큰 요청의 것이라고 아직 귀속되지 않았다(타임스탬프 대조 미완).

3. **토큰 전달 아티팩트는 배제 가능** — 로컬 실측: `Bearer` 중복/헤더 원문 그대로 전달(=CRLF `\r` 혼입)은
   Tomcat 이 **400** 으로 거절한다. 앞 세션이 관측한 값은 401 이므로 이 계열 아티팩트는 아님.
   단, **body(JSON) 추출 vs 헤더 추출** 대조는 아직 프로덕션에서 안 돌려봤다.

4. **프로덕션 배선 정상 재확인:** 백엔드 컨테이너 1개(`mmt-backend-blue`, 이미지 = 커밋 `889390a`
   = 현행 코드), `SPRING_PROFILES_ACTIVE=secure`, `MMT_DIAGNOSIS_ENABLED=true`,
   JWT_SECRET 지문 컨테이너=envfile `1e2c4de11946`, nginx upstream 단일 슬롯.

### 이 사실들이 시사하는 것

- **서명 불일치 분기**는 단일 JVM·단일 키에서 자기 발급 토큰에는 원리상 발생할 수 없다.
  → 그 로그의 토큰은 **다른 키로 서명된 토큰** = 2026-07 유출 대응 **JWT_SECRET 로테이션 이전에 발급된
  토큰**(브라우저 localStorage 잔존)일 가능성이 높다. 이는 백엔드 결함이 아니라 **stale 클라이언트 상태**이며,
  재로그인으로 해소된다. 프론트가 서명 실패 401 에서 토큰을 비우지 않고 재시도 루프를 돌면 증상이 지속된다.
- **블랙리스트 분기**는 아래 §"코드 정독으로 확정된 잠재 결함" 1번 메커니즘으로 자가 유발이 가능하다.
- 따라서 **"백엔드가 자기 발급 fresh 토큰을 거부한다"는 헤드라인 전제 자체가 재검증 대상**이다.
  기각되면 본 항목은 런치 블로커가 아니라 "stale 토큰 처리 + 토큰 유일성" 하드닝으로 강등된다.

### 코드 정독으로 확정된 잠재 결함 (401 근본원인 여부와 무관하게 실재)

1. **access 토큰에 `jti`·`iat` 없음** (`TokenProvider.generateToken`) → 토큰 문자열이
   (subject, authorities, exp-초)의 **결정적 함수**다. 같은 유저가 같은 초에 두 번 발급받으면 **바이트 동일**.
   `AuthService.reissue` 는 블랙리스트 키로 **access 토큰 문자열 자체**를 쓰므로, reissue 가 자기가
   방금 발급한 토큰과 같은 초에 한 번 더 돌면 **방금 내준 토큰을 블랙리스트에 올린다** →
   그 유저는 해당 토큰이 만료될 때까지 `로그아웃한 상태입니다` 401. 프론트 `client.ts` 의
   401→reissue 자동 재시도가 동시 요청과 겹치면 이 조건이 실제로 만들어진다.
   *(refresh 토큰은 `jti`(UUID)가 있어 이 문제가 없다 — access 만의 결함.)*
2. **`redisBlackListTemplate` 이 사실상 `redisTemplate` 과 같은 빈** — `RedisConfig` 에
   `RedisTemplate<String,Object>` 빈이 하나뿐이라 `RedisUtil` 의 두 필드가 동일 빈으로 주입된다.
   "블랙리스트 전용 템플릿"이라는 의도가 조용히 미충족(캐시·refresh 슬롯과 DB0 공유).

## 수정 착수 순서 (다음 세션)

> ⚠️ 아래 1~2 는 2차 진단으로 **부분 대체**됐다. 갱신된 순서는 §"갱신된 착수 순서" 참조.

1. **결정적 로그 1줄 확보:** 프로덕션 백엔드에서 fresh-토큰 401 시각의 `TokenProvider` INFO 로그 확인 (`diagnose-backend-jwt.sh` [5] 섹션). 세 갈래로 확정:
   - `로그아웃한 상태입니다` → `hasKeyBlackList`가 fresh 토큰에 true = 블랙리스트 오탐 (Redis/조회 로직)
   - `잘못된 JWT 서명` → 서명 검증 실패 (reissue 성공과 모순 → 재조사)
   - `유효한 JWT 토큰이 없습니다` → 토큰 미도달 (전달/프록시)
2. **로컬 재현:** `securelocal` 백엔드에 동일 curl 절차(signup→login→/me). 로컬 401이면 코드 결함(그린 테스트 가능), 로컬 200이면 프로덕션-환경 특정 → 프로덕션 Redis 블랙리스트 상태 점검.
3. **Analyze-Before-Change**(`/analyze-before-change`) 후 국소 수정. 후보:
   - `RedisConfig`에 `redisBlackListTemplate` 빈 명시적 분리(현재 암묵 동일 빈) 또는 블랙리스트 조회 로직 점검.
   - `hasKeyBlackList` 오탐이면 원인 제거 + 회귀 테스트(`validateToken(fresh)=true`).
4. **관련 하드닝(부차, 별도 판단):** 프론트 `client.ts`의 401→reissue 자동 재시도 + jti 없는 access 토큰 조합이 블랙리스트 캐스케이드를 유발할 수 있음 — 근본 수정 후 재평가.

## 갱신된 착수 순서 (2026-07-28 세션 2 기준)

0. **[완료 · 세션 2]** 진단 종결. 로컬 재현 green → 마스킹 메커니즘 실증 → 프로덕션 로그에서
   `Table 'mmt.self_report_answers'/'mmt.learning_queues' doesn't exist` 확보.
   (에이전트는 SSM·프로덕션 HTTPS 접촉이 auto mode 분류기에 차단돼 사람이 맥에서 실행)

1. **🟡 결함 ① — M7 additive DDL 적용 → 재런치로 연기 (2026-07-31 결정)**
   - **연기 사유:** 티어다운 시점에 M7 테이블은 **비어 있었다**(기능이 500 이라 데이터 0건).
     스냅샷 전에 만들 이득이 없어 **스냅샷은 DDL 이전 상태**로 떴고, 멱등 스크립트가 리포에
     커밋돼 있어 재런치 때 적용한다. *(구 서술의 "티어다운 Phase 1 에서 적용"은 폐기 — 그 Phase 는 건너뜀)*
   - 적용 스크립트: **`api/sql/m7-apply-diagnosis-ddl-prod.sql`** (커밋 `687c29d`).
     `create.sql:161-206` 정본을 옮기되 **재실행 안전** — 테이블은 `IF NOT EXISTS`,
     ALTER 3건은 `information_schema` 가드. PREFLIGHT/POSTFLIGHT 로 적용 전후 상태 확인.
   - RDS 는 `publicly_accessible=false` → 맥에서 직접 못 붙는다. **EC2 호스트(app SG)에서** 적용.
     실행 절차 정본 = [`🤖-M7-티어다운-실행시퀀스.md`](../handoff/🤖-M7-티어다운-실행시퀀스.md)
     **§재런치 런북 3** (명령 원문은 같은 문서 Phase 1 블록에 보존됨).
   - 전부 additive 이고 구 경로 미참조(ADR-0010)라 **구 기능 무영향**. 롤백 = `MMT_DIAGNOSIS_ENABLED=false`
     (테이블은 방치 가능, 필요 시 DROP).

2. **✅ 결함 ② — 마스킹 제거 (완료)**
   - 구현: 커밋 **`d0f4c41`** — `SecurityConfig` 에 `.requestMatchers("/error").permitAll()`.
     `/analyze-before-change` 완료 후 승인된 (A)안. 트레이드오프·기각 대안은 §부록 및 **ADR-0014**.
   - 회귀 테스트: 커밋 **`087fef2`** — `ErrorDispatchMaskingTest`
     (permitAll 경로 핸들러 부재 → **404** / 무토큰 인증필수 → **401 유지** /
     오류 바디에 예외 메시지·스택 미노출). `/error` permitAll 을 되돌리면
     `expected 404 but was 401` 로 실패함을 확인 — **회귀를 실제로 잡는다**. 전체 스위트 통과.
   - 의사결정 기록: **ADR-0014** (커밋 `1a28723`) — residual ④ 종결 명시.
   - ⚠️ 배포 미반영 — 프로덕션 이미지는 `889390a`(수정 미포함). 재빌드·배포 필요.

3. **검증 (재런치 후)** — ⚠️ 스크립트는 mothball 때 삭제됐다. **수동 curl** 로 아래를 확인
   (절차 = §격리 결과 4단계. signup→login→토큰으로 호출. 토큰은 응답 **body** 에서 뽑을 것 —
   헤더 추출은 CRLF 혼입으로 400 이 난다):
   기대: login 200 → `POST /auth/validation` 200 → `GET /learning-queues/me` **404 또는 200**
   (401 = 마스킹 미배포 / 500 = DDL 미적용). 무토큰 대조군은 401 유지.
   `GET /api/v1/hello/no-such-sub` 가 **404** 로 나오는지도 확인(마스킹 제거 확증 —
   임의의 없는 경로는 익명이면 정상적으로 401 이므로 판정에 쓰면 안 된다).

4. **하드닝**
   - ✅ **완료** — access 토큰에 `jti`(UUID)·`iat` 부여 (커밋 **`1c7c29e`**). 발급마다 유일해져
     §잠재 결함 1의 자기-블랙리스트 경로가 원천 소멸. **배포 오버랩 안전**(추가 클레임은 구 인스턴스
     검증에 무영향). 회귀 테스트 `TokenProviderJtiUniquenessTest` (커밋 `087fef2`) —
     연속 발급 토큰 상이·jti 유일·기존 `sub`/`auth`/검증 경로 무영향.
   - ⬜ **미완** — `RedisConfig` 에 `redisBlackListTemplate` 빈 명시 분리(또는 `RedisUtil` 을 단일
     템플릿 + 키 프리픽스 `blacklist:` 로 정리 — 후자가 의도를 더 정확히 표현). 현재는 후보 빈이
     하나뿐이라 두 필드가 **같은 빈**으로 주입돼 블랙리스트가 캐시·refresh 와 DB0 을 공유한다.
     jti 도입으로 자기-무효화 경로는 사라졌으므로 **긴급도는 낮음**.
5. **프론트(별도 판단):** `client.ts:80-92` 의 401→reissue 1회 재시도 → 실패 시 `clearAccessToken()`.
   ⚠️ **마스킹 때문에 프론트가 내부 오류(500)를 "토큰 만료"로 오해해 사용자 토큰을 지우고 강제
   재로그인시켰다** — 이게 백로그 원문 "재로그인 실패" 증상의 실제 발현 경로다. 결함 ② 제거로
   코드 변경 없이 해소되지만, 배포 후 실측으로 확인할 것(`mocks/handlers.ts:50` 의 "실서버 미러" 주석도 재대조).

## 부록 — 마스킹 제거 방안 트레이드오프

| | (A) `/error` permitAll | (B) `shouldNotFilterErrorDispatch()=false` |
|---|---|---|
| 변경 표면 | `SecurityConfig` 1줄 | `JwtFilter` 1 메서드 |
| 효과 | 모든 에러가 원래 상태코드로 나감 | ERROR 디스패치에서 인증 유지 → 인증 요청의 에러가 정상 처리 |
| 리스크 | `/error` 가 익명 접근 가능 (Boot 기본 응답은 status/timestamp/path 만) | 에러 경로에서 토큰 재검증 1회 추가(블랙리스트 Redis 조회 포함) |
| 못 잡는 것 | 인증 요청의 SecurityContext 는 여전히 ERROR 에서 비어 있음(핸들러가 인증정보를 쓰면 문제) | `/error` 가 여전히 authenticated → **익명 요청의 에러는 계속 401 마스킹** |

→ 익명 경로(signup·frontier·preview)가 실제 증상의 중심이므로 **(A)가 필수**, (B)는 선택.

## 재현/검증 자산

- 격리 테스트 절차 = 위 "격리 결과" curl 4단계. 프로덕션 진단 계정 `zdbg142854`·`zdbg2143235`(ROLE_USER) 는 정리 예정 — **수정 검증에 재사용하려면 하나 남겨둘 것**.
- 진단 스크립트 — ⚠️ **2026-07-31 프로덕션 mothball 로 삭제됨**(EC2 종료로 실행 불가 · 계정ID 포함으로 미커밋 · 로컬 전용이었음). 무엇을 어떻게 했는지는 본 문서에 전부 서술돼 재작성 가능. *(원래: 리포 루트, 맥에서 사람이 실행 — 에이전트는 분류기 차단):*
  - `diagnose-backend-jwt.sh` — 배선·시크릿 지문·최근 30분 로그 (read-only, SSM 오케스트레이터)
  - `diagnose-backend-jwt-2.sh` — 기동 이후 전체 JWT 로그 + Redis 키 인벤토리 (read-only, SSM)
  - `verify-prod-jwt-isolation.sh` — 헤드라인 전제 재검증 (프로덕션 signup 1건 발생)
- 로컬 재현 절차(그린 확인됨): `CLAUDE.local.md` 의 진단 백엔드 기동 명령 → signup→login→
  `POST /api/v1/auth/validation`(200) / `GET /api/v1/learning-queues/me`(404=인증성공·큐없음).

## 런치 영향

- 이 버그 미해결 시 **web-v2 인증 기능(저장·학습큐·재로그인) 전부 불능** → M7 런치 완료 선언 불가.
- 인프라/KST 마감(문서·PR·머지)은 진행 가능하나, **런치 그린은 본 수정에 게이트**됨.
- **2026-07-31 갱신:** 비용 절감 티어다운으로 프로덕션이 내려가 **현재 활성 장애는 아니다**.
  다만 게이트는 유효 — 재런치 시 위 §갱신된 착수 순서 1~3 을 완료해야 런치 그린을 선언할 수 있다.
  ⚠️ 코드 수정(②·하드닝)은 리포에만 있고 **구 이미지 `889390a` 에는 없다** → 재런치는 반드시 재빌드.
