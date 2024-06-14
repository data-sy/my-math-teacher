# 베이스 이미지
FROM emacski/tensorflow-serving:latest

# 환경 변수 설정
ENV MODEL_NAME=my_model

# 모델을 컨테이너의 모델 디렉토리로 복사
COPY ./savedmodel /models/my_model/1

# 작업 디렉토리를 설정
WORKDIR /models/my_model/1/

# 텐서플로우 서빙을 시작합니다.
CMD ["tensorflow_model_server", "--port=8500", "--rest_api_port=8501", "--model_name=my_model", "--model_base_path=/models/my_model"]
