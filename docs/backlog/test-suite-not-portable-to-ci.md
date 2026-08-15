# [Infra/Test] 테스트 스위트가 CI 에서 못 돈다 — `skip_tests` 우회가 상수가 된 진짜 이유

- **상태:** 🔵 착수 대기 · 비차단(배포는 `skip_tests=true` 로 가능) · **원인 3종 실측 완료**
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

### 원인 A — 프로파일 의존 (실패 7개 클래스)

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

## 왜 지금까지 안 보였나

- 배포 때마다 `skip_tests=true` 로 넘겼다 — 게이트가 꺼져 있으니 신호가 없었다
- 로컬은 항상 초록이라 "테스트는 통과한다"는 인식이 유지됐다
- 첫 실패(57분 행)는 **로그조차 안 남아** 원인을 못 봤다

## 조치 (순서 있음)

1. **원인 A 먼저** — `include: securelocal` 을 `!test` 조건부로. 7개 클래스가 한 번에 풀린다
2. **원인 C** — 컨테이너 재사용/싱글톤으로 러너 자원 압박 완화
3. 둘 다 되면 `skip_tests` input 과 `if: ${{ !inputs.skip_tests }}` 가드를 **제거** — 워크플로 주석이
   원래 약속한 상태로 되돌린다
4. 그 전까지 배포는 `skip_tests=true` + **로컬 전 스위트 실측**을 근거로 한다(현행)

## 현재 우회의 위험

`skip_tests=true` 배포는 **로컬에서 테스트를 돌렸다는 사람의 기억**에 의존한다.
2026-08-15 M8 배포는 `cleanTest` 강제 재실행으로 181 green 을 실측하고 넘어갔지만,
그 절차가 강제되지 않는다. ⚠️ 특히 `./gradlew test` 는 **`UP-TO-DATE` 로 조용히 스킵**되므로
`cleanTest` 없이 본 초록은 위장일 수 있다(2026-08-15 실제로 한 번 속았다).
