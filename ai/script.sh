# Docker 이미지 빌드 및 푸시
docker buildx build --push --platform linux/amd64,linux/arm64 -t mmt2024/mmt-ai:1.0.0 . --no-cache
# docker build -t mmt2024/mmt-ai:1.0.0 . --no-cache
docker push mmt2024/mmt-ai:1.0.0

# 컨테이너 생성 (윈도우 cmd or Powershell 사용)
# 플라스크 버전
# docker run -d -p 8000:5000 --network mmt-network --name mmt-ai mmt2024/mmt-ai:1.0.0

# 로그 확인 및 http://localhost 접속
docker logs -f mmt-ai
