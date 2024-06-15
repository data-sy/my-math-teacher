# Docker 이미지 빌드
docker build -t mymathteacher/mmt-neo4j:1.0.0 . --no-cache
docker push mymathteacher/mmt-neo4j:1.0.0

# Docker 컨테이너 실행
docker run -d --name mmt-neo4j --network mmt-network mymathteacher/mmt-neo4j:1.0.0

# 로그 확인
docker logs -f mmt-neo4j

# 컨테이너 명령어 실행
docker exec -it mmt-neo4j cypher-shell -u 이름 -p 비번 -f /var/lib/neo4j/import/init.cypher

# # 깃허브액션으로 EC2에 CICD로 올린 건 컨테이너 이름 바뀜 
# # 접속 잘 되는지 확인
# docker exec -it ec2-user-mmt-neo4j-1 cypher-shell -u 이름 -p 비번
# # 잘 되면 나가기
# :exit
# # 데이터 초기화
# docker exec -it ec2-user-mmt-neo4j-1 cypher-shell -u 이름 -p 비번 -f /var/lib/neo4j/import/init.cypher

# # 데이터 조회
# MATCH (n) RETURN n;
# # 나가기
# :exit

