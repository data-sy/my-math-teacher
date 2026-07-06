# 포스트모템 — Redis 크로스인스턴스 캐시 역직렬화 버그 (2026-07-05)

> **한 줄:** blue/green 두 백엔드가 같은 Redis 를 공유하자, 한쪽이 캐시한 List/Map 을
> 다른 쪽이 String 으로 읽어 `ClassCastException → 401`. 무중단 배포를 검증하려던
> §4 측정이 이 버그로 오염됐다. 고친 뒤 코드리뷰가 **더 깊은 함정**(배포 오버랩의
> 포맷 비호환)을 잡아내, "고침"이 오히려 무중단을 깰 뻔한 것을 막았다.
>
> 진단 과정의 원 서술 = `docs/benchmark/milestone-4-troubleshooting-2026-07-05.md`(결함 2).
> 이 문서는 **닫힌 결말 + 사람이 가져갈 교훈** 중심.

---

## 1. 무슨 일이 있었나 (타임라인)

1. **증상.** SSM 배포는 green 으로 완주하고 blue-green 전환 로그도 깨끗했는데, 전환 후
   `/api/v1/concepts/nodes/{id}` 같은 **데이터 엔드포인트가 401**. `/health` 스모크는
   통과. 즉 "헬시"인데 데이터 경로가 죽는 상태.
2. **좁히기.** green 컨테이너를 직접 때리면 401, Redis 를 `FLUSHALL` 하면 같은 green 이
   200. 같은 인스턴스는 자기가 쓴 값을 정상으로 읽었다(자기일관성 OK) — **인스턴스 간**
   round-trip 만 깨졌다.
3. **근본 원인 확정** (아래 §2).
4. **수정** (별도 브랜치, PR #46): serializer 를 한 곳에 고정 + 잠복버그 2건 동반 수정.
5. **코드리뷰가 더 깊은 함정 발견** (아래 §4): 첫 수정안은 "신↔신" 만 검증했는데,
   **배포 오버랩(구·신 인스턴스 공존)** 의 크로스버전 read 를 놓쳤다. 이걸 반영해
   키 버전닝 + fail-closed 로 보강한 뒤 머지.

---

## 2. 근본 원인

```java
// RedisUtil.set() — 문제의 코드
public void set(String key, Object o, long duration) {
    redisTemplate.setValueSerializer(new Jackson2JsonRedisSerializer(o.getClass())); // ← 매 write 마다 갈아끼움
    redisTemplate.opsForValue().set(key, o, duration, MILLISECONDS);
}
```

`RedisTemplate` 은 **공유 싱글턴 빈**이다. `set()` 이 write 마다 그 싱글턴의 value
serializer 를 `o.getClass()` 기반으로 **바꿔치기**했다. 그런데 `get()` 은 "그 순간
설정돼 있는" serializer 로 읽는다.

- 어떤 값을 write 한 적 있는 인스턴스 → serializer 가 그 타입 Jackson 으로 바뀌어 있어
  자기 값을 정상 read.
- **그 타입을 write 한 적 없는 인스턴스** → serializer 가 기동 기본값
  `StringRedisSerializer` 그대로 → 캐시된 List 를 **String** 으로 역직렬화 → 소비측
  `(List)` 캐스트에서 `ClassCastException` → `ExceptionTranslationFilter` 가 401.

증폭 요인: `redisBlackListTemplate` 은 별도 `@Bean` 이 없어 **같은 싱글턴**이 주입됐다.
즉 그래프 캐시·리프레시 토큰·로그아웃 blacklist 가 한 템플릿의 serializer 를 서로
갈아끼우며 스레드·타입 간 경쟁까지 했다.

---

## 3. 왜 이렇게 오래 숨어 있었나

- **자기일관성이 착시를 만든다.** 단일 인스턴스(로컬 개발·단일 컨테이너)에서는 write 한
  serializer 로 그대로 read 하므로 **항상 정상으로 보인다.** 버그는 "두 번째 인스턴스"가
  생기는 순간(=무중단 배포의 blue/green)에만 나온다.
- **테스트가 Redis 를 mock 했다.** 기존 `ConceptServiceCacheTest` 는 `RedisUtil` 을
  모킹해 hit/miss/키prefix 만 검증했다 — **실제 직렬화를 한 번도 태우지 않았다.** 그래서
  depthmap 의 Integer 키가 JSON 에서 String 으로 뭉개지는 별개 잠복버그(M2부터)도
  덮여 있었다.

> **교훈 T1 — "직렬화·영속 경계는 mock 하지 말고 실물(Testcontainers)로 태워라."**
> mock 은 로직은 잡지만 **경계의 계약**(round-trip, 타입 보존)은 못 잡는다.

---

## 4. 코드리뷰가 잡은 더 깊은 함정 (이 사건의 진짜 교훈)

첫 수정안은 serializer 를 `GenericJackson2JsonRedisSerializer`(타입정보 내장)로 고정해
**신↔신** round-trip 을 통과시켰다. "끝" 처럼 보였다. 배포 방법은 "재배포 시 Redis 를
flush" 로 때우려 했다.

적대적 코드리뷰(워크플로 17에이전트)가 두 가지를 지적했다:

1. **배포 오버랩의 크로스버전 read.** blue/green 컷오버 동안 **구 코드와 신 코드가 같은
   Redis 를 동시에** 읽고 쓴다. 신 포맷(`["java.util.ArrayList",[...]]` 래퍼)과 구 포맷
   (`[...]` 민짜)은 호환되지 않는다. 구 인스턴스가 신 포맷을 읽으면 깨지는데 **구 코드엔
   fallback 이 없다** → 무중단을 지키려던 변경이 오히려 컷오버에 500 을 뿌린다.
   `flush` 한 번으로는 못 막는다 — 오버랩 동안 구 인스턴스가 구 포맷을 계속 다시 쓴다.
2. **flush 의 부작용.** 포맷 안 맞으니 flush 하자던 우회책은 **로그아웃 blacklist 까지
   지운다** → 이미 로그아웃한 액세스 토큰이 다시 유효해지는 auth 우회.

→ 반영: **캐시 키에 버전 네임스페이스(`graph:v2:`)** 를 넣어 구·신이 keyspace 를
공유하지 않게 분리(구 인스턴스는 신 포맷을 절대 안 읽음, 구 엔트리는 TTL 로 자연 만료 →
flush 불필요). 그리고 `RedisUtil.get()` 은 `SerializationException` 을 삼키고
**null(부재)로 강등**(fail-closed) — 잔여 구 포맷/레거시 값을 캐시 miss·재인증으로 안전
처리, 연결 오류는 전파.

> **교훈 T2 — "공유 저장소의 직렬화 포맷을 바꾸면, 롤링/blue-green 배포의 오버랩
> 구간(구·신 공존)의 크로스버전 read 까지 correctness 를 밀어라."**
> 신↔신 round-trip 만 되면 끝이 아니다. 무중단을 만들려는 변경이 무중단을 깨는
> 역설을 조심.
>
> **교훈 T3 — "포맷 마이그레이션은 flush 가 아니라 키/스키마 버전닝으로."**
> 버전닝은 keyspace 를 분리해 오버랩을 무해화하고, 무관한 키(인증 상태)를 지우지
> 않는다. flush 는 "부작용으로 무엇을 지우는가"를 항상 점검.

---

## 5. 최종 수정 요약

| 영역 | 변경 |
|---|---|
| `RedisConfig` | value serializer 를 `GenericJackson2JsonRedisSerializer` 로 **한 번만** 고정(`@class` 타입내장) |
| `RedisUtil` | per-write serializer 뮤테이션 2곳 제거; `get()`/`getBlackList()` 에 `SerializationException`→null fail-closed |
| `ConceptService` (잠복①) | ids 캐시를 불변 `Stream.toList()` → **ArrayList** 정규화 (불변 컬렉션은 `@class` 역직렬화 불가) |
| `ConceptService` (잠복②) | depthmap `Map<Integer,Integer>` 직접 캐시 폐기 → **`List<ConceptDepth>`** 저장 후 read 에서 Map 재구성 (JSON object 키 String 뭉갬 회피) |
| `ConceptService` (오버랩 안전) | 캐시 키 **`graph:v2:`** 버전 네임스페이스 |
| 테스트 | `RedisCrossInstanceSerializerTest`(Testcontainers 실 Redis, 독립 템플릿 2개): 신↔신 round-trip 4종 + 구↔신 레거시 null 강등 + 불변리스트 null 강등 |

---

## 6. 사람이 가져갈 체크리스트 (다음에 캐시/직렬화 건드릴 때)

- [ ] 이 값이 **여러 인스턴스가 공유**하는가? 그렇다면 "단일 인스턴스에서 잘 됨"은 증거가 아니다.
- [ ] 직렬화 포맷을 바꾸는가? → **배포 오버랩의 구↔신 크로스버전 read** 를 그려봐라. 구 코드는 못 고친다.
- [ ] 포맷 전환을 **키/스키마 버전닝**으로 할 수 있는가? (flush 는 최후수단 + 부작용 점검)
- [ ] 역직렬화 실패를 **fail-closed**(null/재계산/재인증)로 강등하는가, 아니면 500 으로 전파하는가?
- [ ] JSON 캐시라면: **Map 의 비-String 키**(Integer)는 유실된다. **불변 컬렉션**(`Stream.toList()`)·**record** 는 serializer 별로 round-trip 여부가 다르다. 실물로 태워 확인.
- [ ] 직렬화 경계 테스트를 **mock 이 아니라 Testcontainers 실물**로 두었는가?

---

## 참조

- 수정: fix 브랜치 `fix/redis-cross-instance-cache-serializer` 커밋 `72d70f7` → **PR #46** → `main` 머지
- 코드 위치: `api/.../config/RedisConfig.java`, `api/.../util/RedisUtil.java`, `api/.../service/ConceptService.java`(`GRAPH_NS`), `api/.../util/RedisCrossInstanceSerializerTest.java`
- 진단 서술(전편): `docs/benchmark/milestone-4-troubleshooting-2026-07-05.md` §결함 2
- 상태·다음 계획 정본: 루트 `m4-worklog.md`
