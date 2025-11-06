pipeline {
    agent any

    tools {
        jdk 'JDK17'
        maven 'Maven'
    }

    environment {
        // ✅ Port libre pour l'app
        APP_PORT = "8082"
        
        // URLs des services (sans credentials)
        PROMETHEUS_URL = 'http://192.168.56.10:9090'
        GRAFANA_URL = 'http://192.168.56.10:3000'
    }

    stages {

        /* --------------------------------------------------
           CLONE REPOSITORY
        -------------------------------------------------- */
        stage('Clone Repository') {
            steps {
                retry(3) {
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
                sh 'mvn clean verify'
            }
        }

        /* --------------------------------------------------
           SONARQUBE SAST ANALYSIS
        -------------------------------------------------- */
        stage('SonarQube Analysis') {
            steps {
                withCredentials([string(credentialsId: 'sonar-token', variable: 'SONAR_TOKEN')]) {
                    sh '''
                        mvn sonar:sonar \
                            -Dsonar.projectKey=devsecops \
                            -Dsonar.host.url=http://192.168.56.10:9000 \
                            -Dsonar.login=$SONAR_TOKEN
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
      <username>$NEXUS_USER</username>
      <password>$NEXUS_PASS</password>
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
                    echo "✅ Building Docker image..."
                    docker build -t devsecops-springboot:latest .
                '''
            }
        }

        /* --------------------------------------------------
           RUN APP — PORT 8082
        -------------------------------------------------- */
        stage('Run Container') {
            steps {
                sh '''
                    docker rm -f devsecops-app 2>/dev/null || true
                    echo "✅ Starting container on port ${APP_PORT}..."
                    docker run -d \
                        --name devsecops-app \
                        -p ${APP_PORT}:8080 \
                        devsecops-springboot:latest
                    sleep 10
                '''
            }
        }

        /* --------------------------------------------------
           PROMETHEUS CHECK
        -------------------------------------------------- */
        stage('Prometheus Metrics') {
            steps {
                sh '''
                    echo "📡 Checking /prometheus endpoint..."
                    curl -f -s http://localhost:${APP_PORT}/prometheus > /dev/null
                    if [ $? -eq 0 ]; then
                        echo "✅ /prometheus is reachable"
                    else
                        echo "⚠️ /prometheus not reachable (non-blocking)"
                    fi
                '''
            }
        }

        /* --------------------------------------------------
           GRAFANA DASHBOARD IMPORT
        -------------------------------------------------- */
        stage('Import Grafana Dashboard') {
            steps {
                sh '''
                    echo "📈 Importing Grafana dashboard..."
                    curl -s -X POST \
                        -H "Content-Type: application/json" \
                        -d \'{"dashboard": {"title": "DevSecOps Dashboard"}, "overwrite": true}\' \
                        ${GRAFANA_URL}/api/dashboards/import > /dev/null
                    echo "✅ Dashboard import attempted"
                '''
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
                docker rm -f devsecops-app 2>/dev/null || true
                rm -f settings-temp.xml 2>/dev/null || true
            '''
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
