# Etapa 1: se compila el proyecto dentro de Docker con JDK 17 (no hace falta tener Java en el computador)
FROM eclipse-temurin:17-jdk AS build
WORKDIR /workspace

# Primero solo los archivos de Gradle, para que Docker guarde las dependencias descargadas en caché
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies > /dev/null

COPY src src
RUN ./gradlew --no-daemon bootJar

# Etapa 2: imagen más liviana que solo ejecuta el .jar
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /workspace/build/libs/claseJava-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
