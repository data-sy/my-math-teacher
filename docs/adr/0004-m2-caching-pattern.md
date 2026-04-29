# ADR 0004: M2 캐싱 패턴 — RedisUtil 직접 호출, Spring Cache 미도입

## Status

Accepted

## Context

Milestone 2의 CTE 성능 보상 수단으로 캐싱이 필요하다. spec-02 1차 작성에서는 Spring Cache(`@Cacheable`/`@CacheEvict` + `RedisCacheManager` Bean)를 가정했으나, audit에서 다음 사실이 확인됐다:

- 현재 프로젝트에 Spring Cache 인프라 미도입 — `@EnableCaching`/`@Cacheable`/`RedisCacheManager` 모두 부재
- Redis는 `RedisUtil`(`api/src/main/java/com/mmt/api/util/RedisUtil.java`)을 통한 직접 호출 패턴으로만 사용 중 (JWT 토큰 저장 용도)
- Spring Cache 도입 시 `@EnableCaching` + `RedisCacheManager` Bean 등록 + 직렬화 설정 + `condition` SpEL 검증 등 신규 인프라 작업이 추가됨 (M2 범위 확장)

본 ADR은 M2 캐싱 패턴을 단일 결정으로 고정한다.

## Decision

M2의 그래프 쿼리 캐싱은 **`RedisUtil` 직접 호출 패턴**을 채택한다. Spring Cache(`@Cacheable`)는 도입하지 않는다.

캐싱 적용 형태:

```java
public List<Integer> findPrerequisiteCached(int conceptId, int maxDepth) {
    String key = "graph:prerequisites:" + conceptId + ":" + maxDepth;
    @SuppressWarnings("unchecked")
    List<Integer> cached = (List<Integer>) redisUtil.get(key);
    if (cached != null) return cached;

    List<Integer> result = mysqlConceptRepository.get()
        .findPrerequisiteConceptIds(conceptId, maxDepth);
    redisUtil.set(key, result, TTL_24H);
    return result;
}
```

규약:
- 키 prefix `graph:prerequisites:` (메서드별 추가 prefix로 분리: `graph:nodes-by-concept:`, `graph:to-concepts:`)
- TTL 24시간 (`TTL_24H` 상수)
- 무효화: 운영자 수동 endpoint에서 prefix 기반 일괄 삭제 또는 TTL 자연 만료

## Consequences

### Positive
- 기존 Redis 사용 패턴(`RedisUtil`)과 일관성 유지 — 새 컨벤션 학습 비용 없음
- M2 범위가 spec-02 본 목적(분기·캐싱)에 집중되며 인프라 도입 작업이 사라져 일정 단축
- `condition` SpEL 검증, 빈 등록 순서, 자동 직렬화 동작 검증 등 부수적 디버깅 부담 제거
- 캐시 hit/miss 로직이 메서드 본문에 명시적으로 보여 디버깅·로깅이 단순

### Negative
- 캐시 적용 메서드마다 본문에 if-else 6줄이 반복됨 (어노테이션보다 verbose)
- 향후 캐시 정책 변경 시 메서드별 수정 필요 (어노테이션이라면 한 줄 수정)

### Neutral
- 향후 모니터링·운영 자동화 도입 마일스톤(M3 권장 — ADR 0005 참조)에서 Spring Cache로 승격할 수 있음. 본 ADR은 M2 범위에 한정한 결정이며, 향후 결정으로 대체 가능

## Alternatives Considered

1. **Spring Cache(`@Cacheable`) + `RedisCacheManager` Bean 도입** — 기각. 인프라 신규 도입 작업이 spec-02 범위를 벗어나며, `condition` SpEL로 인스턴스 필드(`useMysqlCte`)에 접근하는 동작 검증이 추가 부담. M2 핵심 목적(Neo4j 제거)과 무관한 작업.
2. **캐싱 없이 CTE 단독 사용** — 기각. CTE 깊이 5의 p95 허용치(<100ms)가 M1 baseline 대비 7.7배 허용임에도 캐시 미스 비율이 높으면 사용자 체감 회귀 가능.
3. **외부 캐시 라이브러리 (Caffeine 등) 도입** — 기각. 단일 인스턴스 환경 가정이 명확하지 않고, Redis가 이미 가용하므로 in-memory 캐시 별도 도입 불필요.

## References

- `api/src/main/java/com/mmt/api/util/RedisUtil.java` — 기존 Redis 사용 패턴
- 적용 spec: `docs/specs/m2/spec-02-service-integration-and-caching.md` Task 2.1·2.2
- 관련 ADR: ADR 0002 §1 (피처 플래그 네임스페이스 — 캐시 키 prefix `graph:*`은 별개 영역)
- 후속 결정 가능: M3에서 Spring Cache 승격 검토 (모니터링 인프라 도입 시 동시 진행 가능)
