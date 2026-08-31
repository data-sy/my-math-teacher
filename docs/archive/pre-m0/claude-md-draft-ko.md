# CLAUDE.md

이 파일은 Claude Code (claude.ai/code)가 이 레포지토리에서 작업할 때 참고하는 가이드입니다.

## 프로젝트 개요

**My Math Teacher (MMT)**는 AI 분석과 그래프 기반 지식 표현을 활용하여 학생들의 수학 취약점을 진단하고 맞춤형 학습을 제공하는 풀스택 교육 플랫폼입니다. 1인 개발 프로젝트이며, https://www.my-math-teacher.com 에 배포되어 있습니다.

## 아키텍처

이 프로젝트는 **마이크로서비스 아키텍처**를 사용하며 다음 컴포넌트로 구성됩니다:
- **프론트엔드**: Nginx가 서빙하는 Vue.js 3 SPA
- **백엔드**: Spring Boot 3 REST API
- **데이터베이스**: MySQL (RDS), Neo4j (그래프), Redis (캐시/세션)
- **AI 서비스**: 개념 숙달도 예측을 위한 TensorFlow Serving
- **인프라**: Docker Compose 오케스트레이션, GitHub Actions CI/CD, AWS EC2/RDS 배포

## 주요 개발 명령어

### 백엔드 (Spring Boot)
```bash
cd api

# 개발 실행
./gradlew bootRun

# 빌드
./gradlew clean build

# 테스트
./gradlew test

# Docker 이미지 빌드
./gradlew bootBuildImage
```

### 프론트엔드 (Vue.js)
```bash
cd web

# 의존성 설치
npm install

# 개발 서버
npm run dev

# 프로덕션 빌드
npm run build

# 린트 및 수정
npm run lint

# 프로덕션 빌드 미리보기
npm run preview
```

### Docker를 이용한 로컬 개발
```bash
# 모든 서비스 시작
docker-compose up -d

# 로그 보기
docker-compose logs -f [service-name]

# 모든 서비스 중지
docker-compose down

# 특정 서비스 재빌드
docker-compose up -d --build [service-name]
```

**참고**: docker-compose.yml에서 MySQL은 로컬 컨테이너가 아닌 AWS RDS를 사용하도록 설정되어 있습니다.

### 데이터베이스 접속
```bash
# Neo4j 접속 (지식 그래프)
# 브라우저: http://localhost:7474
# Bolt URL: bolt://localhost:7687
# 인증: neo4j/mmt2024neo4j

# Redis CLI
docker-compose exec mmt-redis redis-cli -a mmt2024jwt
```

## 주요 아키텍처 패턴

### 백엔드 계층 구조 (Spring Boot)
백엔드는 표준 레이어드 아키텍처를 따릅니다:

```
controller/    → REST 엔드포인트 (HTTP 요청/응답 처리)
service/       → 비즈니스 로직 (도메인 작업 오케스트레이션)
repository/    → 데이터 액세스 (JPA/Neo4j 레포지토리)
domain/        → JPA 엔티티 (데이터베이스 모델)
dto/           → 데이터 전송 객체 (API 요청/응답 형식)
config/        → Spring 설정 (보안, CORS, 빈)
jwt/           → JWT 토큰 생성 및 검증
oauth2/        → 소셜 로그인 프로바이더 (구글, 네이버, 카카오)
exception/     → 커스텀 예외 처리
util/          → 공유 유틸리티 함수
```

**중요한 규칙들**:
- 컨트롤러는 `@RestController`를 사용하고 `/api/v1/*` 엔드포인트에 매핑됩니다
- 서비스는 데이터베이스 작업에 `@Transactional`을 사용합니다
- DTO는 도메인별로 분리되어 있습니다 (user/, concept/, test/ 등)
- 레포지토리 메서드는 Spring Data 네이밍 컨벤션을 따릅니다

### 프론트엔드 구조 (Vue.js)
```
views/         → 페이지 레벨 컴포넌트 (DiagView, ResultView 등)
layout/        → 레이아웃 컴포넌트 (AppLayout, AppTopbar, AppMenu 등)
router/        → Vue Router 설정
composables/   → 재사용 가능한 컴포지션 함수 (api.js for HTTP)
service/       → API 서비스 클래스 (AuthService.js)
store/         → Vuex 상태 관리
```

**라우팅**: Vue Router의 `createWebHistory()`를 사용합니다 (해시 모드 아님). Nginx가 SPA 폴백 라우팅을 처리합니다.

**API 호출**: 직접 Axios를 호출하는 대신 `composables/api.js`의 `useApi()` 컴포저블을 사용하세요. 이는 중앙화된 에러 처리와 JWT 토큰 주입을 제공합니다.

### 데이터베이스 설계
- **MySQL**: 관계형 데이터 (Users, Tests, Items, Answers, Results)
- **Neo4j**: 수학 개념과 선수 관계를 저장하는 그래프 데이터베이스 (엣지가 "선수 지식" 관계를 나타내는 방향 그래프)
- **Redis**: JWT 토큰 저장 및 세션 관리

주요 도메인 엔티티:
- `Users` - OAuth2 프로바이더 정보를 포함한 사용자 계정
- `Concept` - 수학 개념/주제
- `Chapter` - 수학 교육과정 챕터
- `Item` - 문제 아이템
- `Test` - 진단 또는 맞춤형 테스트
- `Answer` - 테스트 문항에 대한 사용자 답변
- `Result` - 테스트 결과 분석
- `UserTests` - 사용자-테스트 관계 추적
- `KnowledgeSpace` - 지식 선수 그래프
- `Probability` - AI가 예측한 개념 숙달도 점수

### API 엔드포인트 패턴
백엔드 엔드포인트는 RESTful 규칙을 따릅니다:
- `/api/v1/auth/*` - 인증 (로그인, 회원가입, 토큰 갱신)
- `/api/v1/users/*` - 사용자 관리
- `/api/v1/concepts/*` - 수학 개념
- `/api/v1/chapters/*` - 수학 챕터
- `/api/v1/tests/*` - 테스트 작업
- `/api/v1/items/*` - 문제 아이템
- `/api/v1/ai/*` - AI 진단 엔드포인트
- `/oauth2/authorization/*` - OAuth2 로그인 리다이렉트 (Spring Security가 처리)

### 인증 플로우
1. **JWT 기반 인증**: TTL이 적용된 토큰을 Redis에 저장
2. **소셜 로그인**: 구글, 네이버, 카카오와의 OAuth2 통합
3. **보안 필터**: Spring Security 필터 체인이 보호된 엔드포인트에서 JWT를 검증
4. **프론트엔드**: `vue-cookies`를 통해 쿠키에 토큰 저장

### AI 통합
AI 서비스는 사용자의 답변 패턴을 기반으로 개념 숙달도 확률을 예측합니다:
- 입력: 사용자의 답변 기록 (정답/오답 패턴)
- 출력: 각 수학 개념에 대한 확률 점수
- 모델: TensorFlow Serving을 통해 서빙되는 TensorFlow 신경망
- 데이터셋: AIHub "수학분야 학습자 역량 측정 데이터" 기반

### 그래프 시각화
- **Cytoscape.js**를 사용하여 수학 개념 선수 그래프를 시각화
- 레이아웃 알고리즘: Klay (방향 그래프를 위한 계층적 레이아웃)
- 인터랙티브: 사용자가 노드를 클릭하여 개념 상세 정보와 관계를 볼 수 있음
- `ConceptView.vue`에 구현되어 있음

## 개발 워크플로우

### 브랜치 전략
- `main` - 프로덕션 브랜치 (EC2에 배포됨)
- 피처 브랜치: `main`에서 생성, `feature/*` 또는 `refactor/*` 접두사 사용
- 현재 활성 브랜치: `refactor/front` (프론트엔드 리팩토링 작업)

### CI/CD 파이프라인
GitHub Actions 워크플로우가 `main`에 푸시하면 자동 배포:
1. **api-ci-cd-with-ec2.yml** - 백엔드 배포
2. **web-ci-cd-with-ec2.yml** - 프론트엔드 배포
3. **neo4j-ci-cd-with-ec2.yml** - Neo4j 데이터베이스
4. **ai-ci-cd-with-ec2.yml** - AI 서비스

**배포 플로우**:
```
main에 푸시 → Docker 이미지 빌드 → Docker Hub에 푸시 → EC2에 SSH → 컨테이너 Pull 및 재시작
```

### 설정 파일
- `api/src/main/resources/application.yml` - Spring Boot 설정 (자격 증명을 위한 `securelocal` 프로파일 포함)
- `web/nginx.conf` - Nginx 리버스 프록시 설정 (`/api/v1/`과 `/oauth2/`를 백엔드로 프록시)
- `docker-compose.yml` - 로컬 개발 환경 설정
- 민감한 데이터는 docker-compose를 통해 환경 변수로 전달 (DB 자격 증명, OAuth2 시크릿, JWT 시크릿)

## 중요 사항

### 보안
- **자격 증명 커밋 금지**: OAuth2 시크릿, DB 비밀번호, JWT 시크릿은 docker-compose.yml에 있으며 Git에서 제외되어야 합니다
- **CORS**: 백엔드는 `SecurityConfig.java`에 설정된 특정 origin만 허용합니다
- **JWT 검증**: 모든 보호된 엔드포인트는 Spring Security 필터를 통해 JWT를 검증합니다

### 데이터 초기화
데이터베이스 초기화를 위한 `api/sql/`의 SQL 스크립트:
- `insert_concepts_v1.sql` - 수학 개념
- `insert_chapters.sql` - 챕터 계층
- `insert_items.sql` - 문제 아이템
- `insert_diag_tests.sql` - 진단 테스트
- 최적화된 개념 데이터는 `insert_concepts_opti.sql` 사용

그래프 데이터베이스 설정을 위한 `neo4j/init/`의 Neo4j 초기화 스크립트.

### 프론트엔드-백엔드 통합
- **Nginx 프록시**: 프론트엔드는 `/api/v1/*`로 API 호출을 하고, Nginx가 이를 `mmt-backend:8080`으로 프록시합니다
- **CORS**: Spring Security는 `http://localhost:5173` (Vite 개발 서버)와 프로덕션 도메인을 허용합니다
- **OAuth2 리다이렉트**: 백엔드가 처리하고, 프론트엔드는 쿠키를 통해 토큰을 받습니다

### 성능 테스트
특정 엔드포인트의 부하 테스트를 위한 성능 테스트 모듈이 `api/src/main/java/com/mmt/api/performanceTest/`에 있습니다.

### 한국어
한국 교육 플랫폼입니다. 코드베이스의 많은 주석, 커밋 메시지, README가 한국어로 작성되어 있습니다. 사용자 대면 문자열도 한국어입니다.

## 기술 스택 버전
- Java 17
- Spring Boot 3.1.6
- Gradle 8.4
- Vue.js 3.2.41
- Vite 4.2.1
- Node.js 14 (빌드용)
- MySQL 8 (RDS 사용)
- Neo4j (포트 7687에서 bolt 프로토콜)
- Redis (비밀번호 보호)
- TensorFlow Serving
- Nginx 1.21.4-alpine
- Docker & Docker Compose

## 개발 포트
- 프론트엔드 (Vite dev): 5173
- 백엔드 (Spring Boot): 8080
- 프론트엔드 (Nginx prod): 80
- Neo4j Browser: 7474
- Neo4j Bolt: 7687
- Redis: 6379
- AI Service (TensorFlow Serving): 8501
