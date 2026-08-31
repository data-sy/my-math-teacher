# [Infra/Test] 테스트 스위트가 CI 에서 못 돈다 — `skip_tests` 우회가 상수가 된 진짜 이유

- **상태:** ✅ **종결 (2026-08-31)** — CI 전 스위트 초록 4회 연속(`181 tests, 0 failures`) 후
  `skip_tests` 우회를 제거해 **배포 게이트를 복구**했다. 잔여 없음(아래 §종결)
- **갱신:** 2026-08-31 — 원인 A 사각지대까지 해소(`162054c`, 브랜치 `fix/ci-test-profile-include`) · 로컬 전 스위트 `181/0`.
  ⚠️ **실제 CI 초록은 아직 미증명** — 로컬에서 CI 조건을 재현한 것까지다. 아래 §남은 것
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

## 남은 것 — 왜 아직 CI 전체 초록이 아닌가 (2026-08-31)

**원인 A 는 사각지대까지 닫혔다. 남은 것은 원인 C(러너 자원) 하나**이고, 그래서 아직 `skip_tests` 를 못 뗀다.

2026-08-26 에 "프로파일 미지정 4개 클래스"로 적어둔 목록을 CI 조건 재현으로 실측하니
**실제로 손댈 대상은 2개**였다:

| 클래스 | CI 조건 재현 (2026-08-31) | 처리 |
|---|---|---|
| `ApiApplicationTests` | ❌ `DataSourceBeanCreationException` | ✅ `@ActiveProfiles("test")` + MySQL-only 컨테이너 |
| `RedisUtilTest` | ❌ 같은 원인 | ✅ 동일 |
| `ConceptServiceTest` | — 실행 자체가 없음 | 대상 아님 — 테스트 메서드 없는 의도적 빈 클래스(M1 defer 기록용) |
| `RedisCrossInstanceSerializerTest` | ✅ 통과 | 대상 아님 — 자체 `GenericContainer` |

> 함께 확인: `HealthControllerTest` · `AuthControllerReissueTest` 도 프로파일이 없지만 CI 조건에서
> 통과한다(전체 컨텍스트를 안 띄운다). 즉 "프로파일 미지정" 자체가 아니라 **전체 컨텍스트 기동 여부**가 갈림선이다.

**실패 원인은 플레이스홀더가 아니라 DataSource 부재였다.** 최초 진단(`${...}` 미해결)은
`profiles.default` 전환으로 이미 해소됐고, 남아 있던 두 클래스는 그 다음 층에서 죽었다 —
`test` 프로파일을 안 쓰니 Testcontainers 도 안 물려 datasource 자체가 없었다.

**`ApiApplicationTests` 의 열린 결정은 `MySqlOnlyTestcontainersConfig` 로 닫혔다**(2026-08-31, 사용자 승인).
근거: 프로덕션이 M2 이후 CTE-only + 더미 `GDB_*` 로 뜨므로 **실 Neo4j 를 띄운 형상은 어디에도 없다.**
Neo4j 리포지토리·드라이버 빈은 이 구성에서도 배선되므로(lazy connect) 배선 보증은 유지되고, 잃는 것은
*실제 접속* 하나인데 그건 프로덕션도 하지 않는다. 접속 부재 상태의 기동 무결성은
`Neo4jAbsentBootSmokeTest`(M4 spec-01 R1)가 플래그 ON 쪽에서 이미 지킨다. 러너 자원(원인 C)에도
유리하고 M3 폐기 방향과도 정합이다.

> ⚠️ **원인 B 기록과의 불일치는 아직 안 닫혔다.** run 2 로그에는 `RedisUtilTest` 가 실패 목록에
> 없었다고 적혀 있는데, 2026-08-31 로컬 CI-조건 재현에서는 **실패했다**(DataSource 부재).
> 로컬 재현이 CI 를 대신하지는 못한다 — **실제 CI 로그로 확정할 것.** 추정으로 메우지 말 것.

### CI 실측 (2026-08-31) — 처음으로 초록

run [33372615775](https://github.com/data-sy/my-math-teacher/actions/runs/33372615775) · 브랜치 `ci/tests-only-input` · `skip_tests=false` `tests_only=true`

```
test            ✅ success  9m03s (테스트 실행분 2m10s)
build-and-push  ⏭ skipped  ← tests_only 가드
deploy          ⏭ skipped  ← build-and-push 를 needs
```

업로드된 리포트: **`181 tests · 0 failures · 0 ignored` · 42 클래스** — 로컬과 같은 수라
"조용히 안 돌아서 초록"이 아니다.

**원인 C 는 발현하지 않았다. 다만 반증된 것은 아니다.** 2026-08-15 run 2 는 redis 서비스를 붙이고도
25분에 잘렸고 그것이 C(러너 자원)를 세운 근거였는데, **그 측정은 원인 A 가 살아 있는 상태**였다 —
컨텍스트 로드에 실패하는 클래스들이 매달리며 시간을 먹었을 가능성이 크다. A 를 닫으니 같은 러너에서
9분에 끝났다. 즉 **C 를 별도 원인으로 상정할 근거가 약해졌다.** 1회 관측이라 flaky 가능성은 남는다 —
특히 성능 회귀 테스트는 러너 성능에 민감하고, 워크플로 주석도 `skip_tests` 도입 사유로 "perf flaky"를 적어뒀다.

### ▶ 종결 — `skip_tests` 제거 (2026-08-31)

**선행 조건(환경):** 3306·6379 를 다른 도커 스택이 잡고 있으면 검증이 불가능하다.
`docker ps` 로 확인하고 남의 스택이면 `docker compose stop`(데이터 보존) 후 MMT 인프라를 띄운다 —
`CLAUDE.local.md` 참조. **2026-08-26 세션은 이 조건이 안 맞아 여기서 멈췄다.**

흔들림을 먼저 실측했다 — `tests_only=true` 로 4회(1회 + 병렬 3회):

| run | job 시간 | 테스트 |
|---|---|---|
| [33372615775](https://github.com/data-sy/my-math-teacher/actions/runs/33372615775) | 9m03s | `181 · 0 fail · 0 ignored` (2m10.32s) |
| [33374492956](https://github.com/data-sy/my-math-teacher/actions/runs/33374492956) | 7m09s | `181 · 0 fail · 0 ignored` (2m03.10s) |
| [33374495386](https://github.com/data-sy/my-math-teacher/actions/runs/33374495386) | 8m33s | `181 · 0 fail · 0 ignored` (2m11.08s) |
| [33374498369](https://github.com/data-sy/my-math-teacher/actions/runs/33374498369) | 6m58s | `181 · 0 fail · 0 ignored` (2m13.45s) |

**테스트 실행분 편차는 10초**(2m03~2m13) — 걱정했던 성능 회귀 테스트의 러너 민감성은 나타나지 않았다.
job 총시간의 2분 편차는 테스트가 아니라 앞단(체크아웃·JDK·의존성·이미지 pull)이고 20분 상한까지 여유가 크다.

그래서 `skip_tests` input 과 `if` 가드를 제거했다. **이제 배포는 테스트를 실제로 통과해야 한다.**
`tests_only` 는 남겼다 — 배포 없이 게이트만 보는 용도.

- 되돌리는 비용은 낮다(input 재추가 1커밋). flaky 가 나타나면 그때 대응한다
- ⚠️ dispatch 는 **원격 ref** 를 돈다. 워크플로를 고쳤으면 push 후 그 브랜치를 `--ref` 로 지정한다

**검증 방법(로컬 재현):** `application-securelocal.yml` 을 치워 CI 조건을 만들고 **고치기 전에
실패를 먼저 확인**한 뒤 green 을 본다. 백업·md5 원복 확인 필수(스크립트가 `trap` 으로 항상 원복하게 짠다).
로컬 redis 에 requirepass 가 걸려 있으면 `TEST_REDIS_PASSWORD` 로 넘긴다 — application-test.yml 이 설계한 경로다.

### 로컬 전 스위트의 현재 실측 (2026-08-31)

**`181 tests, 0 failed`** (42 클래스, `cleanTest` 강제, 3m44s) — **MMT 인프라를 띄운 상태의 첫 전 스위트 초록**이다.
2026-08-26 의 `181 tests, 7 failed` 는 예고대로 환경 문제였다: 3306 을 다른 도커 스택이 점유해
`Access denied for user 'mmt2024'` 가 났던 것이고, MMT MySQL 을 띄우니 사라졌다.

> 전 스위트를 돌리면 `shared/benchmark/neo4j-snapshot-<날짜>.json` 의 `generated_at` 이 갱신된다(내용은 동일).
> 커밋 전 `git status` 에서 걸러낼 것.

## 왜 지금까지 안 보였나

- 배포 때마다 `skip_tests=true` 로 넘겼다 — 게이트가 꺼져 있으니 신호가 없었다
- 로컬은 항상 초록이라 "테스트는 통과한다"는 인식이 유지됐다
- 첫 실패(57분 행)는 **로그조차 안 남아** 원인을 못 봤다

## 조치 (순서 있음)

1. ~~**원인 A 먼저** — `include: securelocal` 을 `!test` 조건부로. 7개 클래스가 한 번에 풀린다~~
   → ✅ **완료.** 2026-08-26 `profiles.default` 전환(`222c8d9`, `!test` 조건부가 아님 — 위 §원인 A) +
   2026-08-31 프로파일 미지정 2개 클래스(`162054c`, 위 §남은 것 표)
2. ~~**원인 C** — 컨테이너 재사용/싱글톤으로 러너 자원 압박 완화~~
   → 2026-08-31 CI 실측에서 **발현하지 않았다**(9분 완주, 위 §CI 실측). 미착수로 두되 차단 요소가 아니다
3. ~~`skip_tests` input 과 `if: ${{ !inputs.skip_tests }}` 가드를 **제거**~~ → ✅ 2026-08-31 완료(위 §종결)
4. 그 전까지 배포는 `skip_tests=true` + **로컬 전 스위트 실측**을 근거로 한다(현행)

## 현재 우회의 위험

`skip_tests=true` 배포는 **로컬에서 테스트를 돌렸다는 사람의 기억**에 의존한다.
2026-08-15 M8 배포는 `cleanTest` 강제 재실행으로 181 green 을 실측하고 넘어갔지만,
그 절차가 강제되지 않는다. ⚠️ 특히 `./gradlew test` 는 **`UP-TO-DATE` 로 조용히 스킵**되므로
`cleanTest` 없이 본 초록은 위장일 수 있다(2026-08-15 실제로 한 번 속았다).
