FROM --platform=$BUILDPLATFORM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY pom.xml .
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -B -ntp package -DskipTests

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
RUN addgroup -S library && adduser -S -G library library
COPY --from=build --chown=library:library /workspace/target/library-api-0.0.1-SNAPSHOT.jar app.jar
USER library
EXPOSE 8080
ENTRYPOINT ["java", "-Xmx256m", "-Xms128m", "-jar", "/app/app.jar"]
