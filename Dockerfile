FROM maven:3.9.12-eclipse-temurin-21 AS builder

WORKDIR /workspace
COPY pom.xml .
RUN mvn -B dependency:go-offline
COPY src ./src
RUN mvn -B clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine

RUN addgroup -S forge && adduser -S forge -G forge
WORKDIR /app
COPY --from=builder /workspace/target/forge-flow-demo-0.1.0.jar app.jar
USER forge

EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar", "--spring.profiles.active=docker"]
