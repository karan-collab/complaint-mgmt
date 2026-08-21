# Build stage
FROM maven:3.9-eclipse-temurin-11 AS build
WORKDIR /app

COPY pom.xml settings.xml ./
COPY src ./src

RUN mvn -s settings.xml -DskipTests package -q

# Run stage
#
# jammy (Ubuntu), not alpine: Eclipse Temurin publishes no arm64 build of its
# Alpine JRE images, so an alpine base pins this app to x86 hosts. The Ubuntu
# images are published for both architectures, which is what lets the same
# Dockerfile serve an Ampere/Graviton server, a Hetzner CAX box and an Apple
# Silicon laptop.
#
# Costs roughly 90 MB more per image than alpine. Worth it: the image is pulled
# once per deploy, and being tied to one CPU architecture is a far more
# expensive constraint than the download.
FROM eclipse-temurin:11-jre-jammy
WORKDIR /app

# curl is here for the healthcheck below, nothing else. The apt lists are
# removed in the same layer so they do not end up in the image.
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system spring \
    && useradd --system --gid spring --no-create-home spring

USER spring:spring

HEALTHCHECK --interval=10s --timeout=5s --retries=12 --start-period=60s \
  CMD curl -fsS http://localhost:8080/actuator/health || exit 1

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080

ENV SPRING_PROFILES_ACTIVE=prod

# JVM tuning for small containers. The defaults assume a machine with room to
# spare and cost roughly 200 MB more than this app needs.
#
#   MaxRAMPercentage  the JVM sizes its heap from the CONTAINER limit, not the
#                     host, so this adapts automatically to whatever box it
#                     lands on. 60% of a 1 GB container is a 600 MB ceiling;
#                     of a 4 GB one, 2.4 GB.
#   UseSerialGC       G1 keeps per-region metadata and several GC threads. On a
#                     one-core box with a small heap that overhead buys nothing;
#                     the serial collector is both smaller and faster here.
#   Xss512k           thread stacks default to ~2 MB each and this app runs 45+
#                     threads. Spring's stacks are nowhere near 512 KB deep.
#   MaxMetaspaceSize  class metadata is otherwise uncapped, so a leak has no
#                     ceiling and the container gets OOM-killed instead of the
#                     JVM failing loudly.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=60 -XX:+UseSerialGC -Xss512k -XX:MaxMetaspaceSize=128m"


ENTRYPOINT ["java", "-jar", "app.jar"]
