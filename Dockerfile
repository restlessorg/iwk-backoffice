FROM eclipse-temurin:25-jdk AS builder

WORKDIR /workspace

COPY gradlew .
COPY gradle gradle
COPY build.gradle.kts settings.gradle.kts ./
COPY src src

RUN chmod +x gradlew
RUN ./gradlew --no-daemon bootJar

FROM eclipse-temurin:25-jdk

WORKDIR /app

RUN addgroup --system spring && adduser --system --ingroup spring spring

COPY --from=builder /workspace/build/libs/*.jar app.jar

USER spring

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
