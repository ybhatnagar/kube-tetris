# syntax=docker/dockerfile:1

# --- builder: compile the fat jar ---
FROM eclipse-temurin:21-jdk AS builder
WORKDIR /src

# Prime the dependency cache first — the layer only invalidates when the Gradle
# wrapper or the module build files change, not on every source edit.
COPY gradlew gradlew.bat settings.gradle ./
COPY gradle gradle
COPY engine/build.gradle engine/
COPY collector/build.gradle collector/
COPY executor/build.gradle executor/
COPY api/build.gradle api/
RUN ./gradlew :api:dependencies --no-daemon --console=plain || true

# Now copy sources and build.
COPY engine engine
COPY collector collector
COPY executor executor
COPY api api
COPY ui ui
RUN ./gradlew :api:bootJar --no-daemon --console=plain -x test

# --- runtime: slim JRE + the jar ---
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN groupadd -r kubetetris && useradd -r -u 10001 -g kubetetris kubetetris
COPY --from=builder --chown=10001:10001 /src/api/build/libs/api-0.1.0-SNAPSHOT.jar /app/api.jar
USER 10001
EXPOSE 8080
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75"
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/api.jar"]
