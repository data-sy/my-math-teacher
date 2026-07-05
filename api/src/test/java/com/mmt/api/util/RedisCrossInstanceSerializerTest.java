package com.mmt.api.util;

import com.mmt.api.dto.concept.ConceptResponse;
import com.mmt.api.repository.concept.ConceptDepth;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 인스턴스 간(blue/green) Redis 캐시 역직렬화 회귀 가드.
 *
 * <p>M4 무중단 배포 §4 After 측정에서 발견된 버그: 한 백엔드 인스턴스(blue)가
 * 그래프 CTE 결과를 Redis 에 캐시하면, 그 값을 읽기만 하는 다른 인스턴스(green)가
 * List/Map 을 String 으로 역직렬화해 소비측 (List)/(Map) 캐스트에서
 * ClassCastException → 401 을 냈다. 원인은 RedisUtil.set() 이 write 마다 공유
 * 싱글턴 RedisTemplate 의 value serializer 를 갈아끼운 것 — write 이력이 없는
 * 인스턴스는 기본 StringRedisSerializer 로 남았다.
 *
 * <p>이 테스트는 @SpringBootTest 를 쓰지 않고(전체 컨텍스트 기동의 CI 프로파일
 * 취약성 회피) 같은 Redis 를 가리키는 <b>독립된 두 RedisTemplate</b>(=두 JVM 을
 * 흉내)을 구성한다. writer 만 write 하고 reader 는 write 이력 없이 read 만 한다 —
 * 즉 버그가 재현되던 정확한 조건. 두 템플릿의 serializer 설정은 RedisConfig 와
 * 동일하게(GenericJackson2JsonRedisSerializer) 맞춘다.
 *
 * <p>다루는 시나리오:
 * <ol>
 *   <li>신↔신 round-trip: 실제 런타임 타입(ArrayList&lt;ConceptResponse&gt;,
 *       ArrayList&lt;Integer&gt;, ArrayList&lt;ConceptDepth&gt; record, String)이
 *       인스턴스·타입 무관하게 복원되는가.</li>
 *   <li>구↔신 전환(배포 오버랩): 구 포맷(StringRedisSerializer/타입미지정)으로
 *       쓰인 값을 신 serializer 로 읽을 때 예외가 아니라 <b>null(부재)</b>로
 *       강등되는가 — RedisUtil.get() 의 fail-closed fallback.</li>
 *   <li>불변 컬렉션 계약: Stream.toList() 불변 리스트는 round-trip 되지 않으므로
 *       (ConceptService 가 ArrayList 로 정규화하는 이유) get() 이 null 로 강등하는가.</li>
 * </ol>
 */
@Testcontainers
class RedisCrossInstanceSerializerTest {

    @Container
    static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

    private LettuceConnectionFactory writerFactory;
    private LettuceConnectionFactory readerFactory;
    private RedisTemplate<String, Object> writer;   // 캐시를 채우는 인스턴스(blue)
    private RedisTemplate<String, Object> reader;   // write 이력 없이 읽기만 하는 인스턴스(green)
    private RedisUtil readerRedisUtil;              // green 의 RedisUtil (get() fallback 검증용)

    @BeforeEach
    void setUp() {
        writerFactory = newFactory();
        readerFactory = newFactory();
        writer = newTemplate(writerFactory, new GenericJackson2JsonRedisSerializer());
        reader = newTemplate(readerFactory, new GenericJackson2JsonRedisSerializer());
        // RedisUtil 은 두 템플릿(일반/blacklist)을 받는데, 여기선 동일 reader 템플릿을
        // 양쪽에 주입해 get() 경로의 fallback 만 격리 검증한다.
        readerRedisUtil = new RedisUtil(reader, reader);
    }

    @AfterEach
    void tearDown() {
        writerFactory.destroy();
        readerFactory.destroy();
    }

    private LettuceConnectionFactory newFactory() {
        LettuceConnectionFactory factory = new LettuceConnectionFactory(
                new RedisStandaloneConfiguration(REDIS.getHost(), REDIS.getMappedPort(6379)));
        factory.afterPropertiesSet();
        return factory;
    }

    // 값 serializer 를 지정해 템플릿 구성. 신 포맷은 GenericJackson2Json,
    // 구 포맷 재현은 StringRedisSerializer 를 넘긴다.
    private RedisTemplate<String, Object> newTemplate(
            LettuceConnectionFactory factory,
            org.springframework.data.redis.serializer.RedisSerializer<?> valueSerializer) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(valueSerializer);
        template.setConnectionFactory(factory);
        template.afterPropertiesSet();
        return template;
    }

    @Test
    @DisplayName("한 인스턴스가 캐시한 List/record/String 을, write 이력이 없는 다른 인스턴스가 원래 타입 그대로 읽는다")
    void cachedValuesRoundTripAcrossInstances() {
        // given: writer(blue) 가 각 그래프 캐시 경로의 실제 런타임 타입으로 캐시
        ConceptResponse c = new ConceptResponse();
        c.setConceptId(7595);
        c.setConceptName("이차방정식");
        List<ConceptResponse> objs = new ArrayList<>();   // ConceptConverter → ArrayList
        objs.add(c);
        writer.opsForValue().set("graph:v2:prerequisites:objs:7595:5", objs, 60_000, TimeUnit.MILLISECONDS);

        // ConceptService 는 ids 캐시를 ArrayList 로 정규화(불변 Stream.toList() 는
        // GenericJackson2Json @class 역직렬화 불가 — 그 계약을 여기서 그대로 반영).
        List<Integer> ids = Stream.of(6646, 7595)
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        writer.opsForValue().set("graph:v2:prerequisites:ids:7595:5", ids, 60_000, TimeUnit.MILLISECONDS);

        // depthmap 은 Map<Integer,Integer> 를 직접 캐시하지 않는다: JSON object 키는
        // String 이라 Integer 키가 유실된다. ConceptService 는 List<ConceptDepth> 로
        // 캐시하고 read 후 Map 을 재구성한다 — 여기서도 그 표현을 검증한다.
        List<ConceptDepth> depthPairs = new ArrayList<>();
        depthPairs.add(new ConceptDepth(6646, 0));
        depthPairs.add(new ConceptDepth(7595, 2));
        writer.opsForValue().set("graph:v2:prerequisites:depthmap:7595:3", depthPairs, 60_000, TimeUnit.MILLISECONDS);

        writer.opsForValue().set("refresh:user@x:jti", "refresh-token-abc", 60_000, TimeUnit.MILLISECONDS);

        // when/then: reader(green) 는 이 키들에 write 한 적이 없다 → 버그 재현 조건

        // 1) List<ConceptResponse> — 요소가 String/Map 이 아니라 ConceptResponse 로 복원돼야
        Object objsRead = reader.opsForValue().get("graph:v2:prerequisites:objs:7595:5");
        assertThat(objsRead).isInstanceOf(List.class);
        List<?> objsList = (List<?>) objsRead;
        assertThat(objsList).hasSize(1);
        assertThat(objsList.get(0)).isInstanceOf(ConceptResponse.class);
        ConceptResponse readC = (ConceptResponse) objsList.get(0);
        assertThat(readC.getConceptId()).isEqualTo(7595);
        assertThat(readC.getConceptName()).isEqualTo("이차방정식");

        // 2) List<Integer> (ArrayList 로 정규화된 값이 round-trip 되어야)
        Object idsRead = reader.opsForValue().get("graph:v2:prerequisites:ids:7595:5");
        assertThat(idsRead).isInstanceOf(List.class);
        // equals 로 원소 타입까지 판별: Integer 6646 != String "6646"
        assertThat((List<?>) idsRead).isEqualTo(List.of(6646, 7595));

        // 3) List<ConceptDepth> (record) — 요소가 복원돼 int 필드를 그대로 읽어야 하고,
        //    거기서 재구성한 Map 은 Integer 키 조회가 살아있어야 한다(날카로운 경계:
        //    ProbabilityService 가 entry.getKey() 를 int 로 소비).
        Object depthPairsRead = reader.opsForValue().get("graph:v2:prerequisites:depthmap:7595:3");
        assertThat(depthPairsRead).isInstanceOf(List.class);
        List<?> pairsList = (List<?>) depthPairsRead;
        assertThat(pairsList).allSatisfy(e -> assertThat(e).isInstanceOf(ConceptDepth.class));
        Map<Integer, Integer> rebuilt = new HashMap<>();
        for (Object e : pairsList) {
            ConceptDepth cd = (ConceptDepth) e;
            rebuilt.put(cd.conceptId(), cd.depth());
        }
        assertThat(rebuilt.get(6646)).isEqualTo(0);
        assertThat(rebuilt.get(7595)).isEqualTo(2);

        // 4) String (refresh token / blacklist 경로)
        Object tokenRead = reader.opsForValue().get("refresh:user@x:jti");
        assertThat(tokenRead).isEqualTo("refresh-token-abc");
    }

    @Test
    @DisplayName("구 포맷(StringRedisSerializer) 으로 쓰인 값을 신 serializer 로 읽으면 예외가 아니라 null 로 강등된다")
    void legacyFormatValue_isReadAsAbsent_notThrown() {
        // 배포 오버랩: 구 인스턴스가 StringRedisSerializer 로 남긴 값들.
        RedisTemplate<String, Object> legacyWriter =
                newTemplate(writerFactory, new StringRedisSerializer());
        // (a) 구 컬렉션 캐시: 타입정보 없는 bare JSON 배열 → 신 @class(WRAPPER_ARRAY) 파싱 실패
        legacyWriter.opsForValue().set("graph:prerequisites:ids:7595:5", "[6646,7595]",
                60_000, TimeUnit.MILLISECONDS);
        // (b) refresh token: JSON 이 아닌 raw JWT 문자열
        legacyWriter.opsForValue().set("refresh:user@x:jti", "eyJhbGciOiJIUzI1NiJ9.payload.sig",
                60_000, TimeUnit.MILLISECONDS);

        // RedisUtil.get() 은 SerializationException 을 삼키고 null 을 돌려줘야 한다
        // → 캐시 miss(재계산)/토큰 부재(재로그인)로 안전 강등, 500 아님.
        assertThat(readerRedisUtil.get("graph:prerequisites:ids:7595:5")).isNull();
        assertThat(readerRedisUtil.get("refresh:user@x:jti")).isNull();
    }

    @Test
    @DisplayName("불변 리스트(Stream.toList())는 round-trip 되지 않으며 get() 은 null 로 강등된다")
    void immutableListValue_doesNotRoundTrip_readAsAbsent() {
        // ConceptService 가 ids 를 ArrayList 로 정규화하는 이유를 고정하는 가드:
        // 불변 ImmutableCollections$ListN 은 @class 로 역직렬화되지 않는다.
        List<Integer> immutable = Stream.of(6646, 7595).toList();
        writer.opsForValue().set("graph:v2:prerequisites:ids:7595:5", immutable,
                60_000, TimeUnit.MILLISECONDS);

        // 신 serializer 로 read 시 InvalidTypeIdException → RedisUtil.get() 이 null 강등
        assertThat(readerRedisUtil.get("graph:v2:prerequisites:ids:7595:5")).isNull();
    }
}
