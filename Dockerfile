# JDK 17 베이스 이미지 사용
FROM eclipse-temurin:17-jdk-alpine

# 컨테이너 내 작업 디렉토리 설정
WORKDIR /app

# 빌드된 jar 파일을 컨테이너의 app.jar로 복사 (빌드 폴더 경로는 프로젝트에 맞게 수정)
# 기본적으로 Gradle을 쓰신다면 보통 build/libs/*.jar 입니다.
COPY build/libs/*SNAPSHOT.jar app.jar

# 스프링 부트 포트 노출
EXPOSE 8080

# 애플리케이션 실행
ENTRYPOINT ["java", "-jar", "app.jar"]