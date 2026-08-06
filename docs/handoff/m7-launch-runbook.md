# M7 런치 런북 — 프론트 web-v2 스왑 + 진단 플래그 ON (사람 실행분)

> ⚠️ **SUPERSEDED (2026-07-31) — 이 런북대로 런치하지 마세요.** 이 절차로 A3(플래그 ON)까지 진행했고 A4 실검증에서 프로덕션 인증 버그(→ [`../backlog/m7-prod-auth-fresh-token-401.md`](../backlog/m7-prod-auth-fresh-token-401.md))에 막혔습니다. 이후 **프로덕션 인프라를 mothball(완전 종료, billable $0)** 했습니다 — 스냅샷 `mmt-mothball-2026-07-31` 보존. 앞으로의 정본은 **[`🤖-M7-티어다운-실행시퀀스.md`](🤖-M7-티어다운-실행시퀀스.md) §재런치 런북**(인프라 재프로비저닝 + 스냅샷 restore 포함). 아래 내용은 **런치 절차의 역사적 기록**으로만 보존.

> 이 문서 하나만 보고 따라 하면 M7 실런치를 끝낼 수 있게 쓴 절차서입니다.
> 작성 2026-07-27. 대상 = 사용자님(호스트 접근·Docker Hub 로그인이 필요한 단계).

## 0. 지금까지 된 것 (읽고 시작)

- ✅ **Phase 1 — CI 이미지 부팅 결함 수리**: 유출 대응이 지운 프로덕션 배선을 `application.yml` 안 profile-gated `secure` 문서로 복구(placeholder-only, 리터럴 시크릿 0). [ADR-0013](../adr/0013-restore-production-wiring-as-profile-gated-tracked-config.md). 커밋 `f432c53`.
- ✅ **A1 — 백엔드 다크 배포**: `feat/m7-item-selection`(KST 코어) 이미지 `mmt2024/mmt-backend:889390a` 를 **blue-green 무중단**으로 green 슬롯에 올림. 라이브 헬스 200·CTE 그래프 200 확인. **진단 플래그는 아직 OFF**(다크 — 라이브 기능 무영향).
- ✅ **A2 준비**: web-v2 프론트 이미지 `mymathteacher/mmt-front:2.0.0` 를 **현재 소스로 재빌드 + 로컬 검증 완료**(SPA 200·라우팅·프록시 라우팅 확인). 사용자님 로컬 Docker 에 이미 존재 — **push 만 남음**.

**이제 남은 것 = 아래 A3 → A2 → A4.** (아래 순서 주의: 프론트를 먼저 바꾸면 진단이 아직 404 라 깨져 보임. **플래그 먼저 켜고, 그다음 프론트 스왑**.)

---

## 준비물 (한 번만)

1. **호스트 셸** — EC2 `i-0eb170169ac70ee05`(ap-northeast-2). SSM 세션으로 접속:
   ```bash
   # 1) MFA 로그인 (터미널이 코드를 직접 물어봄)
   aws sts get-caller-identity --profile mmt-admin
   # 2) 세션 시작
   aws ssm start-session --target i-0eb170169ac70ee05 --profile mmt-admin --region ap-northeast-2
   # 3) 세션 안에서 ec2-user 로 전환 (배포 파일들이 ec2-user 홈에 있음)
   sudo -iu ec2-user
   ```
2. **Docker Hub 로그인** — 로컬(맥)에서. `mymathteacher` 네임스페이스에 push 할 수 있는 계정이어야 함:
   ```bash
   docker login
   ```
   > 만약 로그인 계정이 `mymathteacher` 소유가 아니면, 이미지를 본인 계정으로 retag 하고(아래 A2-1 참고) 호스트 compose 태그도 같은 이름으로 맞추세요.

---

## A3. 진단 플래그 ON (백엔드) — **먼저**

플래그(`MMT_DIAGNOSIS_ENABLED`)가 true 여야 `/api/v1/diagnosis/*`·`/learning-queues/*` 빈이 등록됩니다. 지금은 미설정(=false)이라 다크 상태입니다.

### A3-1. 호스트 env-file 에 플래그 추가 (사용자님)

호스트 셸(ec2-user)에서:
```bash
# 이미 있으면 true 로 교체, 없으면 추가 (멱등)
grep -q '^MMT_DIAGNOSIS_ENABLED=' ~/mmt-backend.env \
  && sed -i 's/^MMT_DIAGNOSIS_ENABLED=.*/MMT_DIAGNOSIS_ENABLED=true/' ~/mmt-backend.env \
  || echo 'MMT_DIAGNOSIS_ENABLED=true' >> ~/mmt-backend.env
# 확인 (값이 true 로 보이면 OK)
grep MMT_DIAGNOSIS_ENABLED ~/mmt-backend.env
```
> env-file 은 컨테이너가 **시작할 때** 읽습니다. 그래서 값만 바꾼다고 즉시 적용되지 않고, 백엔드를 **재기동**해야 합니다(다음 단계).

### A3-2. 백엔드 재기동 (blue-green 무중단) — **이건 제가 해드릴 수 있습니다**

A3-1 이 끝났다고 알려주시면, 제가 배포 워크플로를 다시 돌립니다:
```bash
gh workflow run api-ci-cd-with-ec2.yml --ref feat/m7-item-selection -f skip_tests=true
```
- 같은 커밋을 다시 빌드 → `switch-backend.sh` 가 **새 컨테이너를 idle 슬롯에 올리며 갱신된 env-file(플래그 true)을 읽음** → 헬스 통과 후 무중단 컷오버.
- 직접 하실 거면 위 명령을 로컬 리포에서 실행하셔도 됩니다.

### A3-3. 확인 (사용자님, 로그인 세션에서)

브라우저로 `https://www.my-math-teacher.com` 로그인 후, 개발자도구/네트워크에서 진단 API 가 **404 가 아니라 정상 응답**하는지 확인. (익명 상태에서는 401 이 정상 — 인증 후 200 이어야 함.)

---

## A2. 프론트 web-v2 스왑

### A2-1. 이미지 push (사용자님, 로컬 맥)

```bash
# (로그인 계정이 mymathteacher 소유면 그대로)
docker push mymathteacher/mmt-front:2.0.0

# (본인 계정이 다르면: 예) 계정명이 myaccount 라면
# docker tag mymathteacher/mmt-front:2.0.0 myaccount/mmt-front:2.0.0
# docker push myaccount/mmt-front:2.0.0
#   → 이 경우 아래 A2-2 의 이미지 이름도 myaccount/... 로 맞추세요.
```
> 이 이미지는 제가 **현재 소스로 재빌드 + 로컬 검증**한 것입니다(2026-07-27). 태그 `2.0.0` 은 [ADR-0011](../adr/0011-react-web-v2-and-front-image-swap.md) 규약.

### A2-2. 호스트 compose 태그 전환 (사용자님, 호스트 셸)

호스트의 compose 파일(보통 `~/docker-compose.yml`, gitignored)에서 `mmt-front` 서비스의 `image:` 를 현재값(`mmt-front:m6`)에서 `mymathteacher/mmt-front:2.0.0` 으로 바꿉니다.
```bash
# 현재 mmt-front 이미지 확인 (롤백 대비 = 이 값을 기억)
docker inspect --format '{{.Config.Image}}' mmt-front
# compose 파일에서 image 라인 편집 (에디터로)
#   image: mmt-front:m6   →   image: mymathteacher/mmt-front:2.0.0
nano ~/docker-compose.yml     # 또는 vi
# pull + 재기동 (마운트·네트워크는 compose 정의 그대로 유지됨)
docker compose pull mmt-front
docker compose up -d mmt-front
```
> ⚠️ `image:` 만 바꾸세요. `active-backend.conf`(upstream fragment)·letsencrypt 인증서 볼륨 마운트, 포트, `mmt-net` 네트워크는 그대로 둬야 합니다(web-v2 nginx 가 이걸 그대로 씁니다).

### A2-3. 확인 (사용자님)

`https://www.my-math-teacher.com` 를 **강력 새로고침**(캐시 무시). 새 web-v2 화면(모바일 퍼스트 홈)이 뜨고, 개념 그래프·진단 진입이 보이면 OK.

---

## A4. 실서비스 검증 (사용자님, 실제 계정)

[기기 체크리스트](m7-frontend-v2-device-checklist.md) 병행. 최소 수거:

- [ ] **OAuth 3사 각 1회** — 구글·네이버·카카오 로그인(소셜=가입). 실패 콜백(`/?error=`) 인라인 에러 노출 1회.
- [ ] **게이트 경유 진단 완주 1회** — 문답 → 결과(등급/시급도) → 학습 큐.
- [ ] **그래프 탐색** — 노드 선택/해제, 색 층위(A안: 선수=파랑 계열, 후수=보라).
- [ ] **약점 0 케이스** — "다음 단원" CTA 정상, 위 프롬프트 숨김.
- [ ] **스테일 큐** — 저장된 결과에서 큐 체크리스트 토글(첫 탭 실패 없이).

문제 발견 시 아래 롤백.

---

## 롤백 요약

| 대상 | 롤백 방법 |
|---|---|
| **백엔드(A1/A3)** | blue-green 이전 슬롯 재전환(구 이미지 `47063986…` 호스트 잔존) / 또는 env-file `MMT_DIAGNOSIS_ENABLED=false` 후 A3-2 재기동(기능만 끄기) |
| **프론트(A2)** | 호스트 compose `mmt-front` image 를 **`mmt-front:m6`** 로 원복(← `1.0.0` 아님) + `docker compose up -d mmt-front` |
| **플래그(A3)** | env-file 에서 `MMT_DIAGNOSIS_ENABLED=false` 또는 라인 삭제 + 재기동 |

---

## 다 끝나면

A3/A2/A4 각각(또는 전부) 끝났다고 알려주세요. 그러면 제가 **Phase 3 마감**을 이어서 진행합니다:
`/refresh-ops-docs`(ROADMAP·백로그 실배포 상태 최신화) → `/pr`(feat/m7-item-selection → main, KST + web-v2 3건 + ADR-0012·0013 + 배포 결과) → 머지 → 조건부 teardown.
