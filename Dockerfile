FROM eclipse-temurin:17-jdk


# ✅ Copier automatiquement le JAR peu importe son nom
COPY target/*.jar app.jar



EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app.jar"]

