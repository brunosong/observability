FROM eclipse-temurin:17-jre
WORKDIR /app
COPY build/libs/*-0.0.1-SNAPSHOT.jar /app/app.jar
EXPOSE 8081
ENTRYPOINT ["java","-jar","/app/app.jar"]
