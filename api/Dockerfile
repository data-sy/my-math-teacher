# 빌드 이미지로 OpenJDK 17 & Gradle을 지정
FROM gradle:8.4-jdk17 AS build

# 소스코드를 복사할 작업 디렉토리를 생성
WORKDIR /app

# 라이브러리 설치에 필요한 파일만 복사
COPY build.gradle settings.gradle ./
COPY gradlew ./
COPY gradle ./gradle

# Gradle Wrapper를 초기화
RUN chmod +x ./gradlew
RUN ./gradlew dependencies --no-daemon

# 호스트 머신의 소스코드를 작업 디렉토리로 복사
COPY . /app

# 멀티 스테이징 빌드
# Gradle Wrapper를 사용하여 빌드. JAR 파일 생성
RUN ./gradlew clean build -x test --no-daemon

# 런타임 이미지로 OpenJDK 17-jre-slim 지정
# FROM openjdk:17-jre-slim
# 위의 이미지는 linux/arm64/v8에서 사용 불가
# 리팩토링) 경량화 할 수 있는 방법 찾기
FROM amazoncorretto:17-alpine

# 애플리케이션을 실행할 작업 디렉토리를 생성
WORKDIR /app

# 빌드 이미지에서 생성된 JAR 파일을 런타임 이미지로 복사
COPY --from=build /app/build/libs/*.jar /app/mmt.jar

EXPOSE 8080 
ENTRYPOINT ["java"] 
CMD ["-jar", "mmt.jar"]
