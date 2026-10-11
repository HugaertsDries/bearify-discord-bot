FROM eclipse-temurin:25-jdk AS build
WORKDIR /app
# fetches Gradle in its own layer, so builds reuse it until the wrapper changes instead of downloading it every deploy
COPY gradlew .
COPY gradle gradle
RUN chmod +x gradlew && ./gradlew --version --no-daemon
COPY . .
RUN chmod +x gradlew
RUN ./gradlew :music-player:agent:bootJar -x test --no-daemon

FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /app/music-player/agent/build/libs/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
