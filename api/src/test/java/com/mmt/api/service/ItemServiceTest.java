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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 맞춤학습지 조건부 출제(Scope B) 단위 테스트.
 * spec: docs/specs/product/spec-01-personalview-conditional-items-scope-b.md
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
        // IDOR 통과: 본인 소유 학습지 (lenient — 일부 테스트는 IDOR 차단을 먼저 검증)
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

    private Item item(long itemId) {
        Item i = new Item();
        i.setItemId(itemId);
        i.setConceptName("c" + itemId);
        return i;
    }

    private void stubAnswersAndProbabilities(List<Probability> probabilities) {
        when(answerRepository.findAnswersByUserTestId(USER_TEST_ID)).thenReturn(List.of(answer(100L)));
        when(probabilityRepository.findProbability(List.of(100L))).thenReturn(probabilities);
    }

    // --- D1: 맞춤 유형 → depth 필터 ---

    @Test
    void categoryWrong_오답개념_depth0만_출제() {
        stubAnswersAndProbabilities(List.of(prob(5, 0), prob(3, 1), prob(2, 2), prob(9, 3)));
        when(itemRepository.findByConceptId(5)).thenReturn(item(500));

        List<PersonalItemsResponse> result = itemService.findPersonalItems(USER_TEST_ID, USER_EMAIL, "wrong", "nothing", 10);

        assertThat(result).extracting(PersonalItemsResponse::getItemId).containsExactly(500L);
        verify(itemRepository).findByConceptId(5);
        verify(itemRepository, never()).findByConceptId(3);
        verify(itemRepository, never()).findByConceptId(2);
        verify(itemRepository, never()).findByConceptId(9);
    }

    @Test
    void categoryPrerequisite_선수지식_depth1과2만_출제() {
        stubAnswersAndProbabilities(List.of(prob(5, 0), prob(3, 1), prob(2, 2), prob(9, 3)));
        when(itemRepository.findByConceptId(3)).thenReturn(item(300));
        when(itemRepository.findByConceptId(2)).thenReturn(item(200));

        List<PersonalItemsResponse> result = itemService.findPersonalItems(USER_TEST_ID, USER_EMAIL, "prerequisite", "nothing", 10);

        assertThat(result).extracting(PersonalItemsResponse::getItemId).containsExactly(300L, 200L);
        verify(itemRepository, never()).findByConceptId(5); // depth 0 제외
        verify(itemRepository, never()).findByConceptId(9); // depth 3 제외
    }

    // --- D2/D4: 재출제 원본 문항 additive + dedup + 순서 ---

    @Test
    void reExamWrong_원본오답문항_먼저_그다음_신규문항() {
        stubAnswersAndProbabilities(List.of(prob(5, 0)));
        when(itemRepository.findOriginalItemsByUserTestId(USER_TEST_ID, true)).thenReturn(List.of(item(700)));
        when(itemRepository.findByConceptId(5)).thenReturn(item(500));

        List<PersonalItemsResponse> result = itemService.findPersonalItems(USER_TEST_ID, USER_EMAIL, "wrong", "wrong", 10);

        // 원본(700) 먼저 → 신규(500), 번호 1,2
        assertThat(result).extracting(PersonalItemsResponse::getItemId).containsExactly(700L, 500L);
        assertThat(result).extracting(PersonalItemsResponse::getTestItemNumber).containsExactly(1, 2);
    }

    @Test
    void reExamAll_원본_응시문항_전체_조회() {
        stubAnswersAndProbabilities(List.of(prob(5, 0)));
        when(itemRepository.findOriginalItemsByUserTestId(USER_TEST_ID, false)).thenReturn(List.of(item(700), item(701)));
        when(itemRepository.findByConceptId(5)).thenReturn(item(500));

        List<PersonalItemsResponse> result = itemService.findPersonalItems(USER_TEST_ID, USER_EMAIL, "wrong", "all", 10);

        assertThat(result).extracting(PersonalItemsResponse::getItemId).containsExactly(700L, 701L, 500L);
        verify(itemRepository).findOriginalItemsByUserTestId(USER_TEST_ID, false); // onlyWrong=false
    }

    @Test
    void 원본문항과_신규문항이_같은_item이면_dedup_원본우선() {
        stubAnswersAndProbabilities(List.of(prob(5, 0)));
        when(itemRepository.findOriginalItemsByUserTestId(USER_TEST_ID, true)).thenReturn(List.of(item(500)));
        when(itemRepository.findByConceptId(5)).thenReturn(item(500)); // 동일 item_id

        List<PersonalItemsResponse> result = itemService.findPersonalItems(USER_TEST_ID, USER_EMAIL, "wrong", "wrong", 10);

        assertThat(result).extracting(PersonalItemsResponse::getItemId).containsExactly(500L); // 1회만
    }

    @Test
    void reExamNothing_원본문항_조회하지_않음() {
        stubAnswersAndProbabilities(List.of(prob(5, 0)));
        when(itemRepository.findByConceptId(5)).thenReturn(item(500));

        itemService.findPersonalItems(USER_TEST_ID, USER_EMAIL, "wrong", "nothing", 10);

        verify(itemRepository, never()).findOriginalItemsByUserTestId(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyBoolean());
    }

    // --- D3: count 상한 + clamp ---

    @Test
    void count_신규문항_상한_적용() {
        stubAnswersAndProbabilities(List.of(prob(1, 0), prob(2, 0), prob(3, 0), prob(4, 0), prob(5, 0), prob(6, 0), prob(7, 0), prob(8, 0)));
        // distinct concept 8개 중 count=6 → 앞 6개만
        for (int c = 1; c <= 6; c++) when(itemRepository.findByConceptId(c)).thenReturn(item(c * 100));

        List<PersonalItemsResponse> result = itemService.findPersonalItems(USER_TEST_ID, USER_EMAIL, "wrong", "nothing", 6);

        assertThat(result).hasSize(6);
        verify(itemRepository, never()).findByConceptId(7);
        verify(itemRepository, never()).findByConceptId(8);
    }

    @Test
    void count_최소6미만이면_6으로_clamp() {
        stubAnswersAndProbabilities(List.of(prob(1, 0), prob(2, 0), prob(3, 0), prob(4, 0)));
        for (int c = 1; c <= 4; c++) when(itemRepository.findByConceptId(c)).thenReturn(item(c * 100));

        // count=2 요청이지만 clamp 하한 6 → 가용한 4개 전부 출제 (2개로 잘리지 않음)
        List<PersonalItemsResponse> result = itemService.findPersonalItems(USER_TEST_ID, USER_EMAIL, "wrong", "nothing", 2);

        assertThat(result).hasSize(4);
    }

    @Test
    void distinct_중복_concept는_한번만() {
        stubAnswersAndProbabilities(List.of(prob(5, 0), prob(5, 0), prob(5, 0)));
        when(itemRepository.findByConceptId(5)).thenReturn(item(500));

        List<PersonalItemsResponse> result = itemService.findPersonalItems(USER_TEST_ID, USER_EMAIL, "wrong", "nothing", 10);

        assertThat(result).hasSize(1);
        verify(itemRepository).findByConceptId(5);
    }

    // --- D5: 하위호환 (레거시 Scope A) ---

    @Test
    void category_null이면_레거시_depth2이하_혼합_경로() {
        stubAnswersAndProbabilities(List.of(prob(5, 0), prob(3, 1), prob(2, 2), prob(9, 3)));
        when(itemRepository.findByConceptId(5)).thenReturn(item(500));
        when(itemRepository.findByConceptId(3)).thenReturn(item(300));
        when(itemRepository.findByConceptId(2)).thenReturn(item(200));

        List<PersonalItemsResponse> result = itemService.findPersonalItems(USER_TEST_ID, USER_EMAIL, null, null, null);

        // depth 0·1·2 모두 포함, depth 3 제외, 재출제 없음
        assertThat(result).extracting(PersonalItemsResponse::getItemId).containsExactly(500L, 300L, 200L);
        verify(itemRepository, never()).findByConceptId(9);
        verify(itemRepository, never()).findOriginalItemsByUserTestId(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyBoolean());
    }

    @Test
    void 오답이_없으면_빈_리스트() {
        when(answerRepository.findAnswersByUserTestId(USER_TEST_ID)).thenReturn(List.of());

        List<PersonalItemsResponse> result = itemService.findPersonalItems(USER_TEST_ID, USER_EMAIL, "wrong", "nothing", 10);

        assertThat(result).isEmpty();
        verify(itemRepository, never()).findByConceptId(anyInt());
    }

    // --- IDOR ---

    @Test
    void 타인_학습지_조회시_AccessDenied() {
        when(userTestRepository.existsByUserTestIdAndUserId(USER_TEST_ID, USER_ID)).thenReturn(false);

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                        itemService.findPersonalItems(USER_TEST_ID, USER_EMAIL, "wrong", "nothing", 10))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }
}
