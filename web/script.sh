#도커 이미지 빌드 및 푸시
docker buildx build --push --platform linux/amd64,linux/arm64 -t mymathteacher/mmt-front:1.0.0 . --no-cache
# docker build -t mymathteacher/mmt-front:1.0.0 . --no-cache
# docker push mymathteacher/mmt-front:1.0.0

# 컨테이너 생성 (윈도우 cmd or Powershell 사용)
docker run -d -p 80:80 --network mmt-network --name mmt-front mymathteacher/mmt-front:1.0.0

# 로그 확인 및 http://localhost 접속
docker logs -f mmt-front
