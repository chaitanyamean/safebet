FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app
COPY pom.xml /app/pom.xml
COPY betsafe-common /app/betsafe-common
COPY betsafe-gateway /app/betsafe-gateway
RUN apk add --no-cache maven \
    && mvn -pl betsafe-common,betsafe-gateway -am package -DskipTests -q

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/betsafe-gateway/target/*.jar app.jar
EXPOSE 8090
ENTRYPOINT ["java", "-jar", "app.jar"]
