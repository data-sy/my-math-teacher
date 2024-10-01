# k6 도커 이미지 풀
docker pull grafana/k6

# 컨테이너 실행
docker run -v $(pwd):/scripts -i grafana/k6 run /scripts/performance_test.js
docker run --rm -v $(pwd):/scripts -v $(pwd)/results:/results -i grafana/k6 run /scripts/test_concepts_by_chapter.js

docker run --rm -v $(pwd):/scripts -i grafana/k6 run /scripts/test_item_by_concept.js
