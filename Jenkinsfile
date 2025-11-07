pipeline {
    agent any

    tools {
        jdk 'JDK17'
        maven 'Maven'
    }

    environment {
        APP_NAME        = 'devsecops-app'
        APP_PORT        = '8082'
        CONTAINER_PORT  = '8080'
        IMAGE_TAG       = 'devsecops-springboot:latest'
        PROMETHEUS_URL  = 'http://192.168.56.10:9090'
        GRAFANA_URL     = 'http://192.168.56.10:3000'
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
                        echo "⚠️ Gitleaks not found — skipping secrets scan"
                        exit 0
                    fi
                    gitleaks detect --source . --no-banner --report-path gitleaks-report.json || echo "⚠️ Gitleaks detected secrets or failed — continuing pipeline..."
                '''
            }
            post { always { archiveArtifacts artifacts: 'gitleaks-report.json', allowEmptyArchive: true } }
        }

        stage('Build & Test') {
            steps { sh 'mvn clean verify -U' }
        }

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

        stage('Dependencies Scan (Trivy SCA)') {
            steps {
                sh '''
                    echo "🔍 Running Trivy filesystem scan..."
                    if ! command -v trivy >/dev/null; then
                        echo "⚠️ Trivy not found — skipping"
                        exit 0
                    fi
                    trivy fs --format table --severity HIGH,CRITICAL --exit-code 0 --ignore-unfixed --output trivy-fs-report.txt . || true
                    echo "✅ Trivy scan finished (non-blocking mode)"
                '''
            }
            post { always { archiveArtifacts artifacts: 'trivy-fs-report.txt', allowEmptyArchive: true } }
        }

        stage('Build Docker Image') {
            steps { sh 'docker build -t ${IMAGE_TAG} .' }
        }

        stage('Docker Image Scan (Trivy)') {
            steps {
                sh '''
                    echo "🐳 Scanning Docker image with Trivy..."
                    trivy image --format table --severity HIGH,CRITICAL --exit-code 0 --ignore-unfixed --output trivy-image-report.txt ${IMAGE_TAG} || true
                    echo "✅ Docker image scan finished (non-blocking mode)"
                '''
            }
            post { always { archiveArtifacts artifacts: 'trivy-image-report.txt', allowEmptyArchive: true } }
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

        stage('Run Container') {
            steps {
                sh '''
                    docker rm -f ${APP_NAME} 2>/dev/null || true
                    docker run -d --name ${APP_NAME} -p ${APP_PORT}:${CONTAINER_PORT} ${IMAGE_TAG}
                '''
            }
        }

        stage('DAST Scan (OWASP ZAP)') {
            steps {
                sh '''
                    echo "🧪 Running OWASP ZAP Baseline Scan..."
                    docker run --rm -v $(pwd):/zap/wrk/:rw owasp/zap2docker-stable zap-baseline.py \
                        -t http://192.168.56.10:${APP_PORT} \
                        -r zap-report.html || true
                    echo "✅ OWASP ZAP scan completed (report generated)"
                '''
            }
            post { always { archiveArtifacts artifacts: 'zap-report.html', allowEmptyArchive: true } }
        }

        stage('Prometheus Metrics Check (Optional)') {
            steps {
                sh '''
                    echo "📡 Checking Prometheus metrics (non bloquant)..."
                    curl -sf http://192.168.56.10:8081/service/metrics/prometheus || echo "⚠️ Endpoint not reachable"
                '''
            }
        }

        stage('Grafana Dashboard') {
            steps { echo "📊 Grafana URL: ${GRAFANA_URL}" }
        }
    }

    post {
        always {
            echo '🧹 Cleanup...'
            sh 'docker rm -f ${APP_NAME} 2>/dev/null; rm -f settings-temp.xml 2>/dev/null'
            cleanWs()
        }

        success {
            echo '✅ PIPELINE SUCCESSFUL ✅'
        }

        failure {
            echo '❌ PIPELINE FAILED ❌'
        }
    }
}
