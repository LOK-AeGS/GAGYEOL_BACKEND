# Java 17 환경
FROM eclipse-temurin:17-jdk-alpine

# 컨테이너 내부에 /app 이라는 작업 폴더를 만듭니다.
WORKDIR /app

# 빌드된 jar 파일을 복사하는 대신, 호스트의 코드를 직접 실행합니다.
# 윈도우에서 작성된 스크립트의 줄바꿈 오류(CRLF)를 방지하기 위해 권한을 줍니다.
RUN apk add --no-cache bash

# Gradle을 이용해 스프링 부트를 실행하는 명령어입니다.
CMD ["./gradlew", "bootRun"]