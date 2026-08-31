# [Infra/Test] 테스트 스위트가 CI 에서 못 돈다 — `skip_tests` 우회가 상수가 된 진짜 이유

- **상태:** 🟡 부분 해소 — **원인 A·B 해결, C 미착수** · 비차단(배포는 `skip_tests=true` 로 가능)
- **갱신:** 2026-08-26 — 원인 A 수정(`222c8d9`, 브랜치 `fix/ci-test-profile-include`). ⚠️ **CI 전체 초록은 아직 아니다** — 아래 §남은 것
- **등록:** 2026-08-15 (M8 배포 시도 중 두 번 연속 CI 실패로 확정)
- **관련:** [프로파일·로깅 위생 정리](../../ROADMAP.md) `Later` 항목(이것이 원인 A 의 본체) · Testcontainers Redis `@ServiceConnection`(Spring Boot 3.2+ 의존)

## 한 줄

**전 스위트가 CI 에서 성공한 적이 한 번도 없다.** `api-ci-cd-with-ec2.yml` 의 `skip_tests` 는
M4 §4 측정용 *한시* 우회로 도입됐고 워크플로 주석도 "측정·destroy 후 되돌린다"고 적어뒀지만,
되돌리면 스위트가 통과하지 못해 사실상 상수로 굳었다. 로컬에서만 초록이다.

## 실측 (2026-08-15, 두 번의 CI 실행)

| run | 조건 | 결과 |
|---|---|---|
| [31879698138](https://github.com/data-sy/my-math-teacher/actions/runs/31879698138) | Redis 없음·타임아웃 없음 | **57분 행 → 러너 소실.** 로그 blob 조차 미업로드(`BlobNotFound`)라 잡 메타데이터로만 추론 가능했다(step 4 가 `in_progress` 인 채 job 이 `failure`) |
| [31894083031](https://github.com/data-sy/my-math-teacher/actions/runs/31894083031) | **redis 서비스 + `timeout-minutes: 20`** | 25분 타임아웃 취소. **로그 확보** → 원인 3종 확정 |

`timeout-minutes` 를 넣은 것이 진단을 가능하게 했다 — 러너가 죽으면 로그가 안 남는다.

### 원인 A — 프로파일 의존 (실패 7개 클래스) — ✅ 2026-08-26 해소

```
java.lang.IllegalArgumentException at PropertyPlaceholderHelper.java:180
  ← BeanCreationException ← IllegalStateException (컨텍스트 로드 실패)
```

`CytoscapeResponseDto` · `ConceptServiceFeatureFlag` · `BfsDepthMapEquivalence` ·
`FeatureFlagIntegration` · `ErrorDispatchMasking` · `GracefulShutdownConfig` · `QueryTimingAspect`

`application.yml` 의 `spring.profiles.include: securelocal` 이 **무조건** 걸려 있는데,
`application-securelocal.yml` 은 gitignore 라 CI 에 없다 → include 가 no-op 이 되고
`${...}` 플레이스홀더가 미해결로 남아 컨텍스트가 뜨지 않는다.
**로컬은 그 파일이 있어서 통과한다** — 환경이 초록을 만들어 주는 구조.

이것이 `Later` 의 *"`spring.profiles.include: securelocal` 을 `!test` 조건부로 전환"* 항목 본체다.

**해소(2026-08-26, `222c8d9`):** `!test` 조건부 대신 **`spring.profiles.default: securelocal`** 로 전환했다 —
`spring.profiles.include` 는 profile-specific 문서(`spring.config.activate.on-profile`) 안에서 쓸 수 없어
"`!test` 조건부 include" 는 애초에 성립하지 않는다. `default` 는 *활성 프로파일이 하나도 없을 때만* 적용되므로
같은 목적을 부작용 없이 달성한다:

| 실행 경로 | 활성 프로파일 | 변화 |
|---|---|---|
| `@ActiveProfiles("test")` | `test` (securelocal 없음) | **이것이 고친 것** — CI 와 같은 배선을 로컬에서도 실행 |
| 프로파일 없는 `bootRun` | `securelocal` | 종전과 동일 |
| 프로덕션 `active=secure` | `secure` | 종전과 동일(원래도 파일 부재로 include 가 no-op) |

`application-test.yml` 이 그동안 securelocal 에 얹혀 있던 것을 스스로 채우게 했다 —
redis(무인증 기본값 + `TEST_REDIS_PASSWORD` 오버라이드) · oauth2 더미 등록(`.oauth2Login()` 이
`ClientRegistrationRepository` 빈을 요구한다) · jwt · `allowed.origins`. 전부 테스트 전용 더미다.

**검증 — CI 조건을 로컬에서 재현**(`application-securelocal.yml` 을 잠시 치움):

| 단계 | 결과 |
|---|---|
| 수정 전 baseline | `FeatureFlagIntegrationTest` 가 CI 와 **동일한 스택**(`PropertyPlaceholderHelper:180`)으로 실패 → 재현 성립 |
| 수정 후 | 원인 A **7개 클래스 16 테스트 green** (실패 0·에러 0·스킵 0, `cleanTest` 강제) |
| 활성 프로파일 실측 | test 컨텍스트 = `"test"` 단독 (securelocal 미포함) |

*baseline 을 먼저 돌린 이유: 시뮬레이션이 실제로 물어뜯는지 확인하지 않으면 수정 후 초록이
"고쳐서 초록"인지 "시뮬레이션이 무력해서 초록"인지 구분되지 않는다.*

### 원인 B — Redis 부재 (✅ 2026-08-15 해소)

Redis 를 쓰는 테스트 10개 중 **8개가 로컬 `localhost:6379` 를 그냥 쓴다**(자체
`GenericContainer` 를 띄우는 건 `RedisCrossInstanceSerializerTest`·`RefreshTokenRotationIntegrationTest` 뿐).
`TestcontainersConfig` 는 MySQL·Neo4j 만 제공한다.

→ 워크플로에 `redis:7-alpine` 서비스 컨테이너 추가로 **해소됨**(PR [#56](https://github.com/data-sy/my-math-teacher/pull/56)).
두 번째 실행에서 순수 Redis 의존 테스트(`RedisUtil`·`DiagnosisRateLimiter`·`AdminCacheController`·
`TokenProviderJtiUniqueness`·`ConceptServiceCache`)는 **실패 목록에서 사라졌다.**
근본 해법(Testcontainers Redis `@ServiceConnection`)은 Spring Boot 3.2+ 의존이라 별도.

### 원인 C — Testcontainers 컨테이너 기동 실패 (실패 1개 클래스)

```
ContainerLaunchException → RetryCountExceededException
  → com.github.dockerjava.api.exception.InternalServerErrorException
```

`RefreshTokenRotationIntegrationTest`. 테스트 클래스마다 MySQL·Neo4j 를 새로 띄우는 구조라
러너 자원(디스크·메모리)에 부딪힌 것으로 보인다. **컨테이너 재사용**(`withReuse`·싱글톤 패턴)이나
클래스별 컨텍스트 캐싱 정비가 후보. 로컬에선 자원이 넉넉해 드러나지 않는다.

## 남은 것 — 왜 아직 CI 전체 초록이 아닌가 (2026-08-26)

원인 A 를 고치는 과정에서 **A 의 사각지대**가 드러났다. 고친 것은 `@ActiveProfiles("test")` 를
붙인 클래스뿐이고, **프로파일을 아예 안 붙인 4개 클래스**는 여전히 주변 환경에 의존한다:

| 클래스 | 활성 프로파일 | 의존 |
|---|---|---|
| `ApiApplicationTests` | (없음) → `default`=securelocal | 실 MySQL·Redis |
| `RedisUtilTest` | (없음) | 실 Redis |
| `ConceptServiceTest` | (없음) | — (로컬 통과) |
| `RedisCrossInstanceSerializerTest` | (없음) | 자체 `GenericContainer` |

CI 에는 securelocal 파일이 없으므로 이 4개에 대해서는 **수정 전후가 동일**하다(개선도 악화도 아님).
`@ActiveProfiles("test")` 를 붙이면 원인 A 와 같은 방식으로 풀리지만, `ApiApplicationTests` 는
Testcontainers 를 어떤 조합으로 물릴지(전체 / MySQL-only) 결정이 필요해 **여기서 멈췄다** — 테스트 설계 판단이다.

> ⚠️ **원인 B 의 "실패 목록에서 사라졌다"와 위 표는 서로 안 맞는다.** `RedisUtilTest` 는 프로파일이
> 없어 `${spring.redis.host}` 를 채울 소스가 CI 에 없는데, run 2 로그에서는 실패 목록에 없었다고 기록돼 있다.
> 둘 중 하나가 틀렸다 — **다음에 CI 를 실제로 돌려 로그로 확정할 것.** 추정으로 메우지 말 것.

### ▶ 다음 세션은 여기서부터

**선행 조건(환경):** 3306·6379 를 다른 도커 스택이 잡고 있으면 검증이 불가능하다.
`docker ps` 로 확인하고 남의 스택이면 `docker compose stop`(데이터 보존) 후 MMT 인프라를 띄운다 —
`CLAUDE.local.md` 참조. **2026-08-26 세션은 이 조건이 안 맞아 여기서 멈췄다.**

**할 일:** 위 표의 4개 클래스에 `@ActiveProfiles("test")` 를 붙여 원인 A 와 같은 방식으로 푼다.
셋은 기계적이지만 **`ApiApplicationTests` 하나가 결정을 요구한다** — 이 클래스는 전체 컨텍스트를
띄우므로 DB 가 필요한데, `TestcontainersConfig`(MySQL+Neo4j) 를 물릴지 `MySqlOnlyTestcontainersConfig`
를 물릴지가 갈린다. Neo4j 는 M2 이후 미사용이라 안 띄우는 쪽이 러너 자원(=원인 C)에도 유리하지만,
`contextLoads()` 가 Neo4j 빈까지 포함한 전체 배선을 보증하던 성격을 잃는다. **사용자와 정할 것.**

**검증 방법:** 2026-08-26 과 동일하게 — `application-securelocal.yml` 을 잠시 치워 CI 조건을
재현하고, **고치기 전에 먼저 실패를 확인**한 뒤 수정 후 green 을 본다(백업·md5 원복 확인 필수).

### 로컬 전 스위트의 현재 실측 (2026-08-26)

`181 tests, 7 failed` — 실패 7건은 **선재 환경 문제이지 이 변경 탓이 아니다**(같은 환경에서
main 을 돌려 동일 실패 확인). 근본 원인 = `Access denied for user 'mmt2024'` — 3306 을
다른 도커 스택이 점유해 MMT MySQL 이 아니었다(`CLAUDE.local.md` 의 알려진 포트 충돌).
**MMT 인프라를 띄운 상태의 전 스위트 초록은 아직 미실측.**

## 왜 지금까지 안 보였나

- 배포 때마다 `skip_tests=true` 로 넘겼다 — 게이트가 꺼져 있으니 신호가 없었다
- 로컬은 항상 초록이라 "테스트는 통과한다"는 인식이 유지됐다
- 첫 실패(57분 행)는 **로그조차 안 남아** 원인을 못 봤다

## 조치 (순서 있음)

1. ~~**원인 A 먼저** — `include: securelocal` 을 `!test` 조건부로. 7개 클래스가 한 번에 풀린다~~
   → ✅ **2026-08-26 완료**(`222c8d9`). 단 `!test` 조건부가 아니라 `profiles.default` 로 — 위 §원인 A 참조.
   프로파일 미지정 4개 클래스는 미해결(§남은 것)
2. **원인 C** — 컨테이너 재사용/싱글톤으로 러너 자원 압박 완화
3. 둘 다 되면 `skip_tests` input 과 `if: ${{ !inputs.skip_tests }}` 가드를 **제거** — 워크플로 주석이
   원래 약속한 상태로 되돌린다
4. 그 전까지 배포는 `skip_tests=true` + **로컬 전 스위트 실측**을 근거로 한다(현행)

## 현재 우회의 위험

`skip_tests=true` 배포는 **로컬에서 테스트를 돌렸다는 사람의 기억**에 의존한다.
2026-08-15 M8 배포는 `cleanTest` 강제 재실행으로 181 green 을 실측하고 넘어갔지만,
그 절차가 강제되지 않는다. ⚠️ 특히 `./gradlew test` 는 **`UP-TO-DATE` 로 조용히 스킵**되므로
`cleanTest` 없이 본 초록은 위장일 수 있다(2026-08-15 실제로 한 번 속았다).
