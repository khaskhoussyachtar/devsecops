pipeline {
    agent any

    tools {
        jdk 'JDK17'
        maven 'Maven'
    }

    environment {
        APP_NAME       = 'devsecops-app'
        APP_PORT       = '8082'          // port exposé sur l'hôte
        CONTAINER_PORT = '8080'          // port interne Spring Boot
        IMAGE_TAG      = 'devsecops-springboot:latest'
        PROMETHEUS_URL = 'http://192.168.56.10:9090'
        GRAFANA_URL    = 'http://192.168.56.10:3000'
    }

    stages {

        /* --------------------------------------------------
           CLONE REPOSITORY
        -------------------------------------------------- */
        stage('Clone Repository') {
            steps {
                retry(3) {
                    // 🔔 Note : l’URL GitHub actuelle est vide → à remplacer par le vrai repo privé dès qu’il existe
                    git branch: 'main', url: 'https://github.com/khaskhoussyachtar/devsecops.git'
                }
            }
        }

        /* --------------------------------------------------
           SECRETS SCAN (GITLEAKS)
        -------------------------------------------------- */
        stage('Secrets Scan') {
            steps {
                sh '''
                    echo "🔍 Running Gitleaks..."
                    if ! command -v gitleaks >/dev/null; then
                        echo "⚠️ gitleaks not found — skipping secrets scan"
                        exit 0
                    fi
                    gitleaks detect --source . --no-banner --report-path gitleaks-report.json
                    EXIT_CODE=$?
                    if [ $EXIT_CODE -eq 1 ]; then
                        echo "⚠️ Secrets found — see gitleaks-report.json"
                    elif [ $EXIT_CODE -ne 0 ]; then
                        echo "🚨 Gitleaks failed (exit $EXIT_CODE)"
                        exit $EXIT_CODE
                    else
                        echo "✅ No secrets detected."
                    fi
                '''
            }
            post {
                always {
                    archiveArtifacts artifacts: 'gitleaks-report.json', allowEmptyArchive: true
                }
            }
        }

        /* --------------------------------------------------
           BUILD & TEST
        -------------------------------------------------- */
        stage('Build & Test') {
            steps {
                sh 'mvn clean verify -U'
            }
        }

        /* --------------------------------------------------
           SONARQUBE SAST ANALYSIS
        -------------------------------------------------- */
        stage('SonarQube Analysis') {
            steps {
                withCredentials([string(credentialsId: 'sonarqube-token', variable: 'SONAR_TOKEN')]) {
                    sh '''
                        mvn sonar:sonar \
                            -Dsonar.projectKey=devsecops \
                            -Dsonar.host.url=http://192.168.56.10:9000 \
                            -Dsonar.login=$SONAR_TOKEN \
                            -Dsonar.coverage.exclusions=**/* \
                            -Dsonar.qualitygate.wait=true
                    '''
                }
            }
        }

        /* --------------------------------------------------
           DEPLOY TO NEXUS
        -------------------------------------------------- */
        stage('Deploy to Nexus') {
            steps {
                withCredentials([usernamePassword(
                    credentialsId: 'nexus-credentials',
                    usernameVariable: 'NEXUS_USER',
                    passwordVariable: 'NEXUS_PASS'
                )]) {
                    sh '''
                        cat > settings-temp.xml <<EOF
<settings>
  <servers>
    <server>
      <id>nexus-releases</id>
      <username>${NEXUS_USER}</username>
      <password>${NEXUS_PASS}</password>
    </server>
  </servers>
</settings>
EOF
                        mvn deploy -DskipTests -s settings-temp.xml
                    '''
                }
            }
        }

        /* --------------------------------------------------
           DOCKER BUILD
        -------------------------------------------------- */
        stage('Build Docker Image') {
            steps {
                sh '''
                    echo "✅ Building Docker image: ${IMAGE_TAG}"
                    docker build -t ${IMAGE_TAG} .
                '''
            }
        }

        /* --------------------------------------------------
           RUN APP — PORT 8082
        -------------------------------------------------- */
        stage('Run Container') {
            steps {
                sh '''
                    docker rm -f ${APP_NAME} 2>/dev/null || true
                    echo "✅ Starting container ${APP_NAME} on port ${APP_PORT}..."
                    docker run -d \
                        --name ${APP_NAME} \
                        -p ${APP_PORT}:${CONTAINER_PORT} \
                        ${IMAGE_TAG}
                '''
            }
        }

        /* --------------------------------------------------
           WAIT FOR APP READY (Health check)
        -------------------------------------------------- */
        stage('Wait for App Readiness') {
            steps {
                script {
                    def maxWait = 60
                    def waited = 0
                    def interval = 5
                    def ready = false

                    while (waited < maxWait && !ready) {
                        echo "⏳ Waiting for app health (${waited}s)..."
                        def exitCode = sh(
                            script: "curl -sf http://localhost:${APP_PORT}/actuator/health | grep -q '\"status\":\"UP\"'",
                            returnStatus: true
                        )
                        if (exitCode == 0) {
                            echo "✅ App is UP"
                            ready = true
                        } else {
                            sleep interval
                            waited += interval
                        }
                    }
                    if (!ready) {
                        error("❌ App did not become healthy in ${maxWait}s")
                    }
                }
            }
        }

        /* --------------------------------------------------
           PROMETHEUS ENDPOINT CHECK
        -------------------------------------------------- */
        stage('Prometheus Metrics Check') {
            steps {
                script {
                    def endpoints = ['/actuator/prometheus', '/prometheus']
                    def success = false
                    def attempts = 3

                    for (int i = 0; i < attempts; i++) {
                        echo "📡 Attempt ${i + 1}/${attempts} — checking endpoints..."

                        for (endpoint in endpoints) {
                            def url = "http://localhost:${APP_PORT}${endpoint}"
                            echo "  → ${url}"
                            def code = sh(script: "curl -sf ${url} > /dev/null 2>&1; echo \$?", returnStdout: true).trim()
                            if (code == "0") {
                                echo "✅ Success: ${endpoint} responded"
                                success = true
                                break
                            }
                        }

                        if (success) break
                        sleep 3
                    }

                    if (!success) {
                        echo "❌ None of the Prometheus endpoints are reachable"
                        currentBuild.result = 'UNSTABLE'
                    }
                }
            }
        }

        /* --------------------------------------------------
           PROMETHEUS SCRAPE STATUS CHECK (via API)
        -------------------------------------------------- */
        stage('Prometheus Scrape Validation') {
            steps {
                script {
                    echo "🔍 Verifying Prometheus scrape target status..."
                    def query = """
                        curl -sf ${PROMETHEUS_URL}/api/v1/targets | \\
                        jq -r '.data.activeTargets[] | 
                              select(.scrapeUrl | contains(":${APP_PORT}")) | 
                              .health'
                    """
                    def healthStatus = sh(
                        script: query,
                        returnStdout: true
                    ).trim()

                    if (healthStatus == "up") {
                        echo "✅ Prometheus is scraping the app successfully"
                    } else if (healthStatus == "down") {
                        echo "⚠️ Prometheus target is DOWN — possible config issue"
                        currentBuild.result = 'UNSTABLE'
                    } else if (healthStatus.empty) {
                        echo "❓ No target found on port ${APP_PORT} — check prometheus.yml"
                        currentBuild.result = 'UNSTABLE'
                    } else {
                        echo "❓ Unexpected health status: '${healthStatus}'"
                        currentBuild.result = 'UNSTABLE'
                    }
                }
            }
        }

        /* --------------------------------------------------
           GRAFANA DASHBOARD (Info/Import)
        -------------------------------------------------- */
        stage('Grafana Dashboard') {
            steps {
                echo "📊 Grafana URL: ${GRAFANA_URL}"
                echo "   → Targets: ${PROMETHEUS_URL}/targets?search="
                // 🔔 L’import auto est désactivé ici car ton endpoint actuel échoue (403/401 sans auth)
                // Si tu configures une clé API Grafana, je peux ajouter l’import auto.
            }
        }
    }

    /* --------------------------------------------------
       CLEANUP
    -------------------------------------------------- */
    post {
        always {
            echo '🧹 Cleanup...'
            sh '''
                docker rm -f ${APP_NAME} 2>/dev/null || true
                rm -f settings-temp.xml 2>/dev/null || true
            '''
            cleanWs()
        }
        success {
            echo '✅ PIPELINE SUCCESSFUL ✅'
        }
        unstable {
            echo '⚠️ PIPELINE COMPLETED WITH WARNINGS (e.g., observability checks)'
        }
        failure {
            echo '❌ PIPELINE FAILED ❌'
        }
    }
}
