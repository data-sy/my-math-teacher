# ADR 0014: `/error` 디스패치를 permitAll 로 열어 401 마스킹을 제거한다

## Status
Accepted

## Context

M7 런치 A4 트리아지에서 "프로덕션 인증 전면 불능 — 백엔드가 자기 발급 access 토큰을 401" 로
진단된 런치 블로커가 있었다. 재조사 결과 **인증 문제가 아니었고**, 이 앱의 401 이
"인증 거부"와 "내부 오류"를 구분하지 못하는 것이 진짜 결함이었다.

메커니즘 (Spring Boot 3.1 / Spring Security 6):

1. 전용 핸들러가 없는 예외·404, 그리고 `response.sendError(...)` 는 서블릿 **ERROR 디스패치**(`/error`)를 탄다.
   본 앱의 `JwtAuthenticationEntryPoint`·`JwtAccessDeniedHandler` 는 **둘 다 `sendError`** 를 쓴다.
2. `/error` 가 `SecurityConfig` 의 permitAll 목록에 없어 `anyRequest().authenticated()` 가 적용됐다.
3. `JwtFilter` 는 `OncePerRequestFilter` 기본값(`shouldNotFilterErrorDispatch()=true`)이라
   ERROR 디스패치에서 **실행되지 않아** SecurityContext 가 비어 있다.
4. Spring Boot 3 은 시큐리티 필터체인을 `REQUEST,ASYNC,ERROR` 디스패치 **전부**에 적용한다.

→ 원래 상태코드(404·403·500·모든 미처리 예외)가 **전부 401 로 위장**됐다. 원래 경로의 permitAll 도
무의미하다 — ERROR 디스패치의 URI 는 `/error` 이기 때문이다.

실측 (로컬·프로덕션 동일):

| 요청 | 수정 전 | 기대값 |
|---|---|---|
| `GET /api/v1/hello/no-such-sub` (permitAll 하위, 핸들러 없음) | **401** | 404 |
| `GET /api/v1/chapters` (permitAll) | **401** | 404 |
| `POST /api/v1/auth/signup` 잘못된 JSON (permitAll) | **401** (Spring 은 400 으로 resolve) | 400 |
| M7 진단 저장·큐 조회 (테이블 미적용 → 500) | **401** | 500 |

이 마스킹이 실제로 초래한 피해:

- **오진**: 프로덕션 500(M7 테이블 미적용)이 401 로 보여 한 세션 전체가 JWT 인증 경로를 뒤졌다.
- **프론트 오작동**: `web-v2/src/api/client.ts` 는 401 을 "토큰 만료"로 해석해 reissue 재시도 후
  `clearAccessToken()` → 내부 오류가 날 때마다 사용자를 **강제 로그아웃**시켰다.
- **관측 불가**: 마스킹이 살아있는 한 어떤 401 도 원인 추적이 불가능해, 다른 모든 조사의 선행 차단 요인이었다.

이 함정은 이미 부분 인지돼 있었다 — M7 spec-01 이 `DiagnosisException`(HttpStatus 보유) + 전용
`@ExceptionHandler` 로 **신규 진단 경로만 우회**했고(커밋 `04180ae`), 구 경로의 동일 마스킹은
"무변경 보존"으로 남겨 **residual ④** 로 기록돼 있었다(`docs/handoff/spec-01-verification-bundle.txt`).
근본 원인은 미수정 상태였다.

## Decision

`SecurityConfig` 의 permitAll 목록에 **`.requestMatchers("/error").permitAll()`** 를 추가한다.

`sendError` 가 상태코드를 **먼저 세팅한 뒤** ERROR 디스패치를 걸기 때문에, 이 변경은
정상 401 을 보존하면서 잘못 마스킹된 것만 복구한다:

| 원래 의도 | 수정 전 | 수정 후 |
|---|---|---|
| 401 인증 부재 (`JwtAuthenticationEntryPoint`) | 401 (우연히 일치) | **401 유지** |
| 403 권한 부족 (`JwtAccessDeniedHandler`) | 401 ← residual ④ | **403 복구** |
| 404 핸들러 부재 | 401 | **404 복구** |
| 500 미처리 예외 | 401 | **500 복구** |

`server.error.include-message` / `include-stacktrace` 는 Spring Boot 3 기본값 `never` 를 유지한다
(오류 바디 = timestamp/status/error/path).

## Consequences

### Positive
- 오류 상태코드가 정직해져 **프로덕션 장애 진단이 응답만으로 가능**해진다(SSM 로그 발굴 불요).
- **residual ④ 종결** — 구 경로의 소유권 위반이 403 으로 정상 응답된다.
- 프론트 코드 변경 없이 **내부 오류로 인한 강제 로그아웃이 사라진다**.
- `DiagnosisException` 식의 엔드포인트별 우회를 신규 경로마다 반복할 필요가 없어진다.

### Negative
- `/error` 가 익명 접근 가능해진다. 노출면은 Boot 기본 오류 바디(timestamp/status/error/path)뿐이며
  `include-message=never` 로 예외 메시지·스택은 실리지 않는다. 회귀 테스트로 이 기본값을 고정했다.

### Neutral
- 인증 요청의 ERROR 디스패치에는 여전히 SecurityContext 가 비어 있다(`JwtFilter` 가 안 돎).
  `/error` 렌더링이 인증정보를 쓰지 않으므로 실무상 무영향. 필요해지면
  `shouldNotFilterErrorDispatch()=false` override 를 별도 결정으로 추가한다.
- 프론트 mock(`web-v2/src/mocks/handlers.ts`)의 "실서버 미러" 주석은 배포 후 실측과 재대조 대상.

## Alternatives Considered

1. **`JwtFilter.shouldNotFilterErrorDispatch()=false` override만** — ERROR 디스패치에서 인증을 유지해
   인증 요청의 오류는 정상화되지만, `/error` 가 여전히 `authenticated()` 라 **익명 요청의 오류는 계속
   401 로 마스킹**된다. 실제 증상의 중심이 익명 경로(signup·frontier·preview)였으므로 기각.
   (양자 병행이 가장 정확하나 표면이 넓어져 이번 범위에서 제외.)
2. **엔드포인트별 전용 예외 타입 확대(`DiagnosisException` 패턴)** — 이미 존재하는 우회지만
   신규 경로마다 반복해야 하고 404·500 같은 프레임워크 발생 오류는 덮지 못한다. 근본 해결이 아니라 기각.
3. **`spring.security.filter.dispatcher-types=request`** — 시큐리티 필터를 ERROR/ASYNC 에서 제외.
   전역 필터 적용 범위를 바꿔 부작용 예측이 어렵고, 비인증 상태로 `/error` 가 렌더링되는 건 동일. 기각.

## References
- 관련 ADR: ADR-0010 (M7 진단 플래그·매핑), ADR-0006 (refresh 쿠키·CSRF)
- 백로그 정본: `docs/backlog/m7-prod-auth-fresh-token-401.md`
- residual ④ 원기록: `docs/handoff/spec-01-verification-bundle.txt` (이탈 ③ 항목, 커밋 `04180ae`)
- 구현: 커밋 `d0f4c41` / 회귀 테스트: `api/src/test/java/com/mmt/api/config/ErrorDispatchMaskingTest.java`
- 함께 적용된 하드닝: 커밋 `1c7c29e` (access 토큰 `jti`·`iat`) / `TokenProviderJtiUniquenessTest`
