---
description: ADR 초안 작성
---

다음 작업 수행:

1. docs/adr/ 디렉토리에서 가장 최근 ADR 번호 확인 (ls 또는 find 사용)
2. 다음 번호로 새 ADR 파일 생성:
   - 파일명: NNNN-$ARGUMENTS-kebab-case.md (한국어 주제는 영어 슬러그로 변환)
   - 경로: docs/adr/
3. docs/adr/_template.md 존재 시 해당 템플릿 사용. 없으면 다음 섹션 포함:
   - # ADR NNNN: (제목)
   - ## Status (Proposed)
   - ## Context
   - ## Decision
   - ## Consequences (Positive / Negative / Neutral)
   - ## Alternatives Considered

4. 주제: $ARGUMENTS
5. Context 섹션은 "[사용자 입력 대기 — 결정을 유발한 배경을 채워주세요]"로 둘 것
6. Status는 항상 "Proposed"로 시작 (내가 승인하면 수동으로 Accepted로 변경)
