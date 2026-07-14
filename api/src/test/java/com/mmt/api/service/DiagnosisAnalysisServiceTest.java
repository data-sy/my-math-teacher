package com.mmt.api.service;

import com.mmt.api.domain.diagnosis.SelfReportAnswer;
import com.mmt.api.dto.diagnosis.AnsweredConcept;
import com.mmt.api.repository.concept.ConceptDepth;
import com.mmt.api.repository.concept.JdbcTemplateConceptRepository;
import com.mmt.api.repository.diagnosis.SelfReportAnswerRepository;
import com.mmt.api.repository.probability.DiagnosisProbabilityRow;
import com.mmt.api.repository.probability.ProbabilityRepository;
import com.mmt.api.repository.userTest.UserTestRepository;
import com.mmt.api.repository.users.UsersRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * spec-01 §4.4 DKT 매핑 + §8 순서 동치·가드 검증.
 *
 * 합성 시드: 개념 c → skill_id (c*100), 선수: 30→[20], 20→[10] (depth 확장용).
 */
class DiagnosisAnalysisServiceTest {

    private JdbcTemplateConceptRepository conceptRepo;
    private SelfReportAnswerRepository sraRepo;
    private UserTestRepository userTestRepo;
    private ProbabilityRepository probRepo;
    private UsersRepository usersRepo;
    private DiagnosisDktClient dktClient;
    private DiagnosisAnalysisService service;

    /** IDENTITY 채번 시뮬레이션: 저장 순서대로 id 부여, ASC 재조회 = 저장 순서. */
    private final List<SelfReportAnswer> savedAnswers = new ArrayList<>();
    private final AtomicLong idSeq = new AtomicLong(0);

    @BeforeEach
    void setUp() {
        conceptRepo = mock(JdbcTemplateConceptRepository.class);
        sraRepo = mock(SelfReportAnswerRepository.class);
        userTestRepo = mock(UserTestRepository.class);
        probRepo = mock(ProbabilityRepository.class);
        usersRepo = mock(UsersRepository.class);
        dktClient = mock(DiagnosisDktClient.class);
        service = new DiagnosisAnalysisService(new DiagnosisService(conceptRepo), conceptRepo,
                sraRepo, userTestRepo, probRepo, usersRepo, dktClient);

        when(usersRepo.findUserIdByUserEmail(anyString())).thenReturn(Optional.of(7L));
        when(userTestRepo.saveDiagnosisSession(anyLong())).thenReturn(42L);
        savedAnswers.clear();
        idSeq.set(0);
        when(sraRepo.save(any())).thenAnswer(inv -> {
            SelfReportAnswer a = inv.getArgument(0);
            a.setSelfReportAnswerId(idSeq.incrementAndGet());
            savedAnswers.add(a);
            return a;
        });
        when(sraRepo.findByUserTestIdOrderBySelfReportAnswerIdAsc(42L))
                .thenAnswer(inv -> new ArrayList<>(savedAnswers));

        // 그래프: 30→20→10 사슬, 나머지는 선수 없음. CTE 앵커 = 자기 자신 depth 0.
        when(conceptRepo.findPrerequisitesWithDepth(anyInt(), eq(3))).thenAnswer(inv -> {
            int id = inv.getArgument(0);
            if (id == 30) return List.of(new ConceptDepth(30, 0), new ConceptDepth(20, 1), new ConceptDepth(10, 2));
            if (id == 20) return List.of(new ConceptDepth(20, 0), new ConceptDepth(10, 1));
            return List.of(new ConceptDepth(id, 0));
        });
        // 존재 검증: 기본은 전 개념 존재로 취급
        when(conceptRepo.findExistingConceptIds(anyCollection())).thenAnswer(inv ->
                new java.util.HashSet<Integer>(inv.getArgument(0)));
        // skill_id = conceptId*100 (전 개념 매핑)
        when(conceptRepo.findSkillIdsByConceptIds(anyCollection())).thenAnswer(inv -> {
            java.util.Collection<Integer> ids = inv.getArgument(0);
            java.util.Map<Integer, Integer> m = new java.util.HashMap<>();
            ids.forEach(i -> m.put(i, i)); // skillId = conceptId (범위 테스트 용이)
            return m;
        });
        // 예측 벡터: index i → 0.01*i (skillId-1 인덱싱 검증용), 길이 100
        double[] vec = new double[100];
        for (int i = 0; i < vec.length; i++) vec[i] = i;
        when(dktClient.predict(any())).thenReturn(vec);
    }

    @Test
    @DisplayName("§8 순서 동치: preview 시퀀스 == 귀속 재구성 시퀀스 (순서 포함, 셔플 property)")
    void previewAndSubmitProduceIdenticalSequence() {
        List<AnsweredConcept> base = List.of(
                new AnsweredConcept(30, false),
                new AnsweredConcept(31, true),
                new AnsweredConcept(20, false),
                new AnsweredConcept(35, true));
        for (int seed = 0; seed < 5; seed++) {
            List<AnsweredConcept> answered = new ArrayList<>(base);
            Collections.shuffle(answered, new java.util.Random(seed));
            savedAnswers.clear();
            idSeq.set(0);

            ArgumentCaptor<List<int[]>> captor = ArgumentCaptor.forClass(List.class);
            org.mockito.Mockito.clearInvocations(dktClient);

            service.preview(answered);
            service.submit("s@t.kr", answered);

            verify(dktClient, org.mockito.Mockito.times(2)).predict(captor.capture());
            List<List<int[]>> sequences = captor.getAllValues();
            assertThat(toIntLists(sequences.get(1)))
                    .as("seed=" + seed + " 귀속 시퀀스가 preview 와 순서 포함 동일해야 함")
                    .isEqualTo(toIntLists(sequences.get(0)));
        }
    }

    @Test
    @DisplayName("시퀀스 = 입력 순서 그대로 [skillId, 안다=1/모른다=0] ×10")
    void sequenceBuildRules() {
        List<int[]> seq = DiagnosisAnalysisService.buildSequence(
                List.of(new AnsweredConcept(30, false), new AnsweredConcept(31, true)),
                Map.of(30, 300, 31, 310));
        assertThat(seq).hasSize(20);
        assertThat(seq.get(0)).containsExactly(300, 0);
        assertThat(seq.get(9)).containsExactly(300, 0);
        assertThat(seq.get(10)).containsExactly(310, 1);
        assertThat(seq.get(19)).containsExactly(310, 1);
    }

    @Test
    @DisplayName("미존재 conceptId → preview·귀속 동일하게 400 (FK 500 비대칭 방지)")
    void missingConceptIdRejectedSymmetrically() {
        when(conceptRepo.findExistingConceptIds(anyCollection())).thenAnswer(inv -> {
            java.util.Set<Integer> s = new java.util.HashSet<>(inv.getArgument(0));
            s.remove(9999);
            return s;
        });
        List<AnsweredConcept> answered = List.of(new AnsweredConcept(9999, false));
        for (Runnable call : new Runnable[]{
                () -> service.preview(answered),
                () -> service.submit("s@t.kr", answered)}) {
            org.assertj.core.api.Assertions.assertThatThrownBy(call::run)
                    .isInstanceOf(com.mmt.api.exception.DiagnosisException.class)
                    .hasMessageContaining("9999");
        }
    }

    @Test
    @DisplayName("R2 엣지: 전부 '알아요' → DKT 호출 생략, 약점 없음 정상 결과")
    void allKnownSkipsDkt() {
        DiagnosisAnalysisService.DiagnosisComputation c = service.preview(List.of(
                new AnsweredConcept(30, true), new AnsweredConcept(31, true)));
        verify(dktClient, never()).predict(any());
        assertThat(c.unknownConceptIds()).isEmpty();
        assertThat(c.totalAsked()).isEqualTo(2);
        assertThat(c.servingOk()).isTrue();
    }

    @Test
    @DisplayName("가드 1: 서빙 null → fail-soft (percent 결측, servingOk=false, 확장 목록은 성립)")
    void servingNullFailsSoft() {
        when(dktClient.predict(any())).thenReturn(null);
        DiagnosisAnalysisService.DiagnosisComputation c = service.preview(List.of(
                new AnsweredConcept(30, false)));
        assertThat(c.servingOk()).isFalse();
        assertThat(c.percentByConcept()).isEmpty();
        assertThat(c.minDepthByConcept()).containsKeys(30, 20, 10); // 몰라요 목록 기반 결과는 성립
    }

    @Test
    @DisplayName("가드 2·3: skill_id 미매핑 개념은 시퀀스·시급도에서 제외, 범위 초과는 결측")
    void unmappedAndOutOfRangeGuards() {
        when(conceptRepo.findSkillIdsByConceptIds(anyCollection())).thenAnswer(inv -> {
            java.util.Collection<Integer> ids = inv.getArgument(0);
            java.util.Map<Integer, Integer> m = new java.util.HashMap<>();
            ids.forEach(i -> { if (i != 20) m.put(i, i == 10 ? 5000 : i); }); // 20=미매핑, 10=범위초과
            return m;
        });
        DiagnosisAnalysisService.DiagnosisComputation c = service.preview(List.of(
                new AnsweredConcept(30, false)));
        assertThat(c.percentByConcept()).containsKey(30);
        assertThat(c.percentByConcept()).doesNotContainKeys(20, 10);
    }

    @Test
    @DisplayName("취약 확장 dedup: 겹치는 선수는 MIN(depth) 채택")
    void expansionDedupByMinDepth() {
        // 30 몰라요(10 은 depth2) + 20 몰라요(10 은 depth1) → 10 의 depth = 1
        DiagnosisAnalysisService.DiagnosisComputation c = service.preview(List.of(
                new AnsweredConcept(30, false), new AnsweredConcept(20, false)));
        assertThat(c.minDepthByConcept().get(10)).isEqualTo(1);
        assertThat(c.minDepthByConcept().get(20)).isEqualTo(0); // 몰라요 자신 = depth0 이 depth1 을 이김
    }

    @Test
    @DisplayName("귀속: probabilities 는 user_test_id 스코프로 저장 (개념당 1행, percent 결측 허용)")
    void submitPersistsUserTestScopedRows() {
        service.submit("s@t.kr", List.of(new AnsweredConcept(30, false)));
        ArgumentCaptor<List<DiagnosisProbabilityRow>> rows = ArgumentCaptor.forClass(List.class);
        verify(probRepo).saveForUserTest(eq(42L), rows.capture());
        assertThat(rows.getValue()).extracting(DiagnosisProbabilityRow::conceptId)
                .containsExactlyInAnyOrder(30, 20, 10);
    }

    @Test
    @DisplayName("소유권 위반 = DiagnosisException 403 (401 마스킹 우회 — residual ④), 인증 부재 = 401 유지")
    void ownershipViolationIsForbidden() {
        when(userTestRepo.existsByUserTestIdAndUserId(99L, 7L)).thenReturn(false);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.assertOwnership(99L, "s@t.kr"))
                .isInstanceOf(com.mmt.api.exception.DiagnosisException.class)
                .satisfies(e -> assertThat(((com.mmt.api.exception.DiagnosisException) e).getStatus())
                        .isEqualTo(org.springframework.http.HttpStatus.FORBIDDEN));

        when(usersRepo.findUserIdByUserEmail("ghost@t.kr")).thenReturn(Optional.empty());
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.assertOwnership(99L, "ghost@t.kr"))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }

    private static List<List<Integer>> toIntLists(List<int[]> seq) {
        return seq.stream().map(p -> List.of(p[0], p[1])).collect(java.util.stream.Collectors.toList());
    }
}
