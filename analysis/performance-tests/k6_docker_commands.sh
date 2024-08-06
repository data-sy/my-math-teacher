# k6 도커 이미지 풀
docker pull grafana/k6

# 요약된 결과를 json 파일로 저장하며 run
# docker run --rm -v $(pwd):/scripts -i grafana/k6 run /scripts/test_concepts_by_chapter.js
docker run --rm -v $(pwd):/scripts -v $(pwd)/results:/results -i grafana/k6 run /scripts/test_concepts_by_chapter.js
