# [Dev] 휴면 후 로컬 개발환경이 재현되지 않는다 — 볼륨·이미지 소멸, 시드 정본 불명

- **상태:** 🚧 진행 예정 (Now)
- **등록:** 2026-09-09
- **인프라 불요** — 전부 이 맥에서 닫힌다. 휴면 중 실제로 손댈 수 있는 항목.

## 증상 한 줄

**"다음에 로컬 띄우면 되겠지" 가 지금은 성립하지 않는다.** MMT 볼륨·이미지가 전부 사라졌는데
`docs/DEVELOPMENT.md` 는 여전히 *"볼륨은 유지되므로 초기 데이터 적재를 반복할 필요가 없다"* 로 끝난다 — **stale 서술**이다.

## 실측 (2026-09-09)

| 항목 | 문서가 말하는 것 | 실제 |
|---|---|---|
| `mysql-vol` / `neo4j-vol` | 유지됨 → 시드 재적재 불요 | **없음** → 다음 기동은 `api/sql` 10개 파일 재적재부터 |
| `mmt-*` 컨테이너 | `docker compose up -d` 로 기동 | **없음** (이미지도 없음) |
| `mmt-ai` (TF Serving) | compose 가 `mmt2024/mmt-ai:serving` 참조(2026-09-13 정정 전 `mymathteacher/…`) | **로컬 이미지 없음** — 레지스트리 잔존 여부 미확인 |
| DKT 모델 아티팩트 | (문서에 없음) | `ai/savedmodel/saved_model.pb` 6.7MB — **git 추적 중**(`acc72f4` v1 시절부터) |

## DKT 모델 아티팩트 — 확인 결과 위험 아님 (2026-09-13)

`ai/Dockerfile` 은 `COPY ./savedmodel /models/my_model/1` 이라 **이미지 재빌드가 이 파일에 달려 있다.**
처음엔 git 밖 로컬 유일본으로 보였는데, **실제로는 `acc72f4`("ai 모델", v1 시절)부터 추적되고 있었다**
(`git cat-file -s HEAD:ai/savedmodel/saved_model.pb` = 7,069,575 B · 워킹트리와 동일).
→ **클론만으로 `mmt-ai` 재빌드가 닫힌다. 백업 조치 불요.**

> 오판 경위(같은 실수 방지): `git ls-files ai | head -20` 이 정확히 20줄에서 잘려 목록 끝의 `savedmodel` 이 안 보였다.
> 추적 여부는 **경로를 직접 물어서** 판정한다 — `git ls-files -- <path>` 또는 `git cat-file -e HEAD:<path>`.
> `git check-ignore` 가 비는 것은 "추적 안 됨"의 근거가 아니다(추적 중이면 당연히 ignore 에도 안 걸린다).

남은 확인은 **재빌드가 실제로 되는지**다 — 파일 존재와 이미지 기동은 다른 문제다.

- [ ] 레지스트리 사본도 살아 있나 — `docker manifest inspect mmt2024/mmt-ai:serving`
      (계정 이름은 2026-09-13 에 `mymathteacher`→`mmt2024` 로 정정 → [ADR-0011 §정정](../adr/0011-react-web-v2-and-front-image-swap.md))
- [ ] 재빌드 실증 — `cd ai && docker build --platform linux/amd64 -t mmt2024/mmt-ai:serving .` 후
      8501 `/v1/models/my_model` 이 `AVAILABLE` 인지 (⚠️ 맥 arm64 ≠ EC2 x86_64 — `--platform` 누락 금지)
      `ai/` 는 **명시 지시 없이 수정 금지**지만 빌드는 읽기만 하므로 무관하다.

## 나머지 할 일

- [ ] `docs/DEVELOPMENT.md` 종료 절 정정 — "볼륨 유지" → **"볼륨은 사라질 수 있고, 없으면 시드 재적재부터"**.
- [ ] **시드 재적재 정본 확인** — `api/sql` 에 `insert_concepts*` 변형이 6벌(`_v1`·`_opti`·`_escape`·`_latex`…) 공존한다.
      DEVELOPMENT.md 는 `insert_concepts_latex.sql` 을 지정하지만, **나머지 변형이 왜 남아 있는지**는 아무 데도 없다
      (기존 백로그 한 줄 *"로컬 DB 초기화 시드 정본 부재"* 가 이 항목이다 → 여기로 흡수).
- [ ] **한 바퀴 실증** — 문서만 고치고 끝내지 않는다. 아래 순서가 전부 통과해야 "재현된다" 로 친다.
- [ ] 포트 충돌 절차 확인 — `high-traffic-performance-tuning` 스택이 3306/6379 를 잡고 있다(`CLAUDE.local.md`).

## 완료 판정 (한 바퀴)

1. 남의 스택 비우기 → `docker compose up -d mmt-mysql mmt-redis mmt-ai` 가 뜬다 (**mmt-ai 가 관문이다**)
2. `api/sql` 시드 10개 적재 성공 (`--default-character-set=utf8mb4` 포함)
3. `MMT_DIAGNOSIS_ENABLED=true … ./gradlew bootRun` 실기동 — 단위 테스트는 DI 배선 실패를 안 잡는다
4. `cd web-v2 && VITE_ENABLE_MOCK=false VITE_API_BASE=http://localhost:8080 npm run dev` 로 자가진단 한 세션 완주
5. `cd api && ./gradlew test` — 기준선 **181/0** (⚠️ 사전 `lsof -i :6379` 로 남의 redis 에 붙는 위장 green 배제)

3~4 가 통과하기 전까지는 **"휴면 중에도 할 수 있는 일"(자바 상향 등)의 실기동 검증도 같이 막혀 있다** — 그래서 이게 먼저다.

## 연결

- 절차 정본 = [`docs/DEVELOPMENT.md`](../DEVELOPMENT.md) · 개인 환경 = `CLAUDE.local.md`
- 휴면 잔여 전반 = [`mothball-residual-cleanup-cloud-and-local.md`](mothball-residual-cleanup-cloud-and-local.md)
- 자바/Boot 상향 = [`java-17-lts-upgrade.md`](java-17-lts-upgrade.md) (§검증 3번이 이 항목에 의존)
