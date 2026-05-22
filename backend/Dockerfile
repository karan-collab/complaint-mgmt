# Build stage
FROM maven:3.9-eclipse-temurin-11 AS build
WORKDIR /app

COPY pom.xml settings.xml ./
COPY src ./src

RUN mvn -s settings.xml -DskipTests package -q

# Run stage
FROM eclipse-temurin:11-jre-alpine
WORKDIR /app

RUN apk add --no-cache curl \
    && addgroup -S spring && adduser -S spring -G spring

USER spring:spring

HEALTHCHECK --interval=10s --timeout=5s --retries=12 --start-period=60s \
  CMD curl -fsS http://localhost:8080/actuator/health || exit 1

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080

ENV SPRING_PROFILES_ACTIVE=prod

ENTRYPOINT ["java", "-jar", "app.jar"]
