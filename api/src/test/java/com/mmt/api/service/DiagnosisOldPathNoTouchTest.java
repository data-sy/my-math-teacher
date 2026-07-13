package com.mmt.api.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * spec-01 §8 구 경로 무접촉 (R1 소멸 증명) — 위반 감지 tripwire.
 *
 * 신규 자가진단 경로의 소스가 구 채점 경로의 테이블·메서드
 * (answers·tests_items·items·findAIInput·findBefore)를 일절 참조하지 않는지
 * 소스 레벨에서 감시한다. 위반은 컴파일 에러가 아니라 조용한 데이터 오염으로
 * 나타나므로(S2=C 리뷰 R1) 참조 자체를 결함으로 잡는다.
 *
 * 검사 대상 = 진단 경로 소스 파일 고정 목록 + diagnosis 패키지 전체 (신규 파일 자동 포함).
 */
class DiagnosisOldPathNoTouchTest {

    private static final Path MAIN = Path.of("src/main/java/com/mmt/api");

    private static final List<Path> DIAGNOSIS_SOURCES = List.of(
            MAIN.resolve("service/DiagnosisService.java"),
            MAIN.resolve("service/DiagnosisAnalysisService.java"),
            MAIN.resolve("service/DiagnosisDktClient.java"),
            MAIN.resolve("controller/DiagnosisController.java"));

    private static final List<Path> DIAGNOSIS_PACKAGES = List.of(
            MAIN.resolve("dto/diagnosis"),
            MAIN.resolve("domain/diagnosis"),
            MAIN.resolve("repository/diagnosis"));

    /**
     * 구 경로 테이블 토큰 — SQL 문자열 리터럴 안에서만 금지 (자바 식별자 'answers' 류
     * 로컬 변수는 오염과 무관). \b 는 언더스코어를 경계로 안 치므로
     * self_report_answers 는 \banswers\b 에 걸리지 않는다 (신규 테이블명 허용).
     */
    private static final List<Pattern> FORBIDDEN_TABLES = List.of(
            Pattern.compile("\\banswers\\b"),
            Pattern.compile("\\btests_items\\b"),
            Pattern.compile("\\bitems\\b"));

    /** 구 경로 타입·메서드 토큰 — 소스 전체에서 금지. */
    private static final List<Pattern> FORBIDDEN_SYMBOLS = List.of(
            Pattern.compile("\\bfindAIInput\\b"),
            Pattern.compile("\\bfindBefore\\b"),
            Pattern.compile("\\bAnswerRepository\\b"),
            Pattern.compile("\\bAnswerService\\b"),
            Pattern.compile("\\bItemRepository\\b"),
            Pattern.compile("\\bItemService\\b"));

    private static final Pattern STRING_LITERAL =
            Pattern.compile("\"\"\"(.*?)\"\"\"|\"((?:[^\"\\\\]|\\\\.)*)\"", Pattern.DOTALL);

    @Test
    @DisplayName("신규 진단 경로 소스는 구 경로 테이블·메서드를 참조하지 않는다")
    void diagnosisPathNeverReferencesOldPath() throws IOException {
        for (Path file : allDiagnosisSources()) {
            // 주석은 스캔 제외 — 가드레일을 '설명'하는 문서 언급은 참조가 아니다.
            String source = stripComments(Files.readString(file));
            for (Pattern forbidden : FORBIDDEN_SYMBOLS) {
                assertThat(forbidden.matcher(source).find())
                        .as("%s 가 금지 심볼 '%s' 을 참조 — 구 경로 무접촉 가드레일 위반 (spec-01 §8)",
                                file, forbidden.pattern())
                        .isFalse();
            }
            java.util.regex.Matcher literals = STRING_LITERAL.matcher(source);
            while (literals.find()) {
                String literal = literals.group();
                for (Pattern forbidden : FORBIDDEN_TABLES) {
                    assertThat(forbidden.matcher(literal).find())
                            .as("%s 의 문자열 리터럴이 구 경로 테이블 '%s' 을 참조 — 무접촉 위반 (spec-01 §8): %s",
                                    file, forbidden.pattern(), literal)
                            .isFalse();
                }
            }
        }
    }

    @Test
    @DisplayName("검사 대상 파일이 실제로 존재한다 (경로 이동 시 tripwire 무력화 방지)")
    void watchedSourcesExist() {
        for (Path file : DIAGNOSIS_SOURCES) {
            assertThat(file).as("진단 경로 소스가 이동/삭제됨 — 본 테스트 목록 갱신 필요").exists();
        }
    }

    private static String stripComments(String source) {
        return source.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("//[^\n]*", "");
    }

    private static List<Path> allDiagnosisSources() throws IOException {
        List<Path> files = new java.util.ArrayList<>(DIAGNOSIS_SOURCES);
        for (Path pkg : DIAGNOSIS_PACKAGES) {
            if (Files.isDirectory(pkg)) {
                try (Stream<Path> stream = Files.walk(pkg)) {
                    stream.filter(p -> p.toString().endsWith(".java")).forEach(files::add);
                }
            }
        }
        return files;
    }
}
