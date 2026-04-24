# MMT - AI 기반 수학 튜터링 서비스

## 프로젝트 개요

MMT(My Math Teacher)는 수학 지식 간 선/후 관계를 그래프로 제공하고, AI 분석으로 학생의 수학 취약점을 진단·맞춤 학습을 제안하는 서비스다. 위계가 강한 수학 학문 특성을 반영해 "이전 지식 이해도가 다음 학습에 영향을 미친다"는 전제 위에서 설계되었다.

1인 개발 프로젝트이며, v1(2023.12~2024.07) 이후 v2(2025.02~)가 진행 중이다. 서비스 링크: https://www.my-math-teacher.com

## 모노레포 구조

- `api/` — Spring Boot 기반 백엔드 서버 (활성 개발). 세부 규칙은 @api/CLAUDE.md 참조
- `web/` — Vue 기반 프론트엔드 (활성 개발). 세부 규칙은 @web/CLAUDE.md 참조
- `ai/` — DKT 모델 학습 스크립트 (비활성). 결과 모델은 TensorFlow Serving으로 배포됨. 현재 건드리지 않음
- `neo4j/` — 초기 데이터 생성 및 Docker Hub 이미지 빌드용 (비활성). 현재 건드리지 않음
- `shared/` — 프로젝트 자산 저장소 (다이어그램, 스크립트, 시드 데이터). 코드가 아닌 운영 자산
- `docs/` — 마일스톤·spec·ADR·roadmap

## 인프라

`docker-compose.yml`에 MySQL·Neo4j·Redis·TF Serving·백엔드·프론트 6종이 정의되어 있으나, 로컬 개발 워크플로우는 **인프라 3종만 컨테이너로 띄우고 백엔드·프론트는 호스트에서 직접 실행**한다.

- 로컬 인프라 기동: `docker compose up -d mmt-mysql mmt-neo4j mmt-redis`
- 백엔드 실행: `cd api && ./gradlew bootRun` (securelocal 프로파일 자동 활성화)
- 프론트 실행: `cd web && npm install && npm run dev`
- 접근 포트: API `8080`, Web `5173`, MySQL `3306`, Neo4j `7474/7687`, Redis `6379`

`docker-compose.yml`과 `api/src/main/resources/application-securelocal.yml`은 자격증명을 포함하므로 `.gitignore` 대상이다. 이 파일들은 별도 경로로 공유받거나 기존 환경에서 복사한다.

## 작업 규칙 (전역)

- 모든 작업은 `docs/roadmap.md`의 활성 마일스톤 컨텍스트 안에서 진행
- 스키마 변경·마이그레이션·레이어 간 리팩토링은 반드시 **Analyze-Before-Change** 패턴 준수 (`/analyze-before-change` 커맨드)
- 중요한 의사결정은 ADR 작성. 위치: `docs/adr/`, 템플릿: `docs/adr/_template.md`
- 커밋은 Task 단위로 분리. 여러 Task를 하나의 커밋에 묶지 말 것
- 각 CLAUDE.md 파일은 200줄 이내로 유지. 초과 시 `@` 참조로 외부 문서 분리

## 현재 활성 작업

- [Roadmap](docs/roadmap.md)
- 현재 진행 중: @docs/milestones/milestone-0-claude-code-integration.md

## 금지 사항

- `ai/`, `neo4j/` 디렉토리는 사용자의 명시적 지시 없이 수정하지 말 것
- `docker-compose.yml`의 서비스 구성은 ADR 없이 변경하지 말 것
- 공용 CLAUDE.md·문서·커밋에 민감 정보(비밀번호, OAuth 시크릿, JWT 시크릿, API 키) 포함 금지
- 로컬 절대경로(예: `/Users/...`)는 공용 CLAUDE.md에 포함 금지 — 개인 환경 기술은 `CLAUDE.local.md`로
