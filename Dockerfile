FROM eclipse-temurin:21-jdk-nanoserver-ltsc2022

WORKDIR /app

COPY target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java","-jar","app.jar"]