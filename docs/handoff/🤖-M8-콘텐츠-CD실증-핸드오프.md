# 🤖 [MMT] CD 실증 → zdbg 정리 → M8 콘텐츠 → M8 잔여 구현

> **새 세션에 이 파일을 지목하며 "이 핸드오프 시작하자" 라고 말하면 시작한다.**
> 작성: 2026-08-07 · 기준 = `feat/m7-item-selection` `6e4916f`(미푸시 1) · `feat/m8-concept-links` `dcd1894`(미푸시 5) · 프로덕션 라이브
> 선행 = [`archive/🤖-M7-정리-M8-착수-핸드오프.md`](archive/🤖-M7-정리-M8-착수-핸드오프.md) ✅ 소비 완료

---

## 0. 한눈에 — 지금 어디에 있나

**M7 제품 라인은 끝났고 라이브다.** 이번 세션은 그 뒤의 **정리·운영**이었는데, CD 파이프라인
복구가 예상보다 깊어 시간의 대부분을 썼다. 제품 쪽 전진은 **M8 "링크만" 코드 완료**다.

| 축 | 상태 |
|---|---|
| 프로덕션 | 🟢 정상 — `mmt-front:2.0.2` · 백엔드 `mmt-backend:ea94a1a…`(CI 빌드 이미지) |
| M7 제품 | ✅ 종료 — 남은 건 폴리싱 백로그뿐 |
| M8 링크 | 🚧 코드 완료·미머지 (`feat/m8-concept-links`, 5커밋) · **콘텐츠 시드가 진짜 병목** |
| CD 파이프라인 | 🟡 **두 층 복구 완료, 실증만 미완**(GitHub Actions 장애로 막힘) |

⚠️ **`README.md` 에 미커밋 변경이 있다**(포트폴리오 리라이트 진행분). **건드리지 말 것.**
⚠️ 스크립트 4종은 `docs/handoff/scripts/` 에 있고 **`feat/m7-item-selection` 브랜치에만** 있다.
`feat/m8-concept-links` 를 체크아웃하면 일부가 안 보인다 — 실행은 홈(`~/`) 사본으로 한다.

---

## 1. 착수 순서

```
□ ① CD 1회 실증 (5분 + 대기)      ← GitHub 장애 걷히면 명령 한 줄. 이번 세션의 유일한 미완 증명
□ ② zdbg 테스트 계정 정리 (10분)   ← 스크립트 준비됨, 사람이 한 줄 실행
□ ③ M8 프로덕션 DDL 적용 (10분)    ← 멱등 스크립트 준비됨
□ ④ M8 콘텐츠 큐레이션 (사람 본체) ← 30~50개념 × 링크 3개. 이게 M8 의 실제 무게
□ ⑤ M8 머지 + 배포
□ ⑥ (선택) 인프라 지뢰 2건 — 백로그 ⭐ 2개
```

---

## 2. ① CD 1회 실증 — **막힌 것은 GitHub 쪽뿐**

**정본:** [`../backlog/ci-backend-image-missing-secure-yml.md`](../backlog/ci-backend-image-missing-secure-yml.md) 머리말

두 층은 2026-08-07 에 **실제로 고쳐졌고 증거도 있다**:

| 층 | 원인 | 상태 |
|---|---|---|
| SSH 인그레스 | SG 가 `var.my_ip/32` 인데 공인 IP 가 바뀜 | ✅ 해소 |
| SSM 에이전트 | **`al2023-ami-minimal` 이라 아예 미설치** | ✅ `dnf install` → `PingStatus=Online` 확인 |

남은 건 "CD 가 끝까지 도는가" 한 번뿐이다. 2026-08-06 재실행은 **GitHub Actions 장애**
(`The job was not acquired by Runner` + dispatch `HTTP 500`, githubstatus = `partial_outage`)로
**build 단계에서 끊겨 deploy 는 시작조차 못 했다** — 우리 코드 정보는 0이다.

```bash
# githubstatus.com 에서 Actions 가 operational 인지 먼저 확인한 뒤:
gh workflow run api-ci-cd-with-ec2.yml --ref feat/m7-item-selection -f skip_tests=true
gh run watch <RUN_ID>
```

**실패하면:** 이제 SSM 이 살아 있으므로 `switch-backend.sh` 의 stdout/stderr 가 워크플로 로그로
회수된다 — 이전과 달리 실제 배포 로그를 볼 수 있다.

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
`feat/m8-concept-links` push → PR. 백엔드 변경이 있으므로 **①이 끝난 뒤**가 안전하다(배포 수단 확보).

---

## 5. ⑥ 인프라 지뢰 2건 (백로그 ⭐, ROADMAP `Later` 최상단)

| 항목 | 왜 위험한가 |
|---|---|
| [AMI 필터가 minimal 을 집음](../backlog/ami-filter-picks-minimal-no-ssm-agent.md) | 이번 CD 사망의 근인. **더 큰 지뢰 = `most_recent=true` 인데 `lifecycle` 없음 → 전체 `terraform apply` 가 프로덕션 EC2 를 교체하려 든다.** 결정 순서 = 안전장치(`ignore_changes`) → user_data 설치 → 필터 조이기 |
| [SSH IP 고정 → SSM Session Manager](../backlog/ssh-ingress-ip-pinning-to-session-manager.md) | 공인 IP 바뀔 때마다 SSH·CD 가 재발성으로 막힌다. 선행 = SSM 정상화(✅ 됨). 본체는 SSH 쓰는 스크립트 5개 이관 + **비상 접근 경로 설계** |

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
