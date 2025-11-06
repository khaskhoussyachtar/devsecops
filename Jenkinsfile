pipeline {
    agent any

    tools {
        jdk 'JDK17'
        maven 'Maven'
    }

    environment {
        APP_NAME       = 'devsecops-app'
        APP_PORT       = '8082'
        CONTAINER_PORT = '8080'
        IMAGE_TAG      = 'devsecops-springboot:latest'
        PROMETHEUS_URL = 'http://192.168.56.10:9090'
        GRAFANA_URL    = 'http://192.168.56.10:3000'
    }

    stages {

        stage('Clone Repository') {
            steps {
                retry(3) {
                    git branch: 'main', url: 'https://github.com/khaskhoussyachtar/devsecops.git'
                }
            }
        }

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
                always { archiveArtifacts artifacts: 'gitleaks-report.json', allowEmptyArchive: true }
            }
        }

        stage('Build & Test') {
            steps { sh 'mvn clean verify -U' }
        }

        stage('SonarQube Analysis') {
            steps {
                withCredentials([string(credentialsId: 'sonarqube-token', variable: 'SONAR_TOKEN')]) {
                    sh """
                        mvn sonar:sonar \
                            -Dsonar.projectKey=devsecops \
                            -Dsonar.host.url=http://192.168.56.10:9000 \
                            -Dsonar.login=$SONAR_TOKEN \
                            -Dsonar.coverage.exclusions=**/* \
                            -Dsonar.qualitygate.wait=true
                    """
                }
            }
        }

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

        stage('Build Docker Image') {
            steps { sh 'docker build -t ${IMAGE_TAG} .' }
        }

        stage('Run Container') {
            steps {
                sh """
                    docker rm -f ${APP_NAME} 2>/dev/null || true
                    docker run -d --name ${APP_NAME} -p ${APP_PORT}:${CONTAINER_PORT} ${IMAGE_TAG}
                """
            }
        }

        stage('Prometheus Metrics Check') {
            steps {
                script {
                    def endpoints = [
                        "http://localhost:${APP_PORT}/actuator/prometheus",
                        "http://192.168.56.10:8081/service/metrics/prometheus"
                    ]
                    def success = false
                    def attempts = 3
                    def interval = 5

                    for (int i = 0; i < attempts; i++) {
                        echo "📡 Attempt ${i+1}/${attempts} — checking endpoints..."
                        for (endpoint in endpoints) {
                            echo "  → ${endpoint}"
                            def code = sh(script: "curl -sf ${endpoint} > /dev/null 2>&1; echo \$?", returnStdout: true).trim()
                            if (code == "0") {
                                echo "✅ Success: ${endpoint} responded"
                                success = true
                            } else {
                                echo "❌ Failed: ${endpoint} did not respond"
                            }
                        }
                        if (success) break
                        sleep interval
                    }

                    if (!success) {
                        echo "⚠️ None of the Prometheus endpoints are reachable"
                        currentBuild.result = 'UNSTABLE'
                    }
                }
            }
        }

        stage('Prometheus Scrape Validation') {
            steps {
                script {
                    echo "🔍 Verifying Prometheus scrape target status..."
                    def healthStatus = sh(
                        script: "curl -sf ${PROMETHEUS_URL}/api/v1/targets | jq -r '.data.activeTargets[] | select(.scrapeUrl | contains(\":${APP_PORT}\")) | .health'",
                        returnStdout: true
                    ).trim()

                    if (healthStatus == "up") {
                        echo "✅ Prometheus is scraping the app successfully"
                    } else {
                        echo "⚠️ Prometheus scrape issue"
                        currentBuild.result = 'UNSTABLE'
                    }
                }
            }
        }

        stage('Grafana Dashboard') {
            steps {
                echo "📊 Grafana URL: ${GRAFANA_URL}"
            }
        }
    }

    post {
        always {
            echo '🧹 Cleanup...'
            sh 'docker rm -f ${APP_NAME} 2>/dev/null; rm -f settings-temp.xml 2>/dev/null'
            cleanWs()
        }
        success { echo '✅ PIPELINE SUCCESSFUL ✅' }
        unstable { echo '⚠️ PIPELINE COMPLETED WITH WARNINGS' }
        failure { echo '❌ PIPELINE FAILED ❌' }
    }
}
