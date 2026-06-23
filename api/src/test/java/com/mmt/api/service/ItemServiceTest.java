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
import java.util.stream.Collectors;

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
 * D1 개정(2026-06-23): 맞춤유형 = 우선순위 tier(위주=primary 먼저, 부족 시 secondary spill).
 * D3 개정: count = 목표 총 문항수, tier 순서로 round-robin 채움.
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

    /** 오답 answer → probabilities 스텁 (tier 도출용) */
    private void stubAnswersAndProbabilities(List<Probability> probabilities) {
        when(answerRepository.findAnswersByUserTestId(USER_TEST_ID)).thenReturn(List.of(answer(100L)));
        when(probabilityRepository.findProbability(List.of(100L))).thenReturn(probabilities);
    }

    /** 범위 채우기 풀: 실제 쿼리처럼 요청한 conceptId 로 마스터 풀을 필터해 반환 */
    private void stubPoolMaster(List<Item> master) {
        when(itemRepository.findItemsByConceptIds(any())).thenAnswer(inv -> {
            List<Integer> cids = inv.getArgument(0);
            return master.stream().filter(it -> cids.contains(it.getConceptId())).collect(Collectors.toList());
        });
    }

    private List<Long> ids(List<PersonalItemsResponse> r) {
        return r.stream().map(PersonalItemsResponse::getItemId).toList();
    }

    // --- D1: 우선순위 tier + spill ---

    @Test
    void categoryWrong_primary_depth0_먼저_부족하면_선수지식_spill() {
        stubAnswersAndProbabilities(List.of(prob(5, 0), prob(3, 1), prob(2, 2)));
        // primary={5}(1문항), 부족 → secondary={3,2}로 spill
        stubPoolMaster(List.of(item(500, 5), item(300, 3), item(200, 2)));

        List<PersonalItemsResponse> r = itemService.findPersonalItems(USER_TEST_ID, USER_EMAIL, "wrong", "nothing", 6);

        assertThat(ids(r)).containsExactly(500L, 300L, 200L); // 오답(500) 먼저, 그다음 선수지식 spill
    }

    @Test
    void categoryWrong_primary로_count충족시_secondary_미사용() {
        stubAnswersAndProbabilities(List.of(prob(5, 0), prob(3, 1)));
        // primary={5} 7문항으로 count=6 충족 → secondary={3} 미사용
        stubPoolMaster(List.of(item(501, 5), item(502, 5), item(503, 5), item(504, 5),
                               item(505, 5), item(506, 5), item(507, 5), item(300, 3)));

        List<PersonalItemsResponse> r = itemService.findPersonalItems(USER_TEST_ID, USER_EMAIL, "wrong", "nothing", 6);

        assertThat(r).hasSize(6);
        assertThat(ids(r)).doesNotContain(300L); // secondary 안 건드림
    }

    @Test
    void categoryPrerequisite_primary_depth1_2_먼저_부족하면_오답_spill() {
        stubAnswersAndProbabilities(List.of(prob(5, 0), prob(3, 1), prob(2, 2)));
        // primary={3,2}, 부족 → secondary={5}
        stubPoolMaster(List.of(item(300, 3), item(200, 2), item(500, 5)));

        List<PersonalItemsResponse> r = itemService.findPersonalItems(USER_TEST_ID, USER_EMAIL, "prerequisite", "nothing", 6);

        assertThat(ids(r)).containsExactly(300L, 200L, 500L); // 선수지식 먼저, 오답 spill
    }

    @Test
    void tier내_round_robin_count에서_중단() {
        stubAnswersAndProbabilities(List.of(prob(5, 0), prob(6, 0)));
        // primary={5,6} 각 4문항. count=6 → round-robin 3+3, 504·604 미사용
        stubPoolMaster(List.of(item(501, 5), item(502, 5), item(503, 5), item(504, 5),
                               item(601, 6), item(602, 6), item(603, 6), item(604, 6)));

        List<PersonalItemsResponse> r = itemService.findPersonalItems(USER_TEST_ID, USER_EMAIL, "wrong", "nothing", 6);

        assertThat(ids(r)).containsExactly(501L, 601L, 502L, 602L, 503L, 603L);
    }

    @Test
    void count_전범위_소진되면_미달_허용() {
        stubAnswersAndProbabilities(List.of(prob(5, 0)));
        // primary={5} 1문항, secondary 없음 → count=10 이어도 1
        stubPoolMaster(List.of(item(500, 5)));

        List<PersonalItemsResponse> r = itemService.findPersonalItems(USER_TEST_ID, USER_EMAIL, "wrong", "nothing", 10);

        assertThat(ids(r)).containsExactly(500L);
    }

    @Test
    void count_최소6미만이면_6으로_clamp() {
        stubAnswersAndProbabilities(List.of(prob(5, 0)));
        stubPoolMaster(List.of(item(501, 5), item(502, 5), item(503, 5)));

        List<PersonalItemsResponse> r = itemService.findPersonalItems(USER_TEST_ID, USER_EMAIL, "wrong", "nothing", 2);

        assertThat(r).hasSize(3); // 2로 안 잘림(clamp 6) — 범위 소진까지
    }

    @Test
    void distinct_중복_concept는_한_개념으로() {
        stubAnswersAndProbabilities(List.of(prob(5, 0), prob(5, 0), prob(5, 0)));
        stubPoolMaster(List.of(item(500, 5)));

        List<PersonalItemsResponse> r = itemService.findPersonalItems(USER_TEST_ID, USER_EMAIL, "wrong", "nothing", 10);

        assertThat(ids(r)).containsExactly(500L);
    }

    // --- D2/D4: 재출제 원본 additive + dedup + 순서 + count 산입 ---

    @Test
    void reExamWrong_원본먼저_그다음_tier채움() {
        stubAnswersAndProbabilities(List.of(prob(3, 1), prob(2, 2)));
        when(itemRepository.findOriginalItemsByUserTestId(USER_TEST_ID, true)).thenReturn(List.of(item(700, 99), item(701, 99)));
        stubPoolMaster(List.of(item(300, 3), item(200, 2)));

        List<PersonalItemsResponse> r = itemService.findPersonalItems(USER_TEST_ID, USER_EMAIL, "prerequisite", "wrong", 10);

        assertThat(ids(r)).containsExactly(700L, 701L, 300L, 200L);
        assertThat(r).extracting(PersonalItemsResponse::getTestItemNumber).containsExactly(1, 2, 3, 4);
    }

    @Test
    void reExamAll_원본_응시문항_전체_조회() {
        stubAnswersAndProbabilities(List.of(prob(3, 1)));
        when(itemRepository.findOriginalItemsByUserTestId(USER_TEST_ID, false)).thenReturn(List.of(item(700, 99), item(701, 99)));
        stubPoolMaster(List.of(item(300, 3)));

        List<PersonalItemsResponse> r = itemService.findPersonalItems(USER_TEST_ID, USER_EMAIL, "prerequisite", "all", 10);

        assertThat(ids(r)).containsExactly(700L, 701L, 300L);
        verify(itemRepository).findOriginalItemsByUserTestId(USER_TEST_ID, false);
    }

    @Test
    void 원본과_범위문항이_같은_item이면_dedup_원본우선() {
        stubAnswersAndProbabilities(List.of(prob(5, 0)));
        when(itemRepository.findOriginalItemsByUserTestId(USER_TEST_ID, true)).thenReturn(List.of(item(500, 5)));
        stubPoolMaster(List.of(item(500, 5), item(501, 5))); // 500 중복

        List<PersonalItemsResponse> r = itemService.findPersonalItems(USER_TEST_ID, USER_EMAIL, "wrong", "wrong", 10);

        assertThat(ids(r)).containsExactly(500L, 501L); // 500 1회만
    }

    @Test
    void count_총목표에_원본_산입() {
        stubAnswersAndProbabilities(List.of(prob(5, 0)));
        when(itemRepository.findOriginalItemsByUserTestId(USER_TEST_ID, true)).thenReturn(List.of(item(700, 99), item(701, 99), item(702, 99)));
        stubPoolMaster(List.of(item(501, 5), item(502, 5), item(503, 5), item(504, 5)));

        // count=6, 원본 3 → 신규 3개만 채워 총 6
        List<PersonalItemsResponse> r = itemService.findPersonalItems(USER_TEST_ID, USER_EMAIL, "wrong", "wrong", 6);

        assertThat(r).hasSize(6);
        assertThat(ids(r).subList(0, 3)).containsExactly(700L, 701L, 702L);
    }

    @Test
    void 원본이_count_이상이면_원본전부_채우기생략() {
        when(itemRepository.findOriginalItemsByUserTestId(USER_TEST_ID, false))
                .thenReturn(List.of(item(701, 9), item(702, 9), item(703, 9), item(704, 9), item(705, 9), item(706, 9), item(707, 9)));

        List<PersonalItemsResponse> r = itemService.findPersonalItems(USER_TEST_ID, USER_EMAIL, "wrong", "all", 6);

        assertThat(r).hasSize(7);
        verify(itemRepository, never()).findItemsByConceptIds(any());
    }

    @Test
    void reExamNothing_원본문항_조회하지_않음() {
        stubAnswersAndProbabilities(List.of(prob(5, 0)));
        stubPoolMaster(List.of(item(500, 5)));

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
        verify(itemRepository, never()).findByConceptId(9);
        verify(itemRepository, never()).findItemsByConceptIds(any());
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
