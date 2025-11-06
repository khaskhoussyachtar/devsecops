FROM eclipse-temurin:17-jre-jammy

# Install system updates
RUN apt-get update && \
    apt-get upgrade -y && \
    apt-get install -y --no-install-recommends \
        ca-certificates \
        tzdata \
        wget && \
    apt-get clean && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# ✅ Copier automatiquement le JAR peu importe son nom
COPY target/*.jar app.jar

# Run as non-root user
RUN groupadd -r appuser && useradd -r -g appuser appuser
USER appuser

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]

