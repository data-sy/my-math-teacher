# RDS 시드 런북 (M6 spec-01 §3-B2)

**성격:** 👤 사람 실행 런북 — 데이터면 write. 어시스턴트가 명령은 짜되 실행은 사람이 트리거.
**대상:** 신규 RDS `mmt-db`(빈 DB) → in-repo `api/sql/` 스크립트로 시드
**전제:** `terraform apply` 완료(2026-07-11), RDS `available`, EC2 `running`+SSM `Online`

---

## 결정: 시드 소스 = **(b) in-repo `api/sql/` 스크립트** (2026-07-11, 어시스턴트 권장·사용자 위임)

근거:
1. 프로덕션 `application.yml` = `ddl-auto: none` → 앱이 스키마를 안 만듦. `api/sql/create.sql`이 스키마 정본 = in-repo SQL이 *의도된* 시드 경로.
2. 데모 핵심 데이터 전부 in-repo: 스키마 + 선후관계 그래프 엣지(`insert_knowledge_space.sql`, §4 CTE 기능 실데이터) + concepts·chapters·items·diag·인덱스.
3. 개인정보 리스크 회피 — 운영 덤프(a)는 실사용자 진단이력 PII를 공개 데모 박스에 올리게 됨. 이력서 데모엔 불필요.
4. 재현 가능·버전관리. RDS not-public이라 어차피 EC2 경유 import → 정돈된 스크립트 세트가 큰 덤프보다 가벼움.

---

## 접속 경로: SSM 포트포워딩 (SSH 키·RDS 공개 불필요)

RDS는 `publicly_accessible=false`, 3306은 app SG에서만 열림. EC2를 bastion 삼아 SSM 포트포워딩으로 RDS를 로컬에 터널링한다. **SSH 키 불요, RDS 비공개 유지.**

전제 도구(로컬): `session-manager-plugin`, `mysql` 클라이언트.

```bash
# 1) SSM 포트포워딩 세션 (별도 터미널에서 유지) — 로컬 13306 → RDS 3306
aws ssm start-session \
  --region ap-northeast-2 \
  --target i-0eb170169ac70ee05 \
  --document-name AWS-StartPortForwardingSessionToRemoteHost \
  --parameters host=mmt-db.c7qu444ug8bf.ap-northeast-2.rds.amazonaws.com,portNumber=3306,localPortNumber=13306
# (assume-role 임시자격 필요: source infra/terraform/tf-assume.sh)
```

이후 로컬에서 `mysql -h 127.0.0.1 -P 13306 -u mmtadmin -p mmt < <파일>` 로 로드. 비번 = `terraform.tfvars` 의 `db_password`.

> 대안: `aws ssm start-session` 로 EC2 셸 진입 후, EC2에 sql 파일을 올려(app SG에서 RDS 직결) 로드해도 됨. 위 포트포워딩이 로컬 파일을 그대로 스트리밍할 수 있어 더 단순.

---

## FK-safe 로드 순서 (create.sql FK 의존성 기준)

| # | 파일 | 테이블 | FK 의존 |
|---|---|---|---|
| 1 | `create.sql` | 전체 스키마 + FK | — |
| 2 | `insert_chapters.sql` | chapters | — |
| 3 | **`insert_concepts_latex.sql`** | concepts | chapters |
| 4 | `insert_concepts_sections.sql` | sections, concepts_sections | concepts |
| 5 | `insert_knowledge_space.sql` | knowledge_space (선후 엣지) | concepts |
| 6 | `add_knowledge_space_indexes.sql` | (인덱스) | knowledge_space |
| 7 | `insert_diag_tests.sql` | tests | — |
| 8 | `insert_diag_items.sql` | items (진단 문항) | concepts |
| 9 | `insert_items.sql` | items (개인 문항) | concepts |
| 10 | `insert_diag_testsitems.sql` | tests_items | tests, items |
| 11 | `insert_users.sql` (선택) | users, authority, user_authority | self-contained |

**users_tests·answers·probabilities 는 시드 안 함** — 사용자가 앱을 쓰며 생성되는 런타임 데이터(= PII 없음).

---

## ⚠️ 미해결 correctness 리스크 — 로드 후 반드시 실증

`insert_diag_testsitems.sql` 은 `item_id` 를 **리터럴(1,1,1…)로 하드코딩**한다. 그런데 `items` 는 컬럼리스트 INSERT(=AUTO_INCREMENT id 부여)라, **진단 문항(step 8)과 개인 문항(step 9) 로드 순서가 곧 id 배정 순서**다. tests_items 가 참조하는 id 가 진단 문항 id 와 어긋나면 진단 플로우가 깨진다.

- 위 표는 **진단 문항(8)을 개인 문항(9)보다 먼저** 로드하도록 추론 배치(하드코딩 id 가 낮은 값부터 시작 = 진단 문항이 먼저 id 1..N 을 차지했다는 정황).
- 단 이는 **추론**이다. 원본 운영 DB 의 로드 순서 문서가 없으므로, 로드 후 **실증 필수**:
  1. 진단 테스트 API/데모에서 특정 test 의 문항들이 올바르게 매칭돼 렌더되는지 (item_id 정합)
  2. 개념 그래프 탐색(CTE)이 선후관계대로 나오는지
  3. concept 설명의 수식이 깨지지 않는지 (`_latex` 적합 확인; 깨지면 `_escape` 로 폴백)
- 어긋나면: step 8↔9 순서 조정 후 재시드(`drop.sql`→처음부터), 또는 tests_items 의 item_id 를 실제 배정 id 로 보정.

> `select.sql`·`optimization.sql` 에 원본 검증 쿼리/순서 힌트가 있을 수 있음 — 실증 시 참조.

---

## 롤백

`api/sql/drop.sql`(FK 역순 DROP) → create.sql 부터 재실행. RDS 인스턴스 자체는 유지(destroy=링크 사망).
