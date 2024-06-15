docker volume create neo4j-vol

# Docker 이미지 빌드
docker buildx build --push --platform linux/amd64,linux/arm64 -t mymathteacher/mmt-neo4j:1.0.0 . --no-cache
# docker push mymathteacher/mmt-neo4j:1.0.0

# 도커 풀
# docker pull mymathteacher/mmt-neo4j:1.0.0

# Docker 컨테이너 실행
docker run -d --name mmt-neo4j -v neo4j-vol:/data --network mmt-network mymathteacher/mmt-neo4j:1.0.0

# 로그 확인 # 이거 주석처리하면 안 됨!(DB 서버가 실행될 시간을 줘야 해)
docker logs -f mmt-neo4j

# 컨테이너 명령어 실행
docker exec -it mmt-neo4j cypher-shell -u 이름 -p 비번 -f /var/lib/neo4j/import/init.cypher
# docker exec -it new-mmt-neo4j cypher-shell -u 이름 -p 비번

# 컨테이너 스탑 & 삭제
# docker stop mmt-neo4j
# docker rm mmt-neo4j

# # 재실행해서 볼륨 확인
# docker run -d --name new-mmt-neo4j -v neo4j-vol:/data --network mmt-network mymathteacher/mmt-neo4j:1.0.0
# docker exec -it new-mmt-neo4j cypher-shell -u 이름 -p 비번

# # 데이터 조회
# MATCH (n) RETURN n;
# # 나가기
# :exit

# compose로 실행 시 볼륨 확인
# docker exec -it mmt-neo4j-1 cypher-shell -u 이름 -p 비번

# EC2 볼륨 테스트
# MATCH (n {concept_id: 4015}), (m)
# WHERE m.chapter_name = n.chapter_name
# RETURN (m);
