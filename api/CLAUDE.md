# MMT API (Spring Boot)

루트 규칙은 @/CLAUDE.md 참조. 이 문서는 `api/` 워크스페이스에만 적용되는 규칙이다.

## 기술 스택

Java 17 · Spring Boot 3.1.6 (Gradle) · JPA + Hibernate(주 영속성) · JdbcTemplate(레거시, JPA 로 점진 전환 중) ·
MySQL 8 · Redis · Spring Security + OAuth2 Client(Google/Naver/Kakao) · JWT(`jjwt` 0.11.5) · Lombok ·
Spring Data Neo4j Reactive(**그래프 탐색은 M2 에서 MySQL 재귀 CTE 로 이전 완료** — 플래그로 분기, Neo4j 실폐기는 M3)

## 빌드 & 테스트

```bash
./gradlew build                      # 전체 빌드
./gradlew cleanTest test             # 전체 테스트 ⚠️ cleanTest 없으면 UP-TO-DATE 로 조용히 스킵된다
./gradlew test --tests "ClassName"   # 단일 테스트
./gradlew compileJava                # 컴파일 체크
./gradlew bootRun                    # 로컬 실행 (프로파일 미지정 시 securelocal 활성)
```

## 아키텍처

- 레이어: **Controller → Service → Repository** · 베이스 패키지 `com.mmt.api`
- 패키지: `config` `controller` `domain` `dto` `repository`(도메인별 서브패키지) `service` `jwt` `oauth2` `exception` `util`
- 주요 도메인:
  - **user / auth** — 회원, OAuth2 로그인, 권한
  - **chapter** — 교과 단원
  - **concept** — 수학 개념 (정본 = MySQL `concepts`. Neo4j 사본은 M2 이후 미사용)
  - **knowledgeSpace** — 개념 간 선후 관계 (MySQL `knowledge_space` + 재귀 CTE. 프로덕션은 CTE-only)
  - **diagnosis** — M7 자가진단(self-report OX) 순회·DKT 시급도·학습 큐. 플래그 `mmt.diagnosis.enabled` 뒤
  - **item** — 문제 · **test / userTest** — 진단 테스트, 응시 이력, 답안

## 코드를 만지기 전에 알아야 할 것

- **`ConceptService` 의 그래프 분기** — 생성자 주입 4종(`ConceptRepository`(Neo4j Reactive) ·
  `KnowledgeSpaceRepository` · `JdbcTemplateConceptRepository`(CTE) · `RedisUtil`). CTE 전환은 빈 등록이 아니라
  `@Value` 불리언 `useMysqlCte`(`mmt.migration.use-mysql-cte-for-graph`, 기본 false)로 **각 그래프 메서드 안에서 분기**한다.
  true = `findPrerequisitesWithDepth`/`findPrerequisiteConcepts`(WITH RECURSIVE), false = Neo4j 경로
- 그래프 메서드: `findNodesByConceptId` · `findNodesIdByConceptIdDepth2/3/5` · `findToConcepts`
- 그래프 캐시 키 네임스페이스 = `graph:v2:` (크로스인스턴스 직렬화 안전)
- `LogicUtil.bfs(int start, List<Integer>)` → `Map<Integer,Integer>` (시작 노드로부터의 거리 맵)
- `ProbabilityService` 는 **동기** `RestTemplate` 로 TF Serving 호출(`getPrediction`, URL 하드코딩
  `http://mmt-ai:8501/...`). 리액티브 `.block()` 은 `ConceptService`·`KnowledgeSpaceService` 두 곳에만 있다
- Neo4j 컨테이너 이미지: `mymathteacher/mmt-neo4j:1.0.0` (커스텀 빌드)

## 영속성 레이어 규칙

- **신규 리포지토리는 JPA 사용** (JdbcTemplate 금지)
- 기존 JdbcTemplate 코드 수정 시: 단순 수정은 현행 유지(불필요한 전환 금지) / 구조적 변경이 필요하면
  JPA 전환을 함께 제안하되 **반드시 ADR 작성**
- `repository/concept/` 에는 `ConceptRepository`(Neo4j Reactive)와 `JdbcTemplateConceptRepository`(MySQL 조회)가 공존.
  JPA 리포지토리는 아직 없고, 전환은 Epic: JdbcTemplate → JPA 의 대상
- 배치 삽입은 `BatchPreparedStatementSetter` 또는 JPA `batch_size`
- 신규 그래프 쿼리 추가 전 로드맵 확인 (CTE 경로가 정본)

## 테스트 규칙

현재 테스트 클래스 42개 · 181 테스트 — 통합(Testcontainers)·N+1 회귀·성능 회귀·결정론 단위가 모두 있다.

- `@SpringBootTest` 남용 금지 — `@DataJpaTest` · `@WebMvcTest` 우선
- 통합 테스트는 Testcontainers 기반 (`TestcontainersConfig` 재사용)
- N+1 가능성이 있는 변경은 Hibernate `Statistics` 로 쿼리 수 검증 (`*N1Test.java` 패턴)
- 테스트 없이 리포지토리 로직을 변경하지 말 것 — 최소 단위 테스트 동반
- **결정론이 계약인 로직**(문항 선택 등)은 "같은 입력 → 같은 출력"을 테스트로 고정할 것
- ⚠️ 단위 테스트는 생성자를 직접 호출하므로 **Spring DI 배선 실패를 못 잡는다** — 빈 생성자·설정 변경 시
  `bootRun` 으로 실기동 확인 (M7 에서 실제로 다중 생성자 부팅 실패를 라이브가 먼저 잡았다)
- ⚠️ **로컬 초록을 CI 초록으로 읽지 말 것.** 전 스위트는 CI 에서 아직 통과한 적이 없다 —
  [`test-suite-not-portable-to-ci.md`](../docs/backlog/test-suite-not-portable-to-ci.md)

## 테스트 인프라

- 테스트 프로파일: `application-test.yml` (`@ActiveProfiles("test")` 로 활성화).
  **`securelocal` 과 독립적이다** — base 는 `spring.profiles.default: securelocal` 이라 활성 프로파일이 있으면 끌려오지 않는다.
  따라서 test 프로파일은 redis·oauth2·jwt·CORS 를 스스로 갖는다(전부 더미).
  로컬 redis 에 비밀번호가 걸려 있으면 `TEST_REDIS_PASSWORD` 로 넘긴다
- Testcontainers: `src/test/java/.../config/TestcontainersConfig.java` (MySQL 8.0 + Neo4j, `withReuse`)
- N+1 감지: Hibernate `Statistics` API 직접 사용 (별도 유틸 없음) · 회귀 테스트 = `*N1Test.java`
- 쿼리 시간 측정: `QueryTimingAspect` (Micrometer Timer)
- 성능 기준선: `docs/benchmark/milestone-1-baseline.md` · 그래프 스냅샷: `shared/benchmark/neo4j-snapshot-*.json`

## 마이그레이션 규칙

- 스키마·쿼리 구조 변경은 **Analyze-Before-Change 필수** (`/analyze-before-change`)
- 가능하면 피처 플래그로 구·신 병행 구조 우선
- **롤백 시나리오가 없는 마이그레이션은 금지**
- 프로덕션 반영 전 `application-securelocal.yml` 로 로컬 검증 — 자격증명 파일은 절대 커밋 금지

## 피처 플래그 컨벤션

신규 플래그는 `mmt.<영역>.<설정>` 2단계 구조를 따른다:

| 영역 | 용도 | 예 |
|---|---|---|
| `mmt.migration.*` | 마이그레이션 | `use-mysql-cte-for-graph` |
| `mmt.observability.*` | 관측성 | `slow-query-threshold-ms` |
| `mmt.benchmark.baseline.*` | 벤치마크 기준선 | 실측 후 주입 |
| `mmt.diagnosis.*` | M7 자가진단 경로 | `enabled` (ADR-0010) |

새 영역 추가 시 ADR 로 기록한 뒤 본 표에 추가.

## 보안

- `application-securelocal.yml`(로컬)·`application-secure.yml`(프로덕션)은 자격증명을 포함 → **둘 다** `.gitignore` 대상.
  시크릿-보유 프로파일은 **변형별 전수 확인**(2026-07 유출 원인 = `securelocal` 만 ignore 하고 `secure` 누락)
- JWT 시크릿·OAuth 클라이언트 시크릿을 코드·테스트·로그에 노출 금지
- 컨트롤러 계층 전수에 Spring Security 설정이 적용되는지 확인 후 신규 엔드포인트 추가

## ADR

아키텍처·영속성·마이그레이션 의사결정은 `docs/adr/` 에 기록한다. 템플릿 `_template.md`, 생성 `/write-adr`.
