# 🤖 [MMT] zdbg 정리 → M8 콘텐츠 → M8 머지·배포

> **새 세션에 이 파일을 지목하며 "이 핸드오프 시작하자" 라고 말하면 시작한다.**
> 작성: 2026-08-07 · **기준 갱신 2026-08-15** = `feat/m7-item-selection` `1c05026`(미푸시 9 — **전부 문서**) · `feat/m8-concept-links` `dcd1894`(미푸시 5, **origin 부재**) · 프로덕션 라이브
> ✅ **① CD 실증은 2026-08-15 에 완료됐다**(§2) — 이 핸드오프의 남은 본체는 **②③④⑤**.
> ⚠️ `feat/m8-concept-links` 는 origin 에 없다 — CD 는 원격 ref 를 돌리므로 ⑤ 는 push 가 전제다.

---

## 0. 한눈에 — 지금 어디에 있나

**M7 제품 라인은 끝났고 라이브다.** 이번 세션은 그 뒤의 **정리·운영**이었는데, CD 파이프라인
복구가 예상보다 깊어 시간의 대부분을 썼다. 제품 쪽 전진은 **M8 "링크만" 코드 완료**다.

| 축 | 상태 |
|---|---|
| 프로덕션 | 🟢 정상 — `mmt-front:2.0.2` · 백엔드 `mmt-backend:9ba37bf…`(CD 배포분, 활성 슬롯=green) |
| M7 제품 | ✅ 종료 — 남은 건 폴리싱 백로그뿐 |
| M8 링크 | 🚧 코드 완료·미머지 (`feat/m8-concept-links`, 5커밋) · **콘텐츠 시드가 진짜 병목** |
| CD 파이프라인 | ✅ **실증 완료 (2026-08-15)** — 배포 수단 확보 |

✅ **README 포트폴리오 재작성 커밋 완료** (2026-08-13, `99ac30a`·`741493e` — 이 브랜치). 잔여 4건은 [`readme-portfolio-followups.md`](../backlog/readme-portfolio-followups.md) 로 분리돼 있고 이 핸드오프의 차단 요소가 아니다.
⚠️ 스크립트 4종은 `docs/handoff/scripts/` 에 있고 **`feat/m7-item-selection` 브랜치에만** 있다.
`feat/m8-concept-links` 를 체크아웃하면 일부가 안 보인다 — 실행은 홈(`~/`) 사본으로 한다.

---

## 1. 착수 순서

```
■ ① CD 1회 실증                   ✅ 완료 2026-08-15 (run 31872517144) — §2
■ ② zdbg 테스트 계정 정리          ✅ 완료 2026-08-15 (사후 조회 계정 0개)
■ ③ M8 프로덕션 DDL 적용           ✅ 완료 2026-08-15 (POSTFLIGHT table·fk·idx 3/3)
□ ④ M8 콘텐츠 큐레이션 (사람 본체) ← 파일럿 10개념 × 3링크 초안 완료, URL 검수 중
                                     시드 대상은 **중·고등 필터** 상위 10 (초등 쏠림 발견)
□ ⑤ M8 머지 + 배포                ← ①로 배포 수단이 확보됐다. push 선행 필수
□ ⑥ (선택) 인프라 지뢰 2건 — 백로그 ⭐ 2개
```

---

## 2. ① CD 1회 실증 — ✅ **완료 (2026-08-15)**

**정본:** [`../backlog/ci-backend-image-missing-secure-yml.md`](../backlog/ci-backend-image-missing-secure-yml.md) 머리말(종결 처리됨)

[run 31872517144](https://github.com/data-sy/my-math-teacher/actions/runs/31872517144) — **12m02s, build→deploy 전 구간 성공.**

```
test           skipped (skip_tests=true)
build-and-push ✅ 10m23s · mmt-backend:9ba37bf (amd64+arm64)
deploy         ✅ 1m31s
   OIDC assume-role → SSM send-command(tag:Project=mmt)
   invocation 해석 1초 → i-098e63bf15a150633      ← 08-05 실패 지점
   switch-backend.sh stdout 회수 성공             ← SSM 복구의 실제 효과
      활성 blue → green · 헬스 OK(12/30) · 데이터 smoke OK(14,178 bytes)
      nginx reload → 구버전 blue graceful 드레인(30s)
```

배포 후 라이브 `health` 200(0.13s) · `concepts/nodes/7925` 200 · 프론트 200. **활성 슬롯 = green.**

**실증으로서 깨끗하다:** 직전 라이브 이미지 커밋(`ea94a1a`) → 배포된 `9ba37bf` 사이 `api/` 코드 변경이
**0건**(문서·워크플로·프론트만)이라 성패가 파이프라인으로만 귀속된다. 2026-08-07 실패는 GitHub Actions
`partial_outage` 였음이 이 성공으로 확인됐다.

> 재발 방지(AMI 필터가 minimal 을 집는 문제)는 **미착수** — [백로그 ⭐](../backlog/ami-filter-picks-minimal-no-ssm-agent.md) (§5).

---

## 3. ② zdbg 프로덕션 테스트 계정 정리

**정본:** [`../backlog/m7-diagnostic-test-accounts-cleanup.md`](../backlog/m7-diagnostic-test-accounts-cleanup.md) (FK 조사 완료분 포함)

```bash
cp ~/my-math-teacher/docs/handoff/scripts/zdbg-cleanup.sh ~/ && bash ~/zdbg-cleanup.sh
# [3] 두 값이 0이고 [1] 에 실사용자가 없으면:  bash ~/zdbg-cleanup.sh --delete
```

스키마에 **`ON DELETE CASCADE` 가 전무**하고 `users_tests.diagnosis_id` 가 **자기참조 FK** 라
자식-우선 + NULL 끊기가 필요하다. 스크립트가 그 순서를 단일 트랜잭션으로 처리한다.

---

## 4. ③④⑤ [M8] 개념 학습자료 링크

**설계 정본:** [`../specs/m7/spec-03-learning-path-links.md`](../specs/m7/spec-03-learning-path-links.md) · **브랜치:** `feat/m8-concept-links`(미푸시 5커밋)

**이번 세션에 끝낸 것(코드):** `concept_links` 테이블 · JPA 리포지토리 · 결과 카드 부착(IN 일괄 1쿼리) ·
프론트 계약 정렬 · 시드 도구. 백엔드 181 green · mock e2e 18 passed.

**범위를 "링크만" 으로 잡은 이유(사용자 결정):** `reason`/`goal` 은 `source_concept_id`·`goal_concept_id`
additive 컬럼만의 문제가 아니라 **큐 생성 알고리즘이 병합 지점을 관통해 provenance 를 운반하는 새 책임**을
지는 일이다 — spec-03 §3.2 가 스스로 "컬럼 하나 추가보다 무거운 변경"이라 경고한다.

### ③ 프로덕션 DDL (멱등)
RDS 는 `publicly_accessible=false` → **EC2 호스트에서** 실행.
```bash
# 호스트에서
curl -fsSL https://raw.githubusercontent.com/data-sy/my-math-teacher/<커밋>/api/sql/m8-apply-concept-links-ddl-prod.sql -o /tmp/m8.sql
mysql -h <RDS_HOST> -P 3306 -u <USER> -p mmt < /tmp/m8.sql   # POSTFLIGHT 3줄 전부 OK 여야 성공
```

### ④ 콘텐츠 큐레이션 — **M8 의 실제 본체**
```bash
# 대상 개념 뽑기 (병목 상위 50 — 앱의 countBlockedDescendants 와 같은 재귀)
mysql ... < shared/scripts/select-bottleneck-concepts.sql
# CSV 채운 뒤 적재 SQL 생성
bash shared/scripts/concept-links-seed-to-sql.sh > /tmp/seed.sql
mysql ... < /tmp/seed.sql
```
**시드가 0이어도 안전하다** — 링크 결측이 계약이라 API·UI 가 그대로 동작(빈 배열 → 섹션 생략).
즉 ③⑤ 를 먼저 배포하고 ④ 를 천천히 채워도 된다.

### ⑤ 머지·배포
`feat/m8-concept-links` push → PR. 백엔드 변경이 있으므로 배포 수단이 필요한데 **①이 끝나 확보됐다**(2026-08-15).
⚠️ CD 는 **원격 ref** 를 돌린다 — 미푸시 5커밋 상태로는 실증에 M8 코드가 올라가지 않는다. push 가 전제다.

---

## 5. ⑥ 인프라 지뢰 2건 (백로그 ⭐, ROADMAP `Later` 최상단)

| 항목 | 왜 위험한가 |
|---|---|
| [AMI 필터가 minimal 을 집음](../backlog/ami-filter-picks-minimal-no-ssm-agent.md) | 이번 CD 사망의 근인. **더 큰 지뢰 = `most_recent=true` 인데 `lifecycle` 없음 → 전체 `terraform apply` 가 프로덕션 EC2 를 교체하려 든다.** 결정 순서 = 안전장치(`ignore_changes`) → user_data 설치 → 필터 조이기 |
| [SSH IP 고정 → SSM Session Manager](../backlog/ssh-ingress-ip-pinning-to-session-manager.md) | 공인 IP 바뀔 때마다 SSH·CD 가 재발성으로 막힌다. 선행 = SSM 정상화(✅ 됨). 본체는 SSH 쓰는 스크립트 5개 이관 + **비상 접근 경로 설계** |
| ⏰ [certbot 갱신 타이머 미확인](../backlog/tls-cert-renewal-timer-after-relaunch.md) | **TLS 만료 `2026-11-03`.** M6 에서 실증한 타이머는 terminate 된 옛 호스트 것이고 재런치 런북에 재등록 단계가 없다. 증상 0 → 만료 당일 사이트 전체 차단. **셋 중 유일하게 기한이 있다** |

⚠️ **터미널에서 `terraform apply` 를 전체로 돌리지 말 것.** 지금은 `-target` 으로만 만졌다.

---

## 6. 이번 세션에서 배운 것 (다음 세션이 같은 함정을 밟지 않게)

- **오래된 문서가 "미착수"라고 우기면 디스크·git 을 먼저 믿어라.** ③ 의 secure.yml 배선 결함은
  이미 `f432c53`+ADR-0013 으로 해결돼 있었는데 백로그·핸드오프가 stale 이라 지뢰처럼 보였다.
- **증상으로 판정하고 희망 상태로 판정하지 마라.** `sync-my-ip.sh` 첫 판이 `terraform.tfvars` 를
  기준으로 비교해 **SSH 가 막혔는데 "동기화 불필요"** 라는 거짓 초록을 냈다. 지금은 SSH 도달 여부로 판정한다.
- **AL2023 은 IMDSv2** — 토큰 없이 메타데이터를 물으면 빈 값이 와서 "IAM 롤 없음"으로 오독한다.
- **가설은 두 번 틀렸다**(배선 소실 / 에이전트 등록 지연). 답은 `rpm -q` 한 줄이었다 —
  **추론 전에 실물을 조회**하는 순서가 빨랐다.

---

## 7. 환경 (CLAUDE.local.md 정본 — 여기 중복 최소)

```bash
docker compose up -d mmt-mysql mmt-neo4j mmt-redis mmt-ai     # 인프라 (테스트 전 필수 — 없으면 21건 실패)
cd api && ./gradlew test                                       # 181 green 기준
cd web-v2 && npx tsc --noEmit && npx playwright test           # mock e2e 18 passed 기준
```
⚠️ 외부 redis 가 6379 를 점유하면 **위장 green** — `lsof -i :6379` 확인.
⚠️ 프론트 이미지는 `docker buildx build --platform linux/amd64` 필수. 이미지 repo = `mmt2024`.
