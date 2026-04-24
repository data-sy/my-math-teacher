#도커 이미지 빌드 및 푸시
docker buildx build --push --platform linux/amd64,linux/arm64 -t mymathteacher/mmt-backend:1.0.0 . --no-cache
# docker build -t mymathteacher/mmt-backend:1.0.0 . --no-cache
# docker push mymathteacher/mmt-backend:1.0.0 

# 컨테이너 생성
# docker run -d -p 8080:8080 -e RDB_URL=mmt-mysql -e NOSQL_URL=mmt-redis -e GDB_URL=mmt-neo4j --network mmt-network --name mmt-backend mymathteacher/mmt-backend:1.0.0
# proxy 사용 시
docker run -d -e RDB_URL=mmt-mysql -e NOSQL_URL=mmt-redis -e GDB_URL=mmt-neo4j --network mmt-network --name mmt-backend mymathteacher/mmt-backend:1.0.0 

# 로그 확인
docker logs -f mmt-backend
# EC2
docker logs -f ec2-user-mmt-backend-1

