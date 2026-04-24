# My Math Teacher ( MMT )
학생들의 수학 취약점을 진단하고, 맞춤 학습을 제공하여 수학 실력 향상을 돕는 서비스

<a name="readme-top"></a>

<!-- TABLE OF CONTENTS -->

## 목차

1. [프로젝트 개요](#Overview)
2. [서비스 소개](#Intro)
3. [아키텍처 및 기술 스택](#Arch)
4. [시스템 설계 및 구현 상세](#Design)
5. [로컬 개발 환경 셋업](#Setup)
6. [레퍼런스](#Ref)

<br/>
<!-- ABOUT THE PROJECT -->

<a name="Overview"> </a>

## 💻 프로젝트 개요
기간 : (v1) 2023.12 ~ 2024.07 (8개월), (v2) 2025.02 ~ (진행 중) <br/>
개발 인원 : 1인 개발 <br/>
서비스 링크 : https://www.my-math-teacher.com  <br/>

<!-- Introduction -->

<a name="Intro"> </a>

## 서비스 소개
MMT는  **수학 지식 간 선/후 관계를 그래프로 확인**하고 **수학 취약점을 진단**하고 **맞춤학습을 제공**하여 학생들의  수학 실력 향상을 돕는 서비스입니다.

### 배경

수학은 위계가 강한 학문이라 **이전 지식의 이해도**가 **다음 지식의 학습에** 영향을 미칩니다.
즉, 수학 실력 향상을 위해 **선수지식을 잘 알고 있는지 파악**하는 것도 중요합니다.
따라서 지식 간 선후 관계를 파악하고 취약점을 찾아내는 것은 수학 공부의 효과적인 방법입니다.

### MMT는 다음 3가지 고민에서 시작되었습니다.

- **고등학교** 수학을 공부하는데, **중학교 때 배운** 개념들이 필요해서 힘들어!
- 내가 뭘 알고 있고, 무엇을 모르는지 명확히 알고 싶어!
- 나에게 딱 맞는 문제들을 풀어서 수학 실력을 키우고 싶어!

### MMT가 제안하는 해결 방법은 다음과 같습니다.

1. 수학 지식 간 **선후 관계를 그래프**로 볼 수 있습니다. 
![](https://velog.velcdn.com/images/data_sy/post/c585350c-0224-42f8-b2c4-1b3b65ebba3a/image.gif)
2. AI 분석을 통해 **수학 취약점을 진단**할 수 있습니다.
![](https://velog.velcdn.com/images/data_sy/post/121f5eb0-804d-4798-81e8-390f8c0b517a/image.png)
3. 취약점에 따른 **맞춤 학습지**를 제작할 수 있습니다.
   
    개발 중

<p align="right">(<a href="#readme-top">맨 위로</a>)</p>

<br/>
<!-- ARCHITECTURE -->

<a name="Arch"> </a>

## ⚒️ 아키텍처 및 기술 스택

### 아키텍처
![](https://velog.velcdn.com/images/data_sy/post/e6e5b39d-411a-4bb9-8041-da41086c461b/image.jpg)

### 기술 스택

| 분류 | 기술 |
|---|---|
| **Frontend** | <img src="https://img.shields.io/badge/vue.js-4FC08D?style=for-the-badge&logo=vue.js&logoColor=white"> <img src="https://img.shields.io/badge/cytoscape.js-F7DF1E?style=for-the-badge&logo=cytoscapedotjs&logoColor=black"> <img src="https://img.shields.io/badge/Nginx-009639?style=for-the-badge&logo=nginx&logoColor=white"> |
| **Backend** | <img src="https://img.shields.io/badge/spring_boot-6DB33F?style=for-the-badge&logo=springboot&logoColor=white"> <img src="https://img.shields.io/badge/spring_security-6DB33F?style=for-the-badge&logo=springsecurity&logoColor=white"> |
| **Database** | <img src="https://img.shields.io/badge/mysql-4479A1?style=for-the-badge&logo=mysql&logoColor=white"> <img src="https://img.shields.io/badge/neo4j-4581C3?style=for-the-badge&logo=neo4j&logoColor=white"> <img src="https://img.shields.io/badge/redis-DC382D?style=for-the-badge&logo=redis&logoColor=white"> |
| **AI** | <img src="https://img.shields.io/badge/tensorflow_serving-FF6F00?style=for-the-badge&logo=tensorflow&logoColor=white"> |
| **Infrastructure/DevOps** | <img src="https://img.shields.io/badge/github-181717?style=for-the-badge&logo=github&logoColor=white"> <img src="https://img.shields.io/badge/github_actions-2088FF?style=for-the-badge&logo=githubactions&logoColor=white"> <img src="https://img.shields.io/badge/docker-2496ED?style=for-the-badge&logo=docker&logoColor=white"> <img src="https://img.shields.io/badge/amazon_ec2-FF9900?style=for-the-badge&logo=amazonec2&logoColor=white"> <img src="https://img.shields.io/badge/amazon_rds-527FFF?style=for-the-badge&logo=amazonrds&logoColor=white"> <img src="https://img.shields.io/badge/amazon_route53-8C4FFF?style=for-the-badge&logo=amazonroute53&logoColor=white">|

<p align="right">(<a href="#readme-top">맨 위로</a>)</p>

<br/>
<!-- Design -->

<a name="Design"> </a>

## 📜 시스템 설계 및 구현 상세

### ERD
![](https://velog.velcdn.com/images/data_sy/post/539ab1a0-4dcd-4a83-b393-6486655e08ab/image.jpg)

### API 명세서
- [POSTMAN API 명세서](https://documenter.getpostman.com/view/28842793/2sAY4rE4aP)

![](https://velog.velcdn.com/images/data_sy/post/9e861682-8f5c-49db-b3d8-c9a11efb4e60/image.png)
![](https://velog.velcdn.com/images/data_sy/post/fa0f8015-57d3-4ce4-a3b5-9d0115aaa31a/image.png)
![](https://velog.velcdn.com/images/data_sy/post/02d2fc93-0e93-4834-9b12-f7df784ac662/image.png)

<br/>
<!-- Local Setup -->

<a name="Setup"> </a>

## 🛠️ 로컬 개발 환경 셋업

`docker-compose`로 인프라(MySQL / Neo4j / Redis)만 띄우고, 백엔드와 프론트는 호스트에서 직접 실행하는 구성입니다.

### 사전 요구사항

- Docker & Docker Compose
- Java 17
- Node.js 20 이상

### 환경 구성 (최초 1회)

아래 두 파일은 자격증명을 포함해 `.gitignore`로 제외되어 있습니다. 팀 내부에서 공유받거나 기존 환경을 참고해 루트에 작성합니다.

- `docker-compose.yml` — 인프라 컨테이너 설정 (MySQL/Neo4j/Redis 비밀번호 등)
- `api/src/main/resources/application-securelocal.yml` — Spring DataSource·Neo4j·Redis 접속 정보

두 파일의 자격증명은 서로 일치해야 합니다.

### 인프라 기동

```bash
docker compose up -d mmt-mysql mmt-neo4j mmt-redis
```

호스트에서 각각 `3306` / `7474·7687` / `6379` 포트로 접근할 수 있습니다.

### 초기 데이터 적재 (볼륨이 비어있는 최초 1회)

**MySQL** — FK 제약 때문에 아래 순서대로 import해야 합니다.

```bash
cd api/sql
for f in create.sql insert_chapters.sql insert_concepts_latex.sql \
         insert_concepts_sections.sql insert_knowledge_space.sql \
         insert_diag_tests.sql insert_diag_items.sql insert_diag_testsitems.sql \
         update_diag_answers.sql insert_users.sql; do
  docker compose exec -T -e MYSQL_PWD=<mmt2024_비번> mmt-mysql \
    mysql -u mmt2024 --default-character-set=utf8mb4 mmt < "$f"
done
```

> - `--default-character-set=utf8mb4`가 없으면 한글 컬럼에서 `Data too long` 에러가 납니다.
> - 개념(concepts) 데이터는 반드시 `insert_concepts_latex.sql`을 사용합니다 (작은따옴표 escape 처리된 버전).

**Neo4j** — CSV를 로드합니다.

```bash
docker compose exec -T mmt-neo4j \
  cypher-shell -u neo4j -p <neo4j_비번> < neo4j/init/init.cypher
```

적재 후 MySQL `concepts` / `knowledge_space` row 수와 Neo4j 노드 / 관계 수가 일치하면 정상입니다.

### 백엔드 / 프론트 실행

```bash
# 백엔드 (securelocal 프로파일이 자동 활성화)
cd api && ./gradlew bootRun

# 프론트
cd web && npm install && npm run dev
```

- 백엔드: http://localhost:8080
- 프론트: http://localhost:5173

### 종료

```bash
docker compose down
```

볼륨(`mysql-vol`, `neo4j-vol`)은 유지되므로 다음 실행 시 초기 데이터 적재를 반복할 필요가 없습니다.

<p align="right">(<a href="#readme-top">맨 위로</a>)</p>

<br/>
<!-- References -->

<a name="Ref"> </a>

## 🤝 레퍼런스
- [AIHub 수학분야 학습자 역량 측정 데이터](https://aihub.or.kr/aihubdata/data/view.do?currMenu=115&topMenu=100&aihubDataSe=realm&dataSetSn=133)

<p align="right">(<a href="#readme-top">맨 위로</a>)</p>

