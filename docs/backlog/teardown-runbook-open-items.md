# [Ops] 티어다운 런북 §5 열린 항목 — 휴면 중 가능한 것만 골라 닫는다

- **상태:** 🚧 진행 예정 (Now)
- **등록:** 2026-09-09
- **정본 관계:** 런북 [`🤖-M7-인프라-티어다운-재런치.md`](../handoff/🤖-M7-인프라-티어다운-재런치.md) **§5** 가 원본 목록.
  여기는 그것을 **"지금 할 수 있나"로 다시 자른 작업 목록**이다. 각 항목의 서술 정본은 링크된 파일에 있다.

## A. 휴면 중 가능 (인프라 불요 — 여기부터)

| 항목 | 무엇 | 검증 표면 |
|---|---|---|
| `RedisConfig` 빈 분리 | `RedisUtil` 이 `redisTemplate`·`redisBlackListTemplate` 둘을 주입받는데 `RedisConfig` 에는 **`redisTemplate()` 하나뿐**이다. 타입으로 같은 빈이 두 필드에 들어간다 — **버그 아닌 설계 스멜**(블랙리스트가 캐시와 같은 serializer·같은 논리 공간을 쓴다) | 로컬 테스트 + `bootRun` 실기동. ⚠️ 인증 blacklist 라 **직렬화 포맷이 바뀌면** 롤링 배포 크로스버전 read 를 따져야 한다 |
| README 포트폴리오 잔여 ② | 레포 description·Postman ([정본](readme-portfolio-followups.md)) | GitHub·문서 |
| README 잔여 ① 중 일부 | 제품 스크린샷 — **라이브 컷은 재런치 대기**지만, 실데이터가 필요 없는 화면은 `web-v2` mock 모드로 지금 캡처 가능 | `npm run dev` |

> ⚠️ 선행: 위 1번의 실기동 검증은 로컬 스택이 살아나야 가능하다 →
> [`local-dev-env-reproducibility-after-mothball.md`](local-dev-env-reproducibility-after-mothball.md) 가 먼저다.

## B. 승인 필요 (규범/데이터면 — 제안만 하고 멈춘다)

- **`docs/specs/m6/first-deploy-runbook.md` 의 `mymathteacher/…` 표기** — 아래 D 의 네이밍 정정에서 **일부러 남긴 것**이다.
  m6 런북은 규범 문서라 별도 승인이 필요하다. 구 v1 자산 `shared/docker_commands/*.sh` 도 같은 오기를 갖고 있으나
  **v1 이력 자산이라 정정 대상이 아니라고 본다**(판단 필요). 과거 실측 리포트의 표기는 당시 기록이므로 건드리지 않는다.

## C. 재런치 대기 (지금은 손댈 수 없음 — 착수 금지)

- **TLS 자동갱신 재등록** — 호스트와 함께 소멸했다. 재등록은 `setup-tls-renewal.sh`, **dry-run 의 "all simulated renewals succeeded" 까지 봐야 끝** ([정본](tls-cert-renewal-timer-after-relaunch.md))
- **SSH 인그레스 → SSM** ([정본](ssh-ingress-ip-pinning-to-session-manager.md))

## D. 이미 해소됨

- **진단 테스트 계정(`zdbg`) 정리 — ✅** 2026-08-15 삭제 실행, 2026-10-02 에 프로덕션 DB 자체를 폐기(마지막 스냅샷 삭제).
  백로그 파일과 `zdbg-cleanup.sh` 는 지웠다 — 필요하면 git 히스토리.
- **ADR-0011 네이밍 정정 — ✅ 2026-09-13 (사용자 승인)** — 실제 Docker Hub 계정은 **`mmt2024`**
  (근거 = `secrets.DOCKERHUB_USERNAME` 를 쓰는 CI + 실값이 박힌 `deploy-front.sh`·M4 리포트).
  ADR-0011 에 §정정 절을 달고(결정 내용은 불변 · 이름만), `docker-compose.yml` 의 이미지 4개도 `mmt2024/` 로 고쳤다.
  ⚠️ compose 는 **gitignored** 라 이 수정은 이 맥에만 있다 — 다른 환경에서는 같은 정정을 다시 해야 한다(추적본 없음).
- §5 "Phase 3 마감 · **남은 것 = `/pr`·main 머지**" → **완료됐다.** `#64` (`ops/mothball-2026-09`) 가 main 에 머지됨(`d99a0df`).
  → 런북 §5 의 이 줄을 ✅ 로 정정한다(handoff = 운영 문서라 `/refresh-ops-docs` 범위).
- §5 "DNS 이력" 은 이미 *조치 불요*로 닫힌 항목 — 다시 열지 않는다.
- **재현/진단 스크립트** 는 2026-07-31 mothball 로 삭제된 것이 정상 상태다(로컬 전용·EC2 종료로 실행 불가). 서술만 [`m7-prod-auth-fresh-token-401.md`](m7-prod-auth-fresh-token-401.md) 에 남는다.

## 연결

- [`mothball-residual-cleanup-cloud-and-local.md`](mothball-residual-cleanup-cloud-and-local.md) — 클라우드·로컬 잔여 정리
- [`local-dev-env-reproducibility-after-mothball.md`](local-dev-env-reproducibility-after-mothball.md) — A 그룹의 선행 조건
