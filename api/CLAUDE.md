# MMT API (Spring Boot)

루트 규칙은 @/CLAUDE.md 참조. 이 문서는 `api/` 워크스페이스에만 적용되는 규칙이다.

## 기술 스택

- Java 17
- Spring Boot 3.1.6 (Gradle)
- JPA + Hibernate (주 영속성)
- JdbcTemplate (레거시 — JPA로 점진 전환 중, 일부 리포지토리 병행 중)
- Spring Data Neo4j Reactive (MySQL로의 마이그레이션 예정 — Milestone 2)
- MySQL 8, Redis
- Spring Security + OAuth2 Client (Google / Naver / Kakao)
- JWT (`io.jsonwebtoken:jjwt` 0.11.5)
- Lombok

## 빌드 & 테스트

- 전체 빌드: `./gradlew build`
- 전체 테스트: `./gradlew test`
- 단일 테스트: `./gradlew test --tests "ClassName"`
- 컴파일 체크: `./gradlew compileJava`
- 로컬 실행: `./gradlew bootRun` (securelocal 프로파일 자동 활성화)

## 아키텍처

- 레이어: **Controller → Service → Repository**
- 베이스 패키지: `com.mmt.api`
- 주요 패키지:
  - `config` — Spring 설정
  - `controller` — REST 엔드포인트
  - `domain` — 엔티티/값 객체
  - `dto` — 요청/응답 DTO
  - `repository` — 도메인별 서브패키지 (예: `repository/concept`, `repository/users`)
  - `service` — 비즈니스 로직
  - `jwt`, `oauth2` — 인증/인가
  - `exception` — 전역 예외 처리
  - `util` — 공용 유틸 (Redis 등)
- 주요 도메인:
  - **user / auth** — 회원, OAuth2 로그인, 권한
  - **chapter** — 교과 단원
  - **concept** — 수학 개념 (MySQL + Neo4j 양쪽에 존재)
  - **knowledgeSpace** — 개념 간 선후 관계 (현재 Neo4j)
  - **item** — 문제
  - **test / userTest** — 진단 테스트, 응시 이력, 답안

## 주요 도메인 객체 및 유틸 (M1에서 확인됨)

- `ConceptRepository`: Neo4j Reactive (`ReactiveNeo4jRepository<Concept, Integer>`)
- `ConceptService` 생성자 주입 4종: `ConceptRepository` + `KnowledgeSpaceRepository` + `JdbcTemplateConceptRepository` + `Optional<MysqlConceptRepository>` (피처 플래그 `mmt.migration.use-mysql-cte-for-graph=true` 시에만 bean 등록되는 스텁)
- `ConceptService` 그래프 메서드: `findNodesByConceptId`, `findNodesIdByConceptIdDepth2`, `findNodesIdByConceptIdDepth3`, `findNodesIdByConceptIdDepth5`, `findToConcepts`
- `LogicUtil.bfs(int start, List<Integer> integerList)`: `Map<Integer, Integer>` 반환 (시작 노드로부터 거리 맵)
- `ProbabilityService`의 `.block()` 호출 위치: `createAndPredict` 류 메서드 (`ProbabilityService.java:66` 근방)
- Neo4j 컨테이너 이미지: `mymathteacher/mmt-neo4j:1.0.0` (커스텀 빌드, 기반 Neo4j 버전은 별도 확인 필요)

## 영속성 레이어 규칙

- **신규 리포지토리는 JPA 사용** (JdbcTemplate 금지)
- 기존 JdbcTemplate 코드 수정 시:
  - 단순 수정은 현행 유지 (불필요한 전환 금지)
  - 구조적 변경이 필요하면 JPA 전환을 함께 제안하되 반드시 ADR 작성 (`docs/adr/`)
- 현재 `repository/concept/`에는 `ConceptRepository`(Spring Data Neo4j Reactive, 그래프 탐색용)와 `JdbcTemplateConceptRepository`(MySQL `concepts`/`chapters` 조회용)가 공존 — JPA 리포지토리는 아직 이 패키지에 없으며, JPA 전환은 Epic: JdbcTemplate → JPA 전환의 대상
- 배치 삽입은 `BatchPreparedStatementSetter` 또는 JPA `batch_size` 설정 사용
- Neo4j 쿼리는 Milestone 2에서 MySQL로 단계적 이전 예정 — 신규 그래프 쿼리 추가 전 로드맵 확인

## 테스트 규칙

현재 테스트 인프라는 최소 수준(`ApiApplicationTests`, `RedisUtilTest` 2개)이다. 본격적인 테스트 규칙·Testcontainers·쿼리 카운트 검증은 **Milestone 1**에서 도입된다. 그 이전까지는 아래를 지향 규칙으로 한다.

- `@SpringBootTest` 남용 금지 — `@DataJpaTest` · `@WebMvcTest` 우선
- 통합 테스트가 필요하면 Testcontainers 기반 준비 (Milestone 1 완료 후 강제)
- N+1 쿼리 가능성이 있는 변경은 `QueryCountAssertions`로 검증
- 테스트 없이 리포지토리 로직을 변경하지 말 것 — 최소 단위 테스트 동반

## 테스트 인프라 (M1 산출물)

- 테스트 프로파일: `application-test.yml` (`@ActiveProfiles("test")` 또는 `-Dspring.profiles.active=test`로 활성화). `securelocal` 프로파일과 독립적이며 인클루드 관계 없음
- Testcontainers 설정: `src/test/java/.../config/TestcontainersConfig.java` (MySQL 8.0 + Neo4j)
- N+1 쿼리 감지: Hibernate `Statistics` API 직접 사용. 별도 유틸 없음
- 성능 기준선 보고서: `docs/benchmark/milestone-1-baseline.md`
- 그래프 결과 스냅샷: `shared/benchmark/neo4j-snapshot-*.json` (sha256 해시 포함, M2 동치성 검증용)
- 쿼리 시간 측정: `QueryTimingAspect` (Micrometer Timer 기반)
- N+1 회귀 테스트 위치: `src/test/java/.../*N1Test.java`

## 마이그레이션 규칙

- 스키마 변경·쿼리 구조 변경은 **Analyze-Before-Change 필수** (`/analyze-before-change`)
- 가능하면 피처 플래그로 구버전·신버전 병행 가능한 구조 우선
- 롤백 시나리오가 없는 마이그레이션은 금지
- 프로덕션 반영 전 `application-securelocal.yml`로 로컬 검증 — 자격증명 파일은 절대 커밋 금지

## 피처 플래그 컨벤션 (M1에서 확정)

신규 피처 플래그는 `mmt.<영역>.<설정>` 2단계 구조를 따른다:

- `mmt.migration.*` — 마이그레이션 관련 (예: `use-mysql-cte-for-graph`, `use-jpa-for-tests`)
- `mmt.observability.*` — 관측성 설정 (예: `slow-query-threshold-ms`)
- `mmt.benchmark.baseline.*` — 벤치마크 기준선 값 (실측 후 주입)

새 영역 추가 시 ADR로 기록한 뒤 본 섹션에 영역명 추가.

## ADR 참조

아키텍처·영속성·마이그레이션 관련 의사결정은 `docs/adr/`에 기록한다. 템플릿: `docs/adr/_template.md`. 새 ADR은 `/write-adr` 슬래시 커맨드로 생성.

## 보안

- `api/src/main/resources/application-securelocal.yml`은 자격증명을 포함 → `.gitignore` 대상
- JWT 시크릿·OAuth 클라이언트 시크릿을 코드·테스트·로그에 노출 금지
- 컨트롤러 계층 전수에 Spring Security 설정이 적용되는지 확인 후 신규 엔드포인트 추가
