---
description: 현재 브랜치 diff를 셀프 리뷰
---

다음 순서로 셀프 리뷰 수행:

1. `git diff main...HEAD` 실행 (main이 없으면 master 또는 기본 브랜치 확인)
2. 변경된 파일 목록을 카테고리별 분류:
   - source (api/, web/, ai/ 하위 코드)
   - test
   - config (build.gradle, package.json, .claude/, docker-compose.yml 등)
   - docs (CLAUDE.md, docs/ 하위)
   - assets (shared/ 하위 — 다이어그램·스크립트·시드 데이터)

3. 다음 관점에서 점검:
   - 로직 오류·엣지 케이스 누락
   - 테스트 커버리지 (신규 코드에 대응하는 테스트 존재 여부)
   - CLAUDE.md 컨벤션 위반 (해당 워크스페이스 기준)
   - 성능 회귀 가능성
   - 민감 정보 커밋 여부 (API 키·패스워드·토큰)
   - 워크스페이스 경계 위반 (api가 web 내부를 직접 참조하는 등)

4. 결과를 다음 형식으로 출력:
   ## 요약 (변경 파일 수, 라인 수)
   ## 카테고리별 변경
   ## 지적 사항 (우선순위순: Critical / Major / Minor)
   ## 누락된 테스트
   ## 제안
