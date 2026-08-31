# 백엔드 API 코드 리뷰 보고서
## My Math Teacher API - Java Spring Boot 애플리케이션

**리뷰어 관점**: 10년차 Java 시니어 개발자 & 기술 면접관
**총 Java 파일 수**: 112개
**총 코드 라인 수**: 약 4,373줄
**프레임워크**: Spring Boot 3.1.6, Java 17
**리뷰 일자**: 2025-12-12

---

## 📋 목차

1. [전체 아키텍처 및 설계 패턴](#1-전체-아키텍처-및-설계-패턴)
2. [코드 구조 및 조직화](#2-코드-구조-및-조직화)
3. [Spring Boot 모범 사례 준수](#3-spring-boot-모범-사례-준수)
4. [보안 구현](#4-보안-구현)
5. [데이터베이스 설계 및 JPA 사용](#5-데이터베이스-설계-및-jpa-사용)
6. [예외 처리 전략](#6-예외-처리-전략)
7. [테스트 커버리지 및 품질](#7-테스트-커버리지-및-품질)
8. [코드 품질 및 가독성](#8-코드-품질-및-가독성)
9. [성능 고려사항](#9-성능-고려사항)
10. [의존성 및 버전 관리](#10-의존성-및-버전-관리)
11. [발견된 치명적 버그](#11-발견된-치명적-버그)
12. [면접 평가 요약](#12-면접-평가-요약)

---

## 1. 전체 아키텍처 및 설계 패턴

### ✅ 강점

#### 깔끔한 계층형 아키텍처
- Controller → Service → Repository 계층이 명확히 분리됨
- 관심사의 분리(Separation of Concerns)가 잘 구현됨

#### 멀티 데이터베이스 아키텍처
정교한 다중 데이터베이스 구조를 성공적으로 구현:
- **MySQL (JPA)**: 관계형 데이터 저장
- **Neo4j**: 지식 그래프 관계 표현
- **Redis**: 토큰 관리 및 캐싱

이는 각 데이터베이스의 강점을 잘 활용한 설계입니다.

#### 보안 우선 설계
- JWT 인증 + OAuth2 소셜 로그인 통합
- Spring Security 필터 체인 제대로 구성
- 다양한 인증 제공자(구글, 네이버, 카카오) 지원

#### Repository 패턴
- JPA와 JdbcTemplate 인터페이스 추상화
- 데이터 접근 계층 분리

#### DTO 패턴
- Domain Entity와 API 계약 분리
- 도메인별로 DTO 패키지 구조화 (user/, concept/, test/ 등)

### ⚠️ 약점

#### 1. Repository 패턴 혼용 (설계 의문)
```java
// User 관련: JPA 사용
public interface UserRepository extends JpaRepository<Users, Long> { ... }

// 비즈니스 엔티티: JdbcTemplate 사용
public class JdbcTemplateTestRepository { ... }
public class JdbcTemplateItemRepository { ... }
```

**문제점**:
- JPA와 JdbcTemplate을 혼용하는 명확한 기준이 없음
- 일관성 부족으로 유지보수 복잡도 증가
- 성능상 이유라면 문서화 필요

**질문**: "왜 User는 JPA를 쓰고 Test/Item은 JdbcTemplate을 사용했나요?"

#### 2. Service 계층 결합도
```java
@Service
public class ProbabilityService {
    private final ConceptRepository conceptRepository;
    private final ProbabilityRepository probabilityRepository;
    private final AnswerService answerService;
    private final ConceptService conceptService;
    // ... 여러 Repository와 Service에 직접 의존
}
```

**문제점**:
- Service가 다른 Service의 Repository에 직접 접근
- 계층 간 책임 경계가 모호함

**권장사항**: Service는 다른 Service를 통해서만 접근

#### 3. 빈약한 도메인 모델 (Anemic Domain Model)
```java
// domain/Test.java
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Test {
    private Long testId;
    private String testName;
    // ... 단순 getter/setter만 존재, 비즈니스 로직 없음
}
```

**문제점**:
- Entity가 데이터만 담고 행위가 없음
- 모든 비즈니스 로직이 Service에 집중됨
- 객체지향 설계 원칙 위배

**개선 방향**: 도메인 주도 설계(DDD) 적용 고려

---

## 2. 코드 구조 및 조직화

### ✅ 강점

- **논리적 패키지 구조**: 기능별 명확한 분리
- **일관된 네이밍**: Controller, Service, Repository 네이밍 컨벤션 준수
- **Lombok 적절한 활용**: 보일러플레이트 코드 효과적으로 제거

### ⚠️ 약점

#### 1. 일관성 없는 Entity 설계
```java
// JPA 어노테이션 사용
@Entity
@Table(name = "users")
public class Users { ... }

// Plain POJO
public class Test { ... }
public class Item { ... }
```

**질문**: 왜 일부는 JPA Entity이고 일부는 POJO인가?

#### 2. 도메인 모델 위치 불명확
- 일부는 `/domain/`에 위치
- 일부는 `/domain/user/`에 위치
- 명확한 분류 기준 없음

#### 3. Value Object 부재
```java
// 원시 타입 집착 (Primitive Obsession)
Long userTestId  // 여기저기 반복
String userEmail // 타입 안정성 부족
```

**개선안**: 도메인 개념을 표현하는 Value Object 생성
```java
class UserTestId { private final Long value; }
class Email { private final String value; }
```

---

## 3. Spring Boot 모범 사례 준수

### ✅ 잘 구현된 사항

#### 1. 생성자 주입 (Constructor Injection)
```java
@Service
@RequiredArgsConstructor  // Lombok으로 생성자 주입
public class AuthService {
    private final TokenProvider tokenProvider;
    private final RedisUtil redisUtil;
    private final AuthenticationManagerBuilder authenticationManagerBuilder;
}
```
**평가**: 필드 주입이나 Setter 주입 대신 생성자 주입 사용 (베스트 프랙티스) ✓

#### 2. Configuration 클래스 분리
- RedisConfig.java
- SecurityConfig.java
- WebConfig.java

#### 3. @Transactional 사용
Service 계층 메서드에 적절히 적용 (6개 파일에서 사용)

#### 4. 전역 예외 처리
```java
@RestControllerAdvice
public class GlobalExceptionHandler { ... }
```

### ❌ 개선 필요 사항

#### 1. Profile 어노테이션 부재
```java
// WebConfig.java lines 20-21
.allowedOrigins(allowedOrigins1, allowedOrigins2)  // EC2용
.allowedOrigins("http://localhost:8080", "http://localhost:5173") // 로컬용
```

**문제점**: 두 설정이 동시에 활성화됨

**개선안**:
```java
@Configuration
@Profile("prod")
public class ProdWebConfig { ... }

@Configuration
@Profile("local")
public class LocalWebConfig { ... }
```

#### 2. Controller 검증 누락
```java
// ✓ 올바른 예
@PostMapping("/signup")
public ResponseEntity<UserDTO> signup(@Valid @RequestBody UserDTO userDTO)

// ✗ @Valid 누락
@PutMapping("")
public boolean updateUser(@RequestBody UserDTO userDTO)
```

**영향**: 유효하지 않은 데이터가 Service 계층까지 전달될 수 있음

#### 3. 하드코딩된 URL
```java
// OAuth2AuthenticationSuccessHandler.java line 45
String targetUrl = "https://www.my-math-teacher.com/login";
```

**개선안**: application.yml로 외부화
```yaml
app:
  oauth2:
    redirect-url: https://www.my-math-teacher.com/login
```

#### 4. API 버전 관리 전략 부재
- URL에 `/api/v1/` 사용 중
- 버전 변경 시 대응 방안 없음

---

## 4. 보안 구현

### ✅ 강점

#### 1. 포괄적인 JWT 구현
```java
// TokenProvider.java
- 적절한 Claims를 포함한 토큰 생성
- Refresh Token 메커니즘
- Redis를 이용한 토큰 블랙리스트 (로그아웃)
- 토큰 만료 검증
```

#### 2. OAuth2 멀티 프로바이더 지원
- 구글, 네이버, 카카오 통합
- Factory 패턴으로 프로바이더 추상화
- 커스텀 사용자 정보 추출

#### 3. 적절한 비밀번호 인코딩
- Spring Security의 BCrypt 사용

#### 4. Security Filter Chain
- Stateless 세션 관리로 적절히 구성

### ❌ 보안 취약점 (CRITICAL)

#### 🚨 1. URL에 JWT 토큰 노출 - **심각**

```java
// OAuth2AuthenticationSuccessHandler.java lines 50-52
return UriComponentsBuilder.fromUriString(targetUrl)
        .queryParam("token", token)  // ⚠️ JWT를 URL 쿼리 파라미터로!
        .build().toUriString();
```

**위험성**:
- 브라우저 히스토리에 토큰 기록
- 서버 로그에 토큰 노출
- Referer 헤더로 외부 사이트에 유출 가능
- **OWASP Top 10: A02:2021 – Cryptographic Failures**

**개선 방법**:
1. HttpOnly, Secure 쿠키 사용
2. POST 요청 본문에 포함
3. Authorization 헤더 사용

**면접 질문**: "JWT를 URL 쿼리 파라미터로 전달하는 것의 보안 위험을 설명해주세요."

#### 🚨 2. 보안 설정 우회 - **심각**

```java
// SecurityConfig.java lines 80-82
.requestMatchers("api/v1/auth/**", ...).permitAll()  // ⚠️ 앞에 / 누락
.requestMatchers("api/v1/items/**", "api/v1/perf-test/*").permitAll()
// 주석: "API 테스트 중이라 잠시 열어둠" ← 프로덕션 코드에 남아있음!
```

**문제점**:
1. 테스트 엔드포인트가 프로덕션에서도 열려있음
2. RequestMatcher에 `/` 누락으로 의도하지 않은 경로가 열릴 수 있음

**개선안**:
```java
@Profile("!prod")
.requestMatchers("/api/v1/perf-test/*").permitAll()
```

#### ⚠️ 3. Rate Limiting 부재

로그인/회원가입 엔드포인트에 속도 제한 없음
- Brute Force 공격에 취약
- DDoS 공격 방어 불가

**개선안**: Bucket4j, Resilience4j 도입

#### ⚠️ 4. 에러 정보 노출

```java
// TokenProvider.java lines 86-87
throw new RuntimeException("권한 정보가 없는 토큰입니다.");
```

**문제점**: 공격자에게 토큰 내부 구조 힌트 제공

**개선안**: 모호한 에러 메시지 사용
```java
throw new UnauthorizedException("Invalid credentials");
```

#### ⚠️ 5. Redis 비밀번호 평문 관리

```java
// RedisConfig.java line 27
@Value("${spring.redis.password}")
private String password;
```

**개선안**:
- AWS Secrets Manager
- Spring Cloud Config Server + encryption

#### ⚠️ 6. CSRF 보호 비활성화

```java
// SecurityConfig.java line 55
.csrf(CsrfConfigurer::disable)  // 왜 비활성화했는지 설명 없음
```

**질문**: Stateless JWT 사용이라도, 브라우저에서 쿠키 사용 시 CSRF 공격 가능. 정당한 이유가 있는가?

---

## 5. 데이터베이스 설계 및 JPA 사용

### ✅ 강점

#### 1. 적절한 엔티티 관계
```java
@ManyToOne, @OneToMany 올바르게 구성
```

#### 2. Cascade 작업
```java
@Entity
public class Users {
    @OneToMany(mappedBy = "users", cascade = CascadeType.ALL)
    private Set<UserAuthority> userAuthoritySet;
}
```

#### 3. N+1 문제 방지
```java
@EntityGraph(attributePaths = "userAuthoritySet")
Optional<Users> findOneWithAuthoritiesByUserEmail(String userEmail);
```
**평가**: Fetch Join을 통한 성능 최적화 ✓

#### 4. JPQL 최적화
필요한 필드만 조회하는 커스텀 쿼리 작성

### ⚠️ 약점

#### 1. 혼재된 ORM 접근 방식 (설계 냄새)

- **JPA**: User 관련 엔티티
- **JdbcTemplate**: 비즈니스 로직 (Test, Item, Answer)
- **Neo4j**: 그래프 데이터

**문제점**:
- 복잡도 증가
- 유지보수 어려움
- 개발자 학습 곡선 상승

**질문**: "각 기술 선택의 명확한 기준이 있나요?"

#### 2. 데이터베이스 마이그레이션 도구 부재

```yaml
# application.yml line 6
ddl-auto: none  # 수동 스키마 관리 - 위험!
```

**문제점**:
- 스키마 변경 이력 추적 불가
- 롤백 불가
- 팀 협업 시 동기화 어려움

**권장**: Flyway 또는 Liquibase 도입

#### 3. SELECT * 쿼리 사용

```java
// JdbcTemplateTestRepository.java line 25
String sql = "SELECT * FROM tests WHERE test_school_level = ?";
```

**영향**:
- 필요없는 컬럼 조회로 네트워크 대역폭 낭비
- 테이블 변경 시 예상치 못한 버그

**개선안**: 명시적 컬럼 지정

#### 4. Batch Size 설정 누락

```yaml
# 누락된 설정
spring:
  jpa:
    properties:
      hibernate:
        jdbc:
          batch_size: 50
```

**영향**: JPA로 여러 엔티티 저장 시 개별 쿼리 실행

#### 5. DB 제약조건과 검증 불일치

Entity 검증 어노테이션과 DB 제약조건 매칭 안됨

#### 6. Lazy Loading 문제

```java
// UserAuthority.java line 20
@ManyToOne(fetch = FetchType.EAGER)  // 항상 즉시 로딩 강제
```

**문제점**: 불필요한 경우에도 연관 엔티티 조회

---

## 6. 예외 처리 전략

### ✅ 강점

#### 1. 전역 예외 핸들러
```java
@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ExceptionHandler(DuplicateMemberException.class)
    // ...
}
```

#### 2. 커스텀 예외
도메인 특화 예외 (DuplicateMemberException, NotFoundMemberException 등)

#### 3. 적절한 HTTP 상태 코드
예외 타입별 적절한 상태 코드 반환

### ⚠️ 약점

#### 1. 불완전한 예외 처리

**처리 중인 예외**:
- MethodArgumentNotValidException
- DuplicateMemberException
- NotFoundMemberException
- UnauthorizedException

**누락된 예외**:
- `DataAccessException` (데이터베이스 에러)
- `HttpClientErrorException` (외부 API 호출)
- `Exception` (기본 폴백)

**개선안**:
```java
@ExceptionHandler(Exception.class)
public ResponseEntity<ErrorResponse> handleGeneralException(Exception e) {
    log.error("Unexpected error", e);
    return ResponseEntity.status(500).body(new ErrorResponse("Internal server error"));
}
```

#### 2. 예외 무시 (Exception Swallowing)

```java
// UserService.java lines 128-131
} catch (Exception e) {
    e.printStackTrace();  // ⚠️ 안티패턴!
    return false;
}
```

**문제점**:
- 에러가 조용히 무시됨
- 디버깅 어려움
- 프로덕션에서 추적 불가

**개선안**:
```java
} catch (Exception e) {
    log.error("Failed to update user", e);
    throw new UserUpdateException("User update failed", e);
}
```

#### 3. 일반적인 RuntimeException 사용

```java
// TokenProvider.java line 86
throw new RuntimeException("권한 정보가 없는 토큰입니다.");
```

**개선안**: 구체적 예외 타입 생성
```java
throw new InvalidTokenException("Missing authority information");
```

#### 4. 에러 응답 DTO 부재

일관성 없는 에러 응답 구조

**개선안**:
```java
@Getter
public class ErrorResponse {
    private final String code;
    private final String message;
    private final LocalDateTime timestamp;
}
```

---

## 7. 테스트 커버리지 및 품질

### 🚨 **치명적 약점** - 가장 큰 문제점

**테스트 커버리지**: **~1%** (112개 Java 파일 중 1-2개만 테스트)

#### 발견된 테스트 파일:

1. `/api/src/test/java/com/mmt/api/ApiApplicationTests.java`
   - 빈 컨텍스트 테스트만 존재

2. `/api/src/test/java/com/mmt/api/util/RedisUtilTest.java`
   - 잘 작성된 유닛 테스트 (좋은 예시!)
   ```java
   @Test
   @DisplayName("Redis에 저장된 데이터는 만료시간이 지나면 삭제된다.")
   void expiredTest() throws Exception {
       // 적절한 테스트 구조
   }
   ```

#### 누락된 테스트:

- ❌ Controller 테스트 (통합 테스트)
- ❌ Service 계층 테스트 (단위 테스트)
- ❌ Repository 테스트
- ❌ Security 테스트 (인증/인가)
- ❌ Validation 테스트

#### 영향:

**이는 프로덕션 코드에서 MAJOR RED FLAG입니다.**

- 치명적 버그가 탐지되지 않고 배포될 수 있음
- 리팩토링 시 회귀 버그 발생 위험
- 코드 변경에 대한 자신감 부족

#### 면접관 평가:

> "프로덕션 서비스를 운영 중인데 테스트가 거의 없다는 것은 심각한 문제입니다. 이 부분에 대한 계획이 있나요?"

**예상 질문들**:
1. "테스트를 작성하지 않은 이유는 무엇인가요?"
2. "어떤 테스트를 어떤 순서로 추가하시겠습니까?"
3. "TDD(Test-Driven Development)에 대해 어떻게 생각하시나요?"

#### 최소 권장 테스트:

```java
// 1. Controller 통합 테스트
@WebMvcTest(AuthController.class)
class AuthControllerTest {
    @Test
    void 로그인_성공() { ... }

    @Test
    void 잘못된_비밀번호로_로그인_실패() { ... }
}

// 2. Service 단위 테스트
@ExtendWith(MockitoExtension.class)
class UserServiceTest {
    @Mock
    private UserRepository userRepository;

    @Test
    void 사용자_생성_성공() { ... }
}

// 3. Repository 테스트
@DataJpaTest
class UserRepositoryTest {
    @Test
    void 이메일로_사용자_조회() { ... }
}
```

---

## 8. 코드 품질 및 가독성

### ✅ 강점

- **일관된 포맷팅**: Java 코딩 컨벤션 준수
- **의미 있는 변수명**: 대부분의 경우 의도가 명확함
- **Lombok 활용**: 보일러플레이트 효과적으로 제거
- **JavaDoc 주석**: Service/Controller 메서드에 존재

### ⚠️ 약점

#### 1. 프로덕션 코드에 남아있는 디버그 코드

```java
// AuthController.java line 97
System.out.println(isValid);

// ProbabilityService.java line 116
System.out.println("예측 요청 실패. 클라이언트 에러: " + e.getRawStatusCode() + ...);
```

**통계**:
- System.out.println 10개 발견 (주석 처리 7개, 활성 3개)

**문제점**:
- 프로덕션 로그에 불필요한 출력
- 성능 영향
- 전문성 결여

**개선안**:
```java
log.debug("Validation result: {}", isValid);
log.error("Prediction request failed. Status: {}", e.getRawStatusCode());
```

#### 2. Controller에 TODO 주석

```java
// AuthController.java lines 48-50
/**
 * 수정할 것 : 로그인DTO를 loginRequest로 바꾸고 변수명도 request로 바꾸기
 * 로그인 한 회원의 id도 꺼내서 토큰과 같이 담아서 loginResponse 만들기
 */
```

**의미**:
- 기술 부채를 인지하고 있으나 해결하지 않음
- 포트폴리오로 제출하기 전에 해결했어야 함

#### 3. 일관성 없는 반환 타입

```java
// UserController.java
public boolean updateUser(UserDTO userDTO)  // boolean 반환

// AuthController.java
public ResponseEntity<UserDTO> signup(@Valid @RequestBody UserDTO userDTO)  // ResponseEntity 반환
```

**문제점**: API 설계 일관성 부족

**개선안**: 모든 Controller가 ResponseEntity 반환

#### 4. Service에 복잡한 검증 로직 + 버그

```java
// UserService.java lines 119-123
if (!userDTO.getUserPassword().isEmpty() ||     // ⚠️ NPE 위험!
    !userDTO.getUserName().isEmpty() ||
    userDTO.getUserPhone() != null ||
    userDTO.getUserBirthdate() != null ||
    !userDTO.getUserComments().isEmpty()) {
```

**버그**: `getUserPassword()`가 null이면 NPE 발생

**개선안**:
```java
if (StringUtils.hasText(userDTO.getUserPassword()) ||
    StringUtils.hasText(userDTO.getUserName()) ||
    // ...
```

#### 5. 매직 넘버 (Magic Numbers)

```java
// AnswerService.java line 39
IntStream.range(0, 10)  // 데이터 증폭 계수가 하드코딩됨
```

**개선안**:
```java
private static final int DATA_AMPLIFICATION_FACTOR = 10;
IntStream.range(0, DATA_AMPLIFICATION_FACTOR)
```

#### 6. 변수명 오타

```java
// UserAuthority.java line 18
private Long userAuthoId;  // "authorityId"가 정확한 이름
```

---

## 9. 성능 고려사항

### ✅ 강점

- **Redis 캐싱**: 메모리 내 토큰 관리
- **Connection Pooling**: Redis용 Lettuce (좋은 선택)
- **Batch 작업**: `BatchPreparedStatementSetter` 사용

### ❌ 약점

#### 🚨 1. Reactive Stream에서 Blocking 호출 - **심각**

```java
// ProbabilityService.java line 66
List<Integer> conceptIdList = conceptIdFlux.collectList().block();
```

**문제점**:
- 이벤트 루프를 차단
- Reactive Programming의 장점 완전 상실
- 동시 요청 처리 능력 저하

**영향**:
- 부하 상황에서 스레드 고갈
- 응답 시간 증가

**개선안**:
```java
// 완전한 reactive chain으로 변경
return conceptIdFlux.collectList()
    .flatMapMany(conceptIdList -> processConceptList(conceptIdList))
    .collectList();
```

**면접 질문**:
> "Reactive Stream에서 `.block()`을 호출하면 어떤 문제가 발생하나요? 이 코드를 어떻게 개선하시겠습니까?"

#### ⚠️ 2. N+1 쿼리 문제

```java
// ProbabilityService.java lines 61-75
for (Probability probability : depth0){
    Long conceptId = probability.getConceptId();
    // ⚠️ 루프 안에서 Neo4j 쿼리!
    Flux<Integer> conceptIdFlux =
        conceptService.findNodesIdByConceptIdDepth3(conceptId);
}
```

**문제점**:
- depth0에 N개 항목이 있으면 N번 쿼리 실행
- 데이터베이스 왕복 시간(RTT) * N

**개선안**: Batch 쿼리로 한 번에 조회

#### ⚠️ 3. 비효율적인 데이터 증폭

```java
// AnswerService.java lines 36-42
answerCodeList.forEach(answerCode -> {
    IntStream.range(0, 10).mapToObj(i ->
        AnswerConverter.convertToIntArray(answerCode))
    // 메모리에 10배 중복 데이터 생성
```

**질문**: "왜 10배로 데이터를 증폭시키나요? AI 학습용인가요?"

**우려**: 메모리 사용량 급증

#### ⚠️ 4. Connection Pool 설정 부재

HikariCP 튜닝 없음
- 기본 설정 사용
- 트래픽 증가 시 connection 부족 가능

**권장 설정**:
```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      connection-timeout: 30000
```

#### ⚠️ 5. 캐싱 어노테이션 누락

자주 조회되는 데이터에 `@Cacheable` 미사용
- Concept, Chapter 등은 변경이 거의 없음
- 캐싱으로 DB 부하 감소 가능

---

## 10. 의존성 및 버전 관리

### ✅ 강점

- **최신 Spring Boot**: 3.1.6 (비교적 최신)
- **Java 17**: LTS 버전 사용
- **최신 라이브러리**:
  - JWT 0.11.5
  - Lombok 최신
  - Spring Security 3.x

### ⚠️ 약점

#### 1. Deprecated Jackson Serializer

```java
// RedisUtil.java line 18
redisTemplate.setValueSerializer(
    new Jackson2JsonRedisSerializer(o.getClass())  // ⚠️ Deprecated
);
```

**개선안**:
```java
redisTemplate.setValueSerializer(
    new GenericJackson2JsonRedisSerializer()
);
```

#### 2. 의존성 관리 부재

```gradle
// build.gradle - 하드코딩된 버전들
implementation 'io.jsonwebtoken:jjwt-api:0.11.5'
runtimeOnly 'io.jsonwebtoken:jjwt-impl:0.11.5'
```

**개선안**: 버전 변수 사용
```gradle
ext {
    jjwtVersion = '0.11.5'
}
dependencies {
    implementation "io.jsonwebtoken:jjwt-api:${jjwtVersion}"
}
```

#### 3. 보안 스캐닝 부재

- OWASP Dependency-Check 없음
- Snyk 없음
- 취약한 의존성 탐지 불가

#### 4. 관찰성(Observability) 누락

```gradle
// 누락된 의존성
- Spring Boot Actuator (헬스체크, 메트릭)
- Micrometer (메트릭 수집)
- Prometheus/Grafana 연동
```

**영향**: 프로덕션 모니터링 불가

---

## 11. 발견된 치명적 버그

### 🐛 버그 #1: UserService.updateUser()에서 NullPointerException

**위치**: `UserService.java` line 119

```java
// ⚠️ getUserPassword()가 null이면 NPE 발생
if (!userDTO.getUserPassword().isEmpty() ||
    !userDTO.getUserName().isEmpty() || ...
```

**재현 시나리오**:
```java
UserDTO dto = new UserDTO();
dto.setUserPassword(null);  // null 설정
userService.updateUser(dto);  // 💥 NullPointerException
```

**수정**:
```java
if (StringUtils.hasText(userDTO.getUserPassword()) || ...
```

---

### 🔓 버그 #2: 프로덕션에 보안 우회 코드 남음

**위치**: `SecurityConfig.java` line 82

```java
.requestMatchers("api/v1/items/**", "api/v1/perf-test/*").permitAll()
// 주석: "API 테스트 중이라 잠시 열어둠"
```

**위험성**:
- 성능 테스트 엔드포인트가 인증 없이 열려있음
- 악의적 사용자가 시스템 부하 유발 가능

**수정**:
```java
@Profile("!prod")
.requestMatchers("/api/v1/perf-test/*").permitAll()
```

---

### 🔐 버그 #3: URL에 JWT 토큰 노출

**위치**: `OAuth2AuthenticationSuccessHandler.java` lines 50-52

```java
return UriComponentsBuilder.fromUriString(targetUrl)
        .queryParam("token", token)  // 보안 취약점
        .build().toUriString();
```

**위험**: OWASP Top 10 위반

---

### ⚡ 버그 #4: Reactive Stream Blocking

**위치**: `ProbabilityService.java` line 66

```java
List<Integer> conceptIdList = conceptIdFlux.collectList().block();
```

**영향**: 비동기 설계의 의미 상실

---

## 12. 면접 평가 요약

### 💪 지원자가 잘한 점 (65/100)

| 항목 | 점수 | 평가 |
|------|------|------|
| **아키텍처 이해도** | 8/10 | 계층형 아키텍처 이해, 멀티 DB 통합 성공 |
| **보안 지식** | 7/10 | JWT + OAuth2 구현 (문제는 있지만 시도) |
| **최신 기술 스택** | 8/10 | Spring Boot 3, Java 17, Reactive 사용 |
| **클린 코드 기본** | 7/10 | 가독성 좋음, 컨벤션 준수 |
| **멀티 DB 경험** | 8/10 | MySQL + Neo4j + Redis 통합 |

**총점**: **38/50** (76%)

---

### 🚨 주요 우려사항 (면접 탈락 요인)

| 항목 | 점수 | 평가 |
|------|------|------|
| **테스트** | 1/10 | 치명적으로 부족 - 가장 큰 약점 |
| **보안 실무** | 4/10 | 토큰 노출, 테스트 코드 방치, Rate Limiting 부재 |
| **성능** | 5/10 | Blocking 호출, N+1 쿼리 |
| **에러 처리** | 5/10 | 예외 무시, 불완전한 핸들러 |
| **프로덕션 준비도** | 3/10 | 디버그 코드, TODO, 하드코딩된 URL |

**총점**: **18/50** (36%)

---

### 🎯 **최종 평가**

#### 종합 점수: **5.5/10** (중급 개발자 수준)

#### 채용 추천: **조건부** ⚠️

- ✅ **채용 가능**: 주니어~미드 레벨 포지션, 멘토링 제공 조건
- ❌ **채용 불가**: 시니어 포지션 또는 단독 프로젝트 리드

#### 평가 근거:

**긍정적 측면**:
- 탄탄한 기본기와 최신 기술 스택 이해
- 보안 구현 시도 (완벽하지는 않지만)
- 야심찬 멀티 DB 아키텍처 구현
- 코드 구조와 네이밍이 명확

**부정적 측면** (치명적):
- **테스트 부재**: 프로덕션 코드로서 용납 불가
- **보안 취약점**: 기본적인 보안 원칙 위반 (JWT in URL)
- **프로덕션 준비 부족**: 디버그 코드, 보안 우회 코드 방치
- **테스트/보안 문화 부재**: 코드 리뷰와 품질 관리 경험 부족

#### 코드 특성 분석:

> 이 코드는 **학습/포트폴리오 프로젝트** 성격이 강하며, **실제 프로덕션 경험**이 부족해 보입니다.

**증거**:
- 테스트 거의 없음
- "잠시 열어둠" 같은 임시 코드가 그대로 남음
- System.out.println 사용
- TODO 주석 방치

---

### 🎯 적합한 포지션

**추천 포지션**:
- 주니어~미드 레벨 백엔드 개발자
- 성장 가능성이 높은 팀
- 시니어 개발자의 멘토링 제공
- 코드 리뷰 문화가 확립된 조직
- 테스트/보안 교육 제공

**부적합한 포지션**:
- 시니어 개발자
- 단독 개발자
- 즉시 프로덕션 투입 필요한 역할
- 금융/의료 등 보안 중요 도메인 (교육 후 가능)

---

## 📋 개선 권장사항 (우선순위 순)

### 🔥 즉시 수정 필요 (프로덕션 배포 전)

1. **JWT URL 노출 제거**
   - HttpOnly 쿠키 사용 또는 POST body 전달

2. **보안 우회 코드 제거**
   - permitAll() 프로덕션에서 제거

3. **Rate Limiting 추가**
   - 로그인/회원가입 엔드포인트 보호

4. **NPE 버그 수정**
   - UserService.updateUser() null 체크 추가

5. **디버그 코드 제거**
   - 모든 System.out.println 삭제, 로거 사용

---

### ⚡ 높은 우선순위

6. **테스트 추가** (가장 중요!)
   - 최소 70% 커버리지 목표
   - Controller 통합 테스트
   - Service 단위 테스트
   - Security 테스트

7. **설정 외부화**
   - application.yml로 URL 이동
   - Spring Profile 사용 (local, dev, prod)

8. **Blocking 호출 수정**
   - Reactive Stream을 완전한 논블로킹으로 리팩토링
   - 또는 Reactive 의존성 제거하고 일반 Spring MVC 사용

9. **전역 예외 핸들러 완성**
   - 누락된 예외 타입 추가
   - 일관된 ErrorResponse DTO

10. **API 문서화**
    - Swagger/OpenAPI 추가

---

### 📊 중간 우선순위

11. **데이터베이스 마이그레이션**
    - Flyway 또는 Liquibase 도입

12. **쿼리 최적화**
    - N+1 문제 해결
    - SELECT * 제거
    - Batch size 설정

13. **모니터링 추가**
    - Spring Boot Actuator
    - Micrometer + Prometheus

14. **Repository 패턴 통일**
    - JPA vs JdbcTemplate 선택 기준 명확화
    - 일관된 접근 방식 적용

15. **Request/Response DTO 분리**
    - 명확한 API 계약

---

## 🎤 면접 시 예상 질문

### 기술적 질문

1. **"JPA와 JdbcTemplate을 혼용한 이유가 무엇인가요? 어떤 기준으로 선택하셨나요?"**
   - 예상 답변 분석 포인트: 성능 고려, 복잡한 쿼리 처리 등

2. **"JWT 토큰을 URL 쿼리 파라미터로 전달하는 것의 보안 문제점을 설명해주세요."**
   - 평가 포인트: 보안 지식 수준 확인

3. **"테스트 커버리지가 매우 낮은데, 그 이유와 개선 계획을 말씀해주세요."**
   - 평가 포인트: 테스트의 중요성 인식, 개선 의지

4. **"AnswerService에서 데이터를 10배 증폭시키는 이유가 무엇인가요?"**
   - 평가 포인트: 비즈니스 로직 이해도

5. **"Reactive Stream에서 `.block()`을 호출하면 어떤 문제가 발생하나요?"**
   - 평가 포인트: 비동기 프로그래밍 이해도

6. **"SecurityConfig에서 '잠시 열어둠'이라는 주석과 함께 엔드포인트를 open한 부분이 있습니다. 프로덕션 배포 시 어떻게 처리하시겠습니까?"**
   - 평가 포인트: 프로덕션 준비 인식

---

### 아키텍처 질문

7. **"Neo4j를 선택한 이유와 MySQL과의 데이터 일관성은 어떻게 관리하나요?"**

8. **"서비스 클래스가 여러 레포지토리에 직접 접근합니다. 계층 간 책임 분리를 어떻게 생각하시나요?"**

9. **"이 프로젝트를 10배 트래픽 증가에 대응하려면 어떤 부분을 개선하시겠습니까?"**

---

### 경험 질문

10. **"실제 사용자가 얼마나 되나요? 프로덕션 이슈 경험이 있나요?"**

11. **"코드 리뷰를 받아본 경험이 있나요? 어떤 피드백을 받았나요?"**

12. **"이 프로젝트에서 가장 어려웠던 기술적 도전은 무엇이었나요?"**

---

## 📊 상세 코드 메트릭

### 분석된 파일 통계

```
총 파일 수: 112개
총 코드 라인: ~4,373줄

디렉토리별 분석:
- controller/     8 files
- service/        10 files
- repository/     20+ files
- domain/         13 files
- dto/            ~30 files
- config/         4 files
- jwt/            5 files
- oauth2/         9 files
- exception/      4 files
- util/           2 files
- performanceTest/ 5 files

테스트 파일: 2개 (RedisUtilTest만 실질적)
```

---

## 🏁 결론

### 이 프로젝트의 가치

**긍정적 평가**:
- 1인 개발로 풀스택 서비스 구축 (대단한 성과)
- 최신 기술 스택 학습 의지
- 복잡한 도메인 문제 해결 시도
- 멀티 DB 통합 경험

**개선 필요**:
- 프로덕션 품질 기준 미달
- 테스트 문화 부재
- 보안 모범 사례 미준수

### 개발자로서의 잠재력

**강점**:
- 빠른 학습 능력 (Spring Boot 3, Reactive, Neo4j 등)
- 문제 해결 의지
- 포기하지 않는 끈기 (1인 개발)

**성장 방향**:
- 테스트 주도 개발(TDD) 학습
- 보안 베스트 프랙티스 교육
- 코드 리뷰 문화 경험
- 프로덕션 운영 경험

---

### 최종 조언 (지원자에게)

이 프로젝트는 **학습 과정으로서 훌륭한 성과**입니다. 하지만 **프로덕션 서비스**로 운영하기에는 다음 영역의 개선이 필수적입니다:

1. **테스트 추가** (가장 우선!)
2. **보안 취약점 수정**
3. **프로덕션 코드 정리**
4. **모니터링/로깅 체계 구축**

**다음 단계 제안**:
- 이 프로젝트를 리팩토링하여 포트폴리오 업그레이드
- 테스트 커버리지 70% 이상 달성
- 보안 취약점 모두 해결
- 프로덕션 배포 경험 문서화

**면접 대비**:
- 위의 약점들을 인정하고, 개선 계획 설명 준비
- "학습 프로젝트에서 실무 코드로 전환하는 과정을 겪고 있다"고 솔직히 말씀
- 멘토링을 받으며 성장하고 싶다는 의지 표현

---

**리뷰 완료 일시**: 2025-12-12
**리뷰어**: 10년차 Java 시니어 개발자
**분석 도구**: Claude Code + Manual Code Review
**참고**: 본 리뷰는 면접관 관점에서 작성되었으며, 건설적 피드백을 목적으로 합니다.

---

🎯 주요 리뷰 결과 요약

  종합 평가: 5.5/10 (중급 개발자 수준)

  강점 (65/100)

  - ✅ 계층형 아키텍처 이해도 우수
  - ✅ MySQL + Neo4j + Redis 멀티 DB 통합
  - ✅ JWT + OAuth2 보안 구현
  - ✅ 최신 기술 스택 (Spring Boot 3, Java 17)
  - ✅ 깔끔한 코드 구조

  치명적 약점

  - 🚨 테스트 커버리지 ~1% (가장 큰 문제)
  - 🚨 URL에 JWT 토큰 노출 (보안 취약점)
  - 🚨 프로덕션에 테스트 코드 방치 (보안 우회)
  - 🚨 Reactive Stream에서 blocking 호출 (성능 문제)
  - 🚨 NullPointerException 버그 (UserService)

  발견된 버그 4개

  1. UserService.updateUser() NPE
  2. 보안 설정 우회 코드 남음
  3. JWT URL 노출
  4. Blocking reactive streams

  채용 추천

  조건부 채용 - 주니어~미드 레벨 포지션, 멘토링 제공 조건

  코드 리뷰 파일에는 다음이 포함되어 있습니다:
  - 12개 섹션의 상세 분석
  - 구체적인 코드 예시와 개선 방안
  - 우선순위별 개선 권장사항 15개
  - 면접 시 예상 질문 12개
  - 채용 가능 여부 평가
