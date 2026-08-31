# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**My Math Teacher (MMT)** is a full-stack educational platform that diagnoses math learning weaknesses and provides personalized learning using AI analysis and graph-based knowledge representation. This is a solo-developed project deployed at https://www.my-math-teacher.com.

## Architecture

The project uses a **microservices architecture** with these components:
- **Frontend**: Vue.js 3 SPA served by Nginx
- **Backend**: Spring Boot 3 REST API
- **Databases**: MySQL (RDS), Neo4j (graph), Redis (cache/sessions)
- **AI Service**: TensorFlow Serving for concept mastery predictions
- **Infrastructure**: Docker Compose orchestration, GitHub Actions CI/CD, AWS EC2/RDS deployment

## Common Development Commands

### Backend (Spring Boot)
```bash
cd api

# Development
./gradlew bootRun

# Build
./gradlew clean build

# Tests
./gradlew test

# Build Docker image
./gradlew bootBuildImage
```

### Frontend (Vue.js)
```bash
cd web

# Install dependencies
npm install

# Development server
npm run dev

# Production build
npm run build

# Lint and fix
npm run lint

# Preview production build
npm run preview
```

### Local Development with Docker
```bash
# Start all services
docker-compose up -d

# View logs
docker-compose logs -f [service-name]

# Stop all services
docker-compose down

# Rebuild specific service
docker-compose up -d --build [service-name]
```

**Note**: MySQL is configured to use AWS RDS in docker-compose.yml, not a local container.

### Database Access
```bash
# Connect to Neo4j (knowledge graph)
# Browser: http://localhost:7474
# Bolt URL: bolt://localhost:7687
# Auth: neo4j/mmt2024neo4j

# Redis CLI
docker-compose exec mmt-redis redis-cli -a mmt2024jwt
```

## Key Architectural Patterns

### Backend Layer Structure (Spring Boot)
The backend follows a standard layered architecture:

```
controller/    → REST endpoints (handles HTTP requests/responses)
service/       → Business logic (orchestrates domain operations)
repository/    → Data access (JPA/Neo4j repositories)
domain/        → JPA entities (database models)
dto/           → Data transfer objects (API request/response formats)
config/        → Spring configuration (security, CORS, beans)
jwt/           → JWT token generation and validation
oauth2/        → Social login providers (Google, Naver, Kakao)
exception/     → Custom exception handling
util/          → Shared utility functions
```

**Important conventions**:
- Controllers use `@RestController` and map to `/api/v1/*` endpoints
- Services use `@Transactional` for database operations
- DTOs are separated by domain (user/, concept/, test/, etc.)
- Repository methods follow Spring Data naming conventions

### Frontend Structure (Vue.js)
```
views/         → Page-level components (DiagView, ResultView, etc.)
layout/        → Layout components (AppLayout, AppTopbar, AppMenu, etc.)
router/        → Vue Router configuration
composables/   → Reusable composition functions (api.js for HTTP)
service/       → API service classes (AuthService.js)
store/         → Vuex state management
```

**Routing**: Uses Vue Router with `createWebHistory()` (not hash mode). Nginx handles SPA fallback routing.

**API calls**: Use the `useApi()` composable from `composables/api.js` instead of direct Axios calls. This provides centralized error handling and JWT token injection.

### Database Design
- **MySQL**: Relational data (Users, Tests, Items, Answers, Results)
- **Neo4j**: Graph database storing math concepts and prerequisite relationships (directed graph where edges represent "requires" relationships)
- **Redis**: JWT token storage and session management

Key domain entities:
- `Users` - User accounts with OAuth2 provider info
- `Concept` - Math concepts/topics
- `Chapter` - Math curriculum chapters
- `Item` - Problem/question items
- `Test` - Diagnostic or personalized tests
- `Answer` - User answers to test items
- `Result` - Test result analysis
- `UserTests` - User-test relationship tracking
- `KnowledgeSpace` - Knowledge prerequisite graph
- `Probability` - AI-predicted concept mastery scores

### API Endpoint Patterns
Backend endpoints follow RESTful conventions:
- `/api/v1/auth/*` - Authentication (login, signup, token refresh)
- `/api/v1/users/*` - User management
- `/api/v1/concepts/*` - Math concepts
- `/api/v1/chapters/*` - Math chapters
- `/api/v1/tests/*` - Test operations
- `/api/v1/items/*` - Problem items
- `/api/v1/ai/*` - AI diagnosis endpoints
- `/oauth2/authorization/*` - OAuth2 login redirects (handled by Spring Security)

### Authentication Flow
1. **JWT-based authentication**: Tokens stored in Redis with TTL
2. **Social login**: OAuth2 integration with Google, Naver, Kakao
3. **Security filter**: Spring Security filter chain validates JWT on protected endpoints
4. **Frontend**: Stores tokens in cookies via `vue-cookies`

### AI Integration
The AI service predicts concept mastery probabilities based on user answer patterns:
- Input: User's answer history (correct/incorrect patterns)
- Output: Probability scores for each math concept
- Model: TensorFlow neural network served via TensorFlow Serving
- Dataset: Based on AIHub "수학분야 학습자 역량 측정 데이터"

### Graph Visualization
- Uses **Cytoscape.js** to visualize math concept prerequisite graphs
- Layout algorithm: Klay (hierarchical layout for directed graphs)
- Interactive: Users can click nodes to see concept details and relationships
- Implemented in `ConceptView.vue`

## Development Workflow

### Branch Strategy
- `main` - Production branch (deployed to EC2)
- Feature branches: Create from `main`, name with `feature/*` or `refactor/*` prefix
- Current active branch: `refactor/front` (frontend refactoring work)

### CI/CD Pipeline
GitHub Actions workflows auto-deploy on push to `main`:
1. **api-ci-cd-with-ec2.yml** - Backend deployment
2. **web-ci-cd-with-ec2.yml** - Frontend deployment
3. **neo4j-ci-cd-with-ec2.yml** - Neo4j database
4. **ai-ci-cd-with-ec2.yml** - AI service

**Deployment flow**:
```
Push to main → Build Docker image → Push to Docker Hub → SSH to EC2 → Pull and restart containers
```

### Configuration Files
- `api/src/main/resources/application.yml` - Spring Boot config (includes profile `securelocal` for credentials)
- `web/nginx.conf` - Nginx reverse proxy config (proxies `/api/v1/` and `/oauth2/` to backend)
- `docker-compose.yml` - Local development environment setup
- Environment variables passed via docker-compose for sensitive data (DB credentials, OAuth2 secrets, JWT secret)

## Important Notes

### Security
- **Never commit credentials**: OAuth2 secrets, DB passwords, JWT secrets are in docker-compose.yml and should stay out of Git
- **CORS**: Backend allows specific origins configured in `SecurityConfig.java`
- **JWT validation**: All protected endpoints validate JWT via Spring Security filters

### Data Initialization
SQL scripts in `api/sql/` for database initialization:
- `insert_concepts_v1.sql` - Math concepts
- `insert_chapters.sql` - Chapter hierarchy
- `insert_items.sql` - Problem items
- `insert_diag_tests.sql` - Diagnostic tests
- Use `insert_concepts_opti.sql` for optimized concept data

Neo4j initialization scripts in `neo4j/init/` for graph database setup.

### Frontend-Backend Integration
- **Nginx proxy**: Frontend makes API calls to `/api/v1/*` which Nginx proxies to `mmt-backend:8080`
- **CORS**: Spring Security allows `http://localhost:5173` (Vite dev server) and production domains
- **OAuth2 redirects**: Handled by backend, frontend receives tokens via cookies

### Performance Testing
Performance test modules exist in `api/src/main/java/com/mmt/api/performanceTest/` for load testing specific endpoints.

### Korean Language
This is a Korean education platform. Much of the codebase comments, commit messages, and README are in Korean. User-facing strings are also in Korean.

## Tech Stack Versions
- Java 17
- Spring Boot 3.1.6
- Gradle 8.4
- Vue.js 3.2.41
- Vite 4.2.1
- Node.js 14 (for builds)
- MySQL 8 (via RDS)
- Neo4j (with bolt protocol on port 7687)
- Redis (password-protected)
- TensorFlow Serving
- Nginx 1.21.4-alpine
- Docker & Docker Compose

## Development Ports
- Frontend (Vite dev): 5173
- Backend (Spring Boot): 8080
- Frontend (Nginx prod): 80
- Neo4j Browser: 7474
- Neo4j Bolt: 7687
- Redis: 6379
- AI Service (TensorFlow Serving): 8501
