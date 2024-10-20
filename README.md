# My Math Teacher ( MMT )
학생들의 수학 취약점을 진단하고, 맞춤 학습을 제공하여 수학 실력 향상을 돕는 서비스

<a name="readme-top"></a>

<!-- TABLE OF CONTENTS -->

## 목차

1. [프로젝트 개요](#Overview)
2. [서비스 소개](#Intro)
3. [아키텍처 및 기술 스택](#Arch)
4. [시스템 설계 및 구현 상세](#Design)
5. [시작 및 배포](#Start)
6. [레퍼런스](#Ref)

<br/>
<!-- ABOUT THE PROJECT -->

<a name="Overview"> </a>

## 💻 프로젝트 개요
기간 : 2023.11.22 ~  <br/>
개발 인원 : 1인 개발 <br/>
서비스 링크 : https://www.my-math-teacher.com  <br/>
이슈 트래커 링크 <br/>
블로그 시리즈 링크 <br/>

<br/>
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
![](https://velog.velcdn.com/images/data_sy/post/8f3d34ad-f419-4f20-996a-ce063c7bf471/image.jpg)


### 기술 스택
버전 추가

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
![](https://velog.velcdn.com/images/data_sy/post/94b3edf8-29ed-46e6-ad93-a4abe363a4e0/image.png)


### API 명세서
[POSTMAN API 명세서](https://documenter.getpostman.com/view/28842793/2s9YC1XEmW)

<br/>
<!-- References -->

<a name="Ref"> </a>

## 🤝 레퍼런스
- [AIHub 수학분야 학습자 역량 측정 데이터](https://aihub.or.kr/aihubdata/data/view.do?currMenu=115&topMenu=100&aihubDataSe=realm&dataSetSn=133)

<p align="right">(<a href="#readme-top">맨 위로</a>)</p>

