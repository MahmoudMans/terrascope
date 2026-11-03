FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /build

COPY pom.xml ./
COPY src ./src

RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -q -DskipTests package

FROM eclipse-temurin:21-jre-alpine

RUN addgroup -S terrascope && adduser -S terrascope -G terrascope

WORKDIR /app

COPY --from=build /build/target/terrascope-backend-*.jar app.jar

USER terrascope

ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"

EXPOSE 8087

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
