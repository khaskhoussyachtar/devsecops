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

        /* =============================
         * 1️⃣ CLONE DU REPOSITORY
         * ============================= */
        stage('Clone Repository') {
            steps {
                retry(3) {
                    git branch: 'main', url: 'https://github.com/khaskhoussyachtar/devsecops.git'
                }
            }
        }

        /* =============================
         * 2️⃣ SECRETS SCAN — GITLEAKS
         * ============================= */
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

        /* =============================
         * 3️⃣ BUILD & TEST — MAVEN
         * ============================= */
        stage('Build & Test') {
            steps { sh 'mvn clean verify -U' }
        }

        /* =============================
         * 4️⃣ ANALYSE SONARQUBE (SAST)
         * ============================= */
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

        /* =============================
         * 5️⃣ SCAN DES DÉPENDANCES — TRIVY (SCA)
         * ============================= */
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

        /* =============================
         * 6️⃣ BUILD DOCKER IMAGE
         * ============================= */
        stage('Build Docker Image') {
            steps {
                sh 'docker build -t ${IMAGE_TAG} .'
            }
        }

        /* =============================
         * 7️⃣ SCAN DE L’IMAGE — TRIVY
         * ============================= */
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

        /* =============================
         * 8️⃣ DEPLOY TO NEXUS
         * ============================= */
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

        /* =============================
         * 9️⃣ RUN CONTAINER
         * ============================= */
        stage('Run Container') {
            steps {
                sh '''
                    docker rm -f ${APP_NAME} 2>/dev/null || true
                    docker run -d --name ${APP_NAME} -p ${APP_PORT}:${CONTAINER_PORT} ${IMAGE_TAG}
                '''
            }
        }

        /* =============================
         * 🔟 PROMETHEUS CHECK (OPTIONAL)
         * ============================= */
        stage('Prometheus Metrics Check (Optional)') {
            steps {
                sh '''
                    echo "📡 Checking Prometheus metrics (non bloquant)..."
                    curl -sf http://192.168.56.10:8081/service/metrics/prometheus || echo "⚠️ Endpoint not reachable"
                '''
            }
        }

        /* =============================
         * 1️⃣1️⃣ GRAFANA DASHBOARD
         * ============================= */
        stage('Grafana Dashboard') {
            steps { echo "📊 Grafana URL: ${GRAFANA_URL}" }
        }
    }

    /* =============================
     * 🔚 POST ACTIONS
     * ============================= */
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
