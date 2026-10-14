FROM eclipse-temurin:25-jdk AS build
WORKDIR /app
# fetches Gradle in its own layer, so builds reuse it until the wrapper changes instead of downloading it every deploy
COPY gradlew .
COPY gradle/wrapper gradle/wrapper
RUN ./gradlew --version --no-daemon
COPY . .
RUN ./gradlew :controller:bootJar -x test --no-daemon

FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /app/controller/build/libs/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
