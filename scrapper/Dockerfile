FROM maven:3.9.12-eclipse-temurin-25 AS build
WORKDIR /workspace
COPY . .
RUN mvn -pl scrapper -am package -DskipTests

FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /workspace/scrapper/target/scrapper-*.jar /app/app.jar
EXPOSE 8081
ENTRYPOINT ["java","-jar","/app/app.jar"]
