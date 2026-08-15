# 로컬 개발 환경 셋업

MMT 저장소를 로컬에서 실행하기 위한 절차. 프로젝트 소개·기술 하이라이트는 [루트 README](../README.md) 참조.

`docker-compose`로 인프라(MySQL / Redis / TF Serving)만 컨테이너로 띄우고, 백엔드와 프론트는 호스트에서 직접 실행하는 구성이다.

## 사전 요구사항

- Docker & Docker Compose
- Java 17
- Node.js 20 이상

## 환경 구성 (최초 1회)

아래 두 파일은 자격증명을 포함해 `.gitignore`로 제외되어 있다. 별도 경로로 공유받거나 기존 환경에서 복사해 루트에 둔다.

- `docker-compose.yml` — 인프라 컨테이너 설정 (MySQL/Neo4j/Redis 비밀번호 등)
- `api/src/main/resources/application-securelocal.yml` — Spring DataSource·Redis 접속 정보

두 파일의 자격증명은 서로 일치해야 한다.

## 인프라 기동

```bash
docker compose up -d mmt-mysql mmt-redis mmt-ai
```

| 컨테이너 | 포트 | 용도 |
|---|---|---|
| `mmt-mysql` | 3306 | 개념·지식그래프·진단 데이터 (그래프 탐색 포함) |
| `mmt-redis` | 6379 | 그래프 결과 캐시, 리프레시 토큰 |
| `mmt-ai` | 8501 | TensorFlow Serving — DKT 모델 |

> **Neo4j는 기본 기동 대상이 아니다.** 그래프 탐색은 M2에서 MySQL 재귀 CTE로 이전됐고(`mmt.migration.use-mysql-cte-for-graph`), 프로덕션도 CTE-only로 운영됐다. 구 Neo4j 경로(플래그 `false`)를 실행해 비교하려는 경우에만 `mmt-neo4j`(7474/7687)를 함께 띄운다.

## 초기 데이터 적재 (볼륨이 비어있는 최초 1회)

FK 제약 때문에 아래 순서대로 import해야 한다.

```bash
cd api/sql
for f in create.sql insert_chapters.sql insert_concepts_latex.sql \
         insert_concepts_sections.sql insert_knowledge_space.sql \
         insert_diag_tests.sql insert_diag_items.sql insert_diag_testsitems.sql \
         update_diag_answers.sql insert_users.sql; do
  docker compose exec -T -e MYSQL_PWD=<mmt2024_비번> mmt-mysql \
    mysql -u mmt2024 --default-character-set=utf8mb4 mmt < "$f"
done
```

> - `--default-character-set=utf8mb4`가 없으면 한글 컬럼에서 `Data too long` 에러가 난다.
> - 개념(concepts) 데이터는 반드시 `insert_concepts_latex.sql`을 사용한다 (작은따옴표 escape 처리된 버전).
> - `create.sql`에는 M7 자가진단 스키마(`self_report_answers`·`learning_queues`·`learning_queue_items` + `probabilities.user_test_id`)가 포함돼 있다. 기존 DB에 이것만 덧붙이려면 멱등 스크립트 `api/sql/m7-apply-diagnosis-ddl-prod.sql`을 사용한다.

<details>
<summary>구 Neo4j 경로를 함께 검증하는 경우 (선택)</summary>

```bash
docker compose exec -T mmt-neo4j \
  cypher-shell -u neo4j -p <neo4j_비번> < neo4j-deprecated/init/init.cypher
```

적재 후 MySQL `concepts` / `knowledge_space` row 수와 Neo4j 노드 / 관계 수가 일치하면 정상이다.

</details>

## 백엔드 실행

```bash
cd api && ./gradlew bootRun     # securelocal 프로파일 자동 활성화
```

자가진단(M7) 경로까지 열려면 피처 플래그를 켠 채 기동한다.

```bash
cd api && \
  MMT_DIAGNOSIS_ENABLED=true \
  MMT_MIGRATION_USE_MYSQL_CTE_FOR_GRAPH=true \
  MMT_DIAGNOSIS_SERVING_URL=http://localhost:8501/v1/models/my_model:predict \
  ./gradlew bootRun
```

> macOS에서는 컨테이너 호스트명(`mmt-ai`) 대신 `localhost:8501`을 써야 한다.

**주요 피처 플래그**

| 플래그 | 기본값 | 효과 |
|---|---|---|
| `mmt.migration.use-mysql-cte-for-graph` | `false` | `true` = 그래프 탐색을 MySQL 재귀 CTE로, `false` = 구 Neo4j 경로 |
| `mmt.diagnosis.enabled` | `false` | `true` = `/api/v1/diagnosis/*`·`/api/v1/learning-queues/*` 개방 |

## 프론트 실행

현재 프론트는 두 벌이 공존한다 — `web/`(구 Vue, 롤백·라이브 자산으로 보존) / `web-v2/`(React, M7 재작성분).

```bash
# web-v2 (React) — mock 모드
cd web-v2 && npm install && npm run dev

# web-v2 — 실서버 연동 모드
cd web-v2 && VITE_ENABLE_MOCK=false VITE_API_BASE=http://localhost:8080 npm run dev

# 실기기 확인 (같은 Wi-Fi의 휴대폰에서 http://<맥IP>:5173)
cd web-v2 && npm run dev -- --host

# web (구 Vue)
cd web && npm install && npm run dev
```

- 백엔드: http://localhost:8080
- 프론트: http://localhost:5173

## 테스트

```bash
cd api && ./gradlew test                        # 전체 (Testcontainers 사용 — Docker 필요)
cd api && ./gradlew test --tests "ClassName"    # 단일 클래스
cd web-v2 && npx playwright test                # e2e (mock 모드)
```

> 외부 Redis가 6379를 점유한 상태면 일부 통합 테스트가 그쪽에 붙어 **위장 green**이 될 수 있다. 테스트 전 `lsof -i :6379`로 어떤 Redis인지 확인할 것.

## 종료

```bash
docker compose down
```

볼륨(`mysql-vol`, `neo4j-vol`)은 유지되므로 다음 실행 시 초기 데이터 적재를 반복할 필요가 없다.
