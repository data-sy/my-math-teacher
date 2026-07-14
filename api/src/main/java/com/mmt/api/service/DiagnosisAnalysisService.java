package com.mmt.api.service;

import com.mmt.api.domain.diagnosis.SelfReportAnswer;
import com.mmt.api.dto.diagnosis.AnsweredConcept;
import com.mmt.api.repository.concept.JdbcTemplateConceptRepository;
import com.mmt.api.repository.diagnosis.SelfReportAnswerRepository;
import com.mmt.api.repository.probability.DiagnosisProbabilityRow;
import com.mmt.api.repository.probability.ProbabilityRepository;
import com.mmt.api.repository.userTest.UserTestRepository;
import com.mmt.api.repository.users.UsersRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * M7 spec-01 §4.4 — self-report→DKT 매핑 엔진 (D3-R, ADR-0010).
 *
 * 결정론 계약: preview(요청 answered[] 순서) 와 귀속(self_report_answer_id ASC 재조회)이
 * 동일 시퀀스 → 동일 결과. 귀속 계산은 요청이 아니라 **저장 후 재조회한 순서**로 수행해
 * 두 순서의 동치가 코드 경로에서 실제로 검증되게 한다 (spec-01 §8 순서 동치 property).
 *
 * 구 경로 무접촉 (S2=C): 이 서비스와 하위 의존은 answers·tests_items·items·
 * findAIInput/findBefore 를 일절 참조하지 않는다 — DiagnosisOldPathNoTouchTest 가 감시.
 */
@Service
public class DiagnosisAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(DiagnosisAnalysisService.class);

    /** 구 경로 ×10 증폭 승계 (spec-01 §4.4-2: 순서 확정 후 반복, 저장은 개념당 1행). */
    static final int AMPLIFICATION = 10;
    /** 취약 확장 깊이 — 구 경로 findPrerequisitesAsDepthMap(cId, 3) 승계. */
    static final int EXPANSION_DEPTH = 3;
    /** blockedDescendants 역방향 CTE depth 상한 (S6 잠정 채택 — 정방향 확장과 대칭). */
    static final int BLOCKED_DEPTH = 3;

    private final DiagnosisService diagnosisService;
    private final JdbcTemplateConceptRepository conceptRepository;
    private final SelfReportAnswerRepository selfReportAnswerRepository;
    private final UserTestRepository userTestRepository;
    private final ProbabilityRepository probabilityRepository;
    private final UsersRepository usersRepository;
    private final DiagnosisDktClient dktClient;

    public DiagnosisAnalysisService(DiagnosisService diagnosisService,
                                    JdbcTemplateConceptRepository conceptRepository,
                                    SelfReportAnswerRepository selfReportAnswerRepository,
                                    UserTestRepository userTestRepository,
                                    ProbabilityRepository probabilityRepository,
                                    UsersRepository usersRepository,
                                    DiagnosisDktClient dktClient) {
        this.diagnosisService = diagnosisService;
        this.conceptRepository = conceptRepository;
        this.selfReportAnswerRepository = selfReportAnswerRepository;
        this.userTestRepository = userTestRepository;
        this.probabilityRepository = probabilityRepository;
        this.usersRepository = usersRepository;
        this.dktClient = dktClient;
    }

    /**
     * 분석 결과 내부 모델 — 결과 계약(§4.4)·학습 큐(§4.6)가 소비.
     *
     * @param minDepthByConcept "몰라요" 개념(depth 0) + 선수 확장(depth 1~3), 개념별 MIN(depth) dedup.
     *                          순회 순서 = 몰라요 입력 순서 → CTE depth 순 (결정론).
     * @param percentByConcept  DKT 확률 (키 부재 = fail-soft 결측: 서빙 실패·미매핑·범위 초과)
     */
    public record DiagnosisComputation(
            int totalAsked,
            List<Integer> unknownConceptIds,
            Map<Integer, Integer> minDepthByConcept,
            Map<Integer, Double> percentByConcept,
            boolean servingOk) {

        public static DiagnosisComputation noWeakness(int totalAsked) {
            return new DiagnosisComputation(totalAsked, List.of(), Map.of(), Map.of(), true);
        }
    }

    /** 익명 preview — 무영속 (S1-A). */
    @Transactional(readOnly = true)
    public DiagnosisComputation preview(List<AnsweredConcept> answered) {
        List<AnsweredConcept> answers = answered == null ? List.of() : answered;
        validateAnswers(answers); // 중복·미존재 400 — 귀속과 대칭
        return compute(answers);
    }

    /**
     * preview·귀속 공통 사전 검증 — 결정론 계약을 에러 동작까지 확장:
     * 중복 conceptId(귀속만 UNIQUE 500 방지) + 미존재 conceptId(귀속만 FK 500,
     * preview 는 조용히 무시되는 비대칭 방지 — 2026-07-13 E2E 스모크 발견).
     */
    private void validateAnswers(List<AnsweredConcept> answers) {
        Map<Integer, Boolean> answeredMap = diagnosisService.toValidatedAnsweredMap(answers);
        if (answeredMap.isEmpty()) {
            return;
        }
        Set<Integer> existing = conceptRepository.findExistingConceptIds(answeredMap.keySet());
        List<Integer> missing = answeredMap.keySet().stream()
                .filter(id -> !existing.contains(id))
                .collect(Collectors.toList());
        if (!missing.isEmpty()) {
            throw new com.mmt.api.exception.DiagnosisException(
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    "존재하지 않는 conceptId: " + missing);
        }
    }

    /** 익명 preview 결과 계약 (§4.4) — 무영속. */
    @Transactional(readOnly = true)
    public com.mmt.api.dto.diagnosis.DiagnosisResultResponse previewResult(List<AnsweredConcept> answered) {
        return assemble(preview(answered));
    }

    /** 귀속 저장 + 결과 계약 — preview 와 동일 shape (결정론 계약). */
    @Transactional
    public SubmitOutcome submitResult(String userEmail, List<AnsweredConcept> answered) {
        Long userTestId = submit(userEmail, answered);
        return new SubmitOutcome(userTestId, getResultInternal(userTestId));
    }

    /** 귀속 결과 재조회 (GET /diagnosis/{userTestId}) — 소유권 검사 포함. */
    @Transactional(readOnly = true)
    public com.mmt.api.dto.diagnosis.DiagnosisResultResponse getResult(Long userTestId, String userEmail) {
        assertOwnership(userTestId, userEmail);
        return getResultInternal(userTestId);
    }

    public record SubmitOutcome(Long userTestId, com.mmt.api.dto.diagnosis.DiagnosisResultResponse result) {}

    /**
     * 영속분에서 computation 재구성 — probabilities.user_test_id 직조회(§6 실측 ③) +
     * self_report_answers 로 headline. percent 전부 NULL(귀속 시점 서빙 실패)이면
     * urgencyAvailable=false 로 degrade 유지.
     */
    private com.mmt.api.dto.diagnosis.DiagnosisResultResponse getResultInternal(Long userTestId) {
        List<SelfReportAnswer> saved = selfReportAnswerRepository
                .findByUserTestIdOrderBySelfReportAnswerIdAsc(userTestId);
        List<Integer> unknown = saved.stream()
                .filter(s -> !s.getKnown())
                .map(SelfReportAnswer::getConceptId)
                .collect(Collectors.toList());

        Map<Integer, Integer> minDepthByConcept = new LinkedHashMap<>();
        Map<Integer, Double> percentByConcept = new LinkedHashMap<>();
        probabilityRepository.findDiagnosisRowsByUserTestId(userTestId).forEach(row -> {
            minDepthByConcept.put(row.conceptId(), row.toConceptDepth());
            if (row.probabilityPercent() != null) {
                percentByConcept.put(row.conceptId(), row.probabilityPercent());
            }
        });
        boolean servingOk = minDepthByConcept.isEmpty() || !percentByConcept.isEmpty();
        return assemble(new DiagnosisComputation(saved.size(), unknown,
                minDepthByConcept, percentByConcept, servingOk));
    }

    /** 카드 메타·blockedDescendants(S6 역방향 CTE depth 3) 조회 후 결과 조립. */
    private com.mmt.api.dto.diagnosis.DiagnosisResultResponse assemble(DiagnosisComputation computation) {
        Map<Integer, com.mmt.api.repository.concept.ConceptCardMeta> metaByConcept =
                conceptRepository.findConceptCardMetas(computation.minDepthByConcept().keySet()).stream()
                        .collect(Collectors.toMap(
                                com.mmt.api.repository.concept.ConceptCardMeta::conceptId, m -> m));
        Map<Integer, Integer> blockedByConcept = new LinkedHashMap<>();
        for (int conceptId : computation.minDepthByConcept().keySet()) {
            blockedByConcept.put(conceptId,
                    conceptRepository.countBlockedDescendants(conceptId, BLOCKED_DEPTH));
        }
        return DiagnosisResultAssembler.assemble(computation, metaByConcept, blockedByConcept);
    }

    /**
     * 귀속 저장 (§4.1 게이트): users_tests + self_report_answers + probabilities.
     * 반환 = 생성된 userTestId. 결과 계산은 저장 후 ASC 재조회 순서로 수행.
     */
    @Transactional
    public Long submit(String userEmail, List<AnsweredConcept> answered) {
        Long userId = usersRepository.findUserIdByUserEmail(userEmail)
                .orElseThrow(() -> new AccessDeniedException("인증 정보를 확인할 수 없습니다."));
        List<AnsweredConcept> answers = answered == null ? List.of() : answered;
        validateAnswers(answers); // 중복·미존재 400 — preview 와 대칭 (UNIQUE·FK 500 방지)

        Long userTestId = userTestRepository.saveDiagnosisSession(userId);
        for (AnsweredConcept a : answers) {
            // saveAll 대신 순차 save: IDENTITY 채번이 입력 순서를 보존함을 명시적으로 고정
            selfReportAnswerRepository.save(new SelfReportAnswer(userTestId, a.getConceptId(), a.isKnown()));
        }

        // 결정론의 실검증 경로: 요청 배열이 아니라 저장분 ASC 재조회로 시퀀스 재구성 (§4.4-2)
        List<AnsweredConcept> reread = selfReportAnswerRepository
                .findByUserTestIdOrderBySelfReportAnswerIdAsc(userTestId).stream()
                .map(s -> new AnsweredConcept(s.getConceptId(), s.getKnown()))
                .collect(Collectors.toList());
        DiagnosisComputation computation = compute(reread);

        List<DiagnosisProbabilityRow> rows = computation.minDepthByConcept().entrySet().stream()
                .map(e -> new DiagnosisProbabilityRow(e.getKey(), e.getValue(),
                        computation.percentByConcept().get(e.getKey())))
                .collect(Collectors.toList());
        if (!rows.isEmpty()) {
            probabilityRepository.saveForUserTest(userTestId, rows);
        }
        return userTestId;
    }

    /**
     * 소유권 검사 (ItemService.findPersonalItems IDOR 선례 승계).
     * 위반은 DiagnosisException(403) — AccessDeniedException 은 /error 디스패치에서
     * 401 로 마스킹된다(2026-07-13 E2E 실측, residual ④). 인증 정보 부재는 401 유지.
     * 구 경로의 동일 마스킹은 무접촉 보존 — 신규 타입으로만 우회한다.
     */
    @Transactional(readOnly = true)
    public void assertOwnership(Long userTestId, String userEmail) {
        Long userId = usersRepository.findUserIdByUserEmail(userEmail)
                .orElseThrow(() -> new AccessDeniedException("인증 정보를 확인할 수 없습니다."));
        if (!userTestRepository.existsByUserTestIdAndUserId(userTestId, userId)) {
            throw new com.mmt.api.exception.DiagnosisException(
                    org.springframework.http.HttpStatus.FORBIDDEN, "본인의 진단 기록만 조회할 수 있습니다.");
        }
    }

    /**
     * 공통 계산 (preview·귀속 동일 경로 — 결정론의 구조적 보장).
     * 입력 answers 의 배열 순서가 곧 DKT 시퀀스 순서다.
     */
    DiagnosisComputation compute(List<AnsweredConcept> answers) {
        List<Integer> unknown = answers.stream()
                .filter(a -> !a.isKnown())
                .map(AnsweredConcept::getConceptId)
                .collect(Collectors.toList());
        if (unknown.isEmpty()) {
            // R2 엣지: "몰라요" 0개 → DKT 호출 생략, 약점 없음 = 정상 결과 (§4.4)
            return DiagnosisComputation.noWeakness(answers.size());
        }

        // 취약 확장: depth0("몰라요") + 선수 depth 1~3, 개념별 MIN(depth) (§4.4-4).
        // CTE 앵커가 자기 자신(depth 0)을 포함하므로 별도 depth0 행 구성이 불필요.
        Map<Integer, Integer> minDepthByConcept = new LinkedHashMap<>();
        for (int conceptId : unknown) {
            conceptRepository.findPrerequisitesWithDepth(conceptId, EXPANSION_DEPTH)
                    .forEach(cd -> minDepthByConcept.merge(cd.conceptId(), cd.depth(), Integer::min));
        }

        // skill_id 일괄 조회 (미매핑 = 맵에 키 부재 — NULL·행부재 모두 커버)
        Set<Integer> allConcepts = new HashSet<>(minDepthByConcept.keySet());
        answers.forEach(a -> allConcepts.add(a.getConceptId()));
        Map<Integer, Integer> skillIdByConcept = conceptRepository.findSkillIdsByConceptIds(allConcepts);

        // DKT 시퀀스: 답변 입력 순서 그대로, [skillId, 안다=1/모른다=0] ×10 (§4.4-2)
        List<int[]> sequence = buildSequence(answers, skillIdByConcept);
        double[] predictions = sequence.isEmpty() ? null : dktClient.predict(sequence);

        // 시급도: probabilityList[skillId-1] + 가드 3종 (null 응답·미매핑·범위 초과)
        Map<Integer, Double> percentByConcept = new LinkedHashMap<>();
        if (predictions != null) {
            for (int conceptId : minDepthByConcept.keySet()) {
                Integer skillId = skillIdByConcept.get(conceptId);
                if (skillId == null) {
                    log.warn("diagnosis: skill_id 미매핑 개념 {} — 시급도 결측 처리", conceptId);
                    continue;
                }
                int index = skillId - 1;
                if (index < 0 || index >= predictions.length) {
                    log.warn("diagnosis: skill_id {} 가 예측 벡터 범위({}) 밖 — 시급도 결측 처리",
                            skillId, predictions.length);
                    continue;
                }
                // 모델 원시 출력은 0~1 확률 — percent(0~100)로 정규화 후 저장·등급 컷.
                // 40/65 컷(구 ResultView 선례)은 0~100 전제인데 구 경로는 원시값을 그대로
                // 저장해 전 카드 HIGH 쏠림 (2026-07-14 R2 실측: 실서빙 분포 0.06~0.59).
                // 구 경로 무접촉 — 신규 경로만 정규화 (docs/benchmark/m7-r2-dkt-selfreport.md).
                percentByConcept.put(conceptId, predictions[index] * 100.0);
            }
        }
        return new DiagnosisComputation(answers.size(), unknown, minDepthByConcept,
                percentByConcept, predictions != null);
    }

    /** [skillId, answerCode] ×10 — 입력 순서 보존, 미매핑 개념은 시퀀스에서 제외(가드). */
    static List<int[]> buildSequence(List<AnsweredConcept> answers, Map<Integer, Integer> skillIdByConcept) {
        List<int[]> sequence = new ArrayList<>();
        for (AnsweredConcept a : answers) {
            Integer skillId = skillIdByConcept.get(a.getConceptId());
            if (skillId == null) {
                LoggerFactory.getLogger(DiagnosisAnalysisService.class)
                        .warn("diagnosis: skill_id 미매핑 개념 {} — DKT 시퀀스에서 제외", a.getConceptId());
                continue;
            }
            int answerCode = a.isKnown() ? 1 : 0;
            for (int i = 0; i < AMPLIFICATION; i++) {
                sequence.add(new int[]{skillId, answerCode});
            }
        }
        return sequence;
    }
}
