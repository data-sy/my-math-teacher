package com.mmt.api.service;

import com.mmt.api.domain.Answer;
import com.mmt.api.domain.Item;
import com.mmt.api.domain.Probability;
import com.mmt.api.dto.item.PersonalItemsResponse;
import com.mmt.api.repository.answer.AnswerRepository;
import com.mmt.api.repository.item.ItemRepository;
import com.mmt.api.repository.probability.ProbabilityRepository;
import com.mmt.api.repository.userTest.UserTestRepository;
import com.mmt.api.repository.users.UsersRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 맞춤학습지 조건부 출제(Scope B) 단위 테스트.
 * spec: docs/specs/product/spec-01-personalview-conditional-items-scope-b.md
 * D3 개정(2026-06-23): count = 목표 총 문항수, 같은 범위에서 round-robin 으로 채움.
 */
@ExtendWith(MockitoExtension.class)
class ItemServiceTest {

    private static final Long USER_TEST_ID = 1L;
    private static final String USER_EMAIL = "u@example.com";
    private static final Long USER_ID = 10L;

    @Mock private AnswerService answerService;
    @Mock private AnswerRepository answerRepository;
    @Mock private ProbabilityRepository probabilityRepository;
    @Mock private ItemRepository itemRepository;
    @Mock private UsersRepository usersRepository;
    @Mock private UserTestRepository userTestRepository;

    @InjectMocks private ItemService itemService;

    @BeforeEach
    void stubOwnership() {
        lenient().when(usersRepository.findUserIdByUserEmail(USER_EMAIL)).thenReturn(Optional.of(USER_ID));
        lenient().when(userTestRepository.existsByUserTestIdAndUserId(USER_TEST_ID, USER_ID)).thenReturn(true);
    }

    private Answer answer(long id) {
        Answer a = new Answer();
        a.setAnswerId(id);
        return a;
    }

    private Probability prob(int conceptId, int depth) {
        Probability p = new Probability();
        p.setAnswerId(100L);
        p.setConceptId(conceptId);
        p.setToConceptDepth(depth);
        return p;
    }

    private Item item(long itemId, int conceptId) {
        Item i = new Item();
        i.setItemId(itemId);
        i.setConceptId(conceptId);
        i.setConceptName("c" + itemId);
        return i;
    }

    /** 오답 answer → probabilities 스텁 (categoryConcepts 도출용) */
    private void stubAnswersAndProbabilities(List<Probability> probabilities) {
        when(answerRepository.findAnswersByUserTestId(USER_TEST_ID)).thenReturn(List.of(answer(100L)));
        when(probabilityRepository.findProbability(List.of(100L))).thenReturn(probabilities);
    }

    /** 범위 채우기 풀(findItemsByConceptIds) 스텁 */
    private void stubPool(List<Item> pool) {
        when(itemRepository.findItemsByConceptIds(any())).thenReturn(pool);
    }

    private List<Long> ids(List<PersonalItemsResponse> r) {
        return r.stream().map(PersonalItemsResponse::getItemId).toList();
    }

    // --- D1: 맞춤 유형 → depth 필터 ---

    @Test
    void categoryWrong_오답개념_depth0만() {
        stubAnswersAndProbabilities(List.of(prob(5, 0), prob(3, 1), prob(2, 2), prob(9, 3)));
        // 범위 = {5}(depth0). 풀에는 depth0 개념 문항만 반환됨.
        stubPool(List.of(item(500, 5)));

        List<PersonalItemsResponse> r = itemService.findPersonalItems(USER_TEST_ID, USER_EMAIL, "wrong", "nothing", 10);

        assertThat(ids(r)).containsExactly(500L);
    }

    @Test
    void categoryPrerequisite_선수지식_depth1과2만() {
        stubAnswersAndProbabilities(List.of(prob(5, 0), prob(3, 1), prob(2, 2), prob(9, 3)));
        // 범위 = {3,2}(depth1,2)
        stubPool(List.of(item(300, 3), item(200, 2)));

        List<PersonalItemsResponse> r = itemService.findPersonalItems(USER_TEST_ID, USER_EMAIL, "prerequisite", "nothing", 10);

        assertThat(ids(r)).containsExactly(300L, 200L);
    }

    // --- D3: count = 목표 총 문항수, 같은 범위에서 round-robin 채움 ---

    @Test
    void count_부족하면_같은_범위에서_개념당_복수문항_roundrobin_채움_count에서_중단() {
        stubAnswersAndProbabilities(List.of(prob(5, 0), prob(6, 0)));
        // 범위 {5,6}, 개념당 문항 4개씩. count=6 → round-robin 으로 6개 채우고 중단(504,604 미사용).
        stubPool(List.of(item(501, 5), item(502, 5), item(503, 5), item(504, 5),
                         item(601, 6), item(602, 6), item(603, 6), item(604, 6)));

        List<PersonalItemsResponse> r = itemService.findPersonalItems(USER_TEST_ID, USER_EMAIL, "wrong", "nothing", 6);

        // pass1: 501,601 / pass2: 502,602 / pass3: 503,603 → count=6 도달, 504·604 미사용
        assertThat(ids(r)).containsExactly(501L, 601L, 502L, 602L, 503L, 603L);
    }

    @Test
    void count_범위_소진되면_미달_허용() {
        stubAnswersAndProbabilities(List.of(prob(5, 0), prob(6, 0)));
        // 개념당 1문항뿐 → count=10 이어도 2문항만
        stubPool(List.of(item(500, 5), item(600, 6)));

        List<PersonalItemsResponse> r = itemService.findPersonalItems(USER_TEST_ID, USER_EMAIL, "wrong", "nothing", 10);

        assertThat(ids(r)).containsExactly(500L, 600L);
    }

    @Test
    void count_최소6미만이면_6으로_clamp() {
        stubAnswersAndProbabilities(List.of(prob(5, 0)));
        // count=2 요청이나 clamp 하한 6 → 가용한 만큼(개념 1개, 문항 3개) 다 채워 3문항
        stubPool(List.of(item(501, 5), item(502, 5), item(503, 5)));

        List<PersonalItemsResponse> r = itemService.findPersonalItems(USER_TEST_ID, USER_EMAIL, "wrong", "nothing", 2);

        assertThat(r).hasSize(3); // 2로 잘리지 않음(clamp 6) — 범위 소진까지
    }

    @Test
    void distinct_중복_concept는_한_개념으로() {
        stubAnswersAndProbabilities(List.of(prob(5, 0), prob(5, 0), prob(5, 0)));
        stubPool(List.of(item(500, 5)));

        List<PersonalItemsResponse> r = itemService.findPersonalItems(USER_TEST_ID, USER_EMAIL, "wrong", "nothing", 10);

        assertThat(ids(r)).containsExactly(500L);
    }

    // --- D2/D4: 재출제 원본 additive + dedup + 순서 + count 산입 ---

    @Test
    void reExamWrong_원본먼저_그다음_범위채움() {
        stubAnswersAndProbabilities(List.of(prob(3, 1), prob(2, 2)));
        when(itemRepository.findOriginalItemsByUserTestId(USER_TEST_ID, true)).thenReturn(List.of(item(700, 99), item(701, 99)));
        stubPool(List.of(item(300, 3), item(200, 2)));

        List<PersonalItemsResponse> r = itemService.findPersonalItems(USER_TEST_ID, USER_EMAIL, "prerequisite", "wrong", 10);

        // 원본(700,701) 먼저 → 신규(300,200), 번호 1..4
        assertThat(ids(r)).containsExactly(700L, 701L, 300L, 200L);
        assertThat(r).extracting(PersonalItemsResponse::getTestItemNumber).containsExactly(1, 2, 3, 4);
    }

    @Test
    void reExamAll_원본_응시문항_전체_조회() {
        stubAnswersAndProbabilities(List.of(prob(3, 1)));
        when(itemRepository.findOriginalItemsByUserTestId(USER_TEST_ID, false)).thenReturn(List.of(item(700, 99), item(701, 99)));
        stubPool(List.of(item(300, 3)));

        List<PersonalItemsResponse> r = itemService.findPersonalItems(USER_TEST_ID, USER_EMAIL, "prerequisite", "all", 10);

        assertThat(ids(r)).containsExactly(700L, 701L, 300L);
        verify(itemRepository).findOriginalItemsByUserTestId(USER_TEST_ID, false);
    }

    @Test
    void 원본과_범위문항이_같은_item이면_dedup_원본우선() {
        stubAnswersAndProbabilities(List.of(prob(5, 0)));
        when(itemRepository.findOriginalItemsByUserTestId(USER_TEST_ID, true)).thenReturn(List.of(item(500, 5)));
        stubPool(List.of(item(500, 5), item(501, 5))); // 500 중복

        List<PersonalItemsResponse> r = itemService.findPersonalItems(USER_TEST_ID, USER_EMAIL, "wrong", "wrong", 10);

        assertThat(ids(r)).containsExactly(500L, 501L); // 500 1회만
    }

    @Test
    void count_총목표에_원본_산입() {
        stubAnswersAndProbabilities(List.of(prob(5, 0)));
        when(itemRepository.findOriginalItemsByUserTestId(USER_TEST_ID, true)).thenReturn(List.of(item(700, 99), item(701, 99), item(702, 99)));
        stubPool(List.of(item(501, 5), item(502, 5), item(503, 5), item(504, 5)));

        // count=6, 원본 3 → 신규 3개만 채워 총 6
        List<PersonalItemsResponse> r = itemService.findPersonalItems(USER_TEST_ID, USER_EMAIL, "wrong", "wrong", 6);

        assertThat(r).hasSize(6);
        assertThat(ids(r).subList(0, 3)).containsExactly(700L, 701L, 702L); // 원본 먼저
    }

    @Test
    void 원본이_count_이상이면_원본전부_신규생략() {
        // 원본 7개, count=6(clamp) → 원본 전부 포함(초과 허용), 범위 채우기 생략
        when(itemRepository.findOriginalItemsByUserTestId(USER_TEST_ID, false))
                .thenReturn(List.of(item(701, 9), item(702, 9), item(703, 9), item(704, 9), item(705, 9), item(706, 9), item(707, 9)));

        List<PersonalItemsResponse> r = itemService.findPersonalItems(USER_TEST_ID, USER_EMAIL, "wrong", "all", 6);

        assertThat(r).hasSize(7);
        verify(itemRepository, never()).findItemsByConceptIds(any()); // 채우기 생략
    }

    @Test
    void reExamNothing_원본문항_조회하지_않음() {
        stubAnswersAndProbabilities(List.of(prob(5, 0)));
        stubPool(List.of(item(500, 5)));

        itemService.findPersonalItems(USER_TEST_ID, USER_EMAIL, "wrong", "nothing", 10);

        verify(itemRepository, never()).findOriginalItemsByUserTestId(anyLong(), anyBoolean());
    }

    // --- D5: 하위호환 (레거시 Scope A, category=null → findByConceptId 경로) ---

    @Test
    void category_null이면_레거시_depth2이하_혼합_경로() {
        stubAnswersAndProbabilities(List.of(prob(5, 0), prob(3, 1), prob(2, 2), prob(9, 3)));
        when(itemRepository.findByConceptId(5)).thenReturn(item(500, 5));
        when(itemRepository.findByConceptId(3)).thenReturn(item(300, 3));
        when(itemRepository.findByConceptId(2)).thenReturn(item(200, 2));

        List<PersonalItemsResponse> r = itemService.findPersonalItems(USER_TEST_ID, USER_EMAIL, null, null, null);

        assertThat(ids(r)).containsExactly(500L, 300L, 200L);
        verify(itemRepository, never()).findByConceptId(9); // depth3 제외
        verify(itemRepository, never()).findItemsByConceptIds(any()); // 레거시는 채우기 미사용
    }

    @Test
    void 오답이_없으면_빈_리스트() {
        when(answerRepository.findAnswersByUserTestId(USER_TEST_ID)).thenReturn(List.of());

        List<PersonalItemsResponse> r = itemService.findPersonalItems(USER_TEST_ID, USER_EMAIL, "wrong", "nothing", 10);

        assertThat(r).isEmpty();
        verify(itemRepository, never()).findItemsByConceptIds(any());
    }

    // --- IDOR ---

    @Test
    void 타인_학습지_조회시_AccessDenied() {
        when(userTestRepository.existsByUserTestIdAndUserId(USER_TEST_ID, USER_ID)).thenReturn(false);

        assertThatThrownBy(() ->
                itemService.findPersonalItems(USER_TEST_ID, USER_EMAIL, "wrong", "nothing", 10))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }
}
