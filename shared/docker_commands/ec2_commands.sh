# 업데이트
sudo yum update -y
# 도커 설치
sudo yum install docker -y
# 도커 서비스 시작
sudo service docker start
# 도커 서비스 실행 확인 (Active: active (running))
systemctl status docker.service
# 도커 그룹 권한 설정
sudo usermod -a -G docker ec2-user
# 권한 적용 확인
docker ps
# 이 때, socket 관련 permission denied 나오면
# Docker 데몬 소켓 파일 권한 변경
sudo chmod 666 /var/run/docker.sock
# 다시 권한 적용 확인
docker ps

# 도커 컴포즈 설치
# auto-start에 docker 등록
sudo chkconfig docker on
# 인스턴스 재시작
sudo reboot
# EC2 다시 접속!!!
ssh -i pem경로 이름@IP주소
# 도커 컴포즈 설치
sudo curl -L https://github.com/docker/compose/releases/latest/download/docker-compose-$(uname -s)-$(uname -m) -o /usr/local/bin/docker-compose
# 권한 부여
sudo chmod +x /usr/local/bin/docker-compose
# 설치 확인
docker-compose version


# 도커 로그인
docker login
# username과 비번 치면 success
# 볼륨 생성
docker volume create mysql-vol
# 네트워크 생성
docker network create mmt-network
# 도커 풀 & 런
# msyql
docker pull mmt2024/mmt-mysql:1.0.0
docker run -d --name mmt-mysql -v mysql-vol:/var/lib/mysql --network mmt-network mmt2024/mmt-mysql:1.0.0
# neo4j는 초기화도 진행
docker pull mmt2024/mmt-neo4j:1.0.0
docker run -d --name mmt-neo4j --network mmt-network mmt2024/mmt-neo4j:1.0.0
    # 로그로 서버 started 확인
docker logs -f mmt-neo4j
docker exec -it mmt-neo4j cypher-shell -u neo4j -p 비번 -f /var/lib/neo4j/import/init.cypher
# redis
docker pull mmt2024/mmt-redis:1.0.0
docker run -d --name mmt-redis --network mmt-network mmt2024/mmt-redis:1.0.0
# api
docker pull mmt2024/mmt-backend:1.0.0
docker run -d -e RDB_URL=mmt-mysql -e NOSQL_URL=mmt-redis -e GDB_URL=mmt-neo4j --network mmt-network --name mmt-backend mmt2024/mmt-backend:1.0.0 
# ai
docker pull mmt2024/mmt-ai:1.0.0
docker run -d -p 8000:5000 --network mmt-network --name mmt-ai mmt2024/mmt-ai:1.0.0
# web
docker pull mmt2024/mmt-web:1.0.0
docker run -d -p 80:80 --network mmt-network --name mmt-front mmt2024/mmt-front:1.0.0

# redis -> 공식 이미지 그대로 가져다쓰는 걸로 바뀜
# ai -> ai serving으로 바뀜

# 이것 대신 도커 컴포즈 사용
