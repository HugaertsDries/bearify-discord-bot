FROM eclipse-temurin:25-jdk AS build
WORKDIR /app
COPY . .
RUN ./gradlew :music-player:agent:bootJar -x test --no-daemon

FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /app/music-player/agent/build/libs/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
