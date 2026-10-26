# ---------- build ----------
FROM eclipse-temurin:27-jdk-alpine AS build
WORKDIR /app

# Dependências em camada separada (cache entre builds quando só o código muda)
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN chmod +x mvnw && ./mvnw -q -B dependency:go-offline

COPY src src
RUN ./mvnw -q -B package -DskipTests && cp target/orangesentinel-*.jar app.jar

# ---------- runtime ----------
FROM eclipse-temurin:27-jre-alpine
WORKDIR /app
RUN addgroup -S app && adduser -S app -G app
COPY --from=build /app/app.jar app.jar
USER app

ENV SPRING_PROFILES_ACTIVE=prod \
    JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"

# O Render injeta a variável PORT; a aplicação usa server.port=${PORT:8080}
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
