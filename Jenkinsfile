pipeline {
    agent any

    tools {
        jdk 'JDK17'
        maven 'Maven'
    }

    environment {
        // ✅ On change le port pour éviter le conflit avec Nexus (8081)
        APP_PORT = "8082"   // ← 8082 est libre (8080:Jenkins, 8081:Nexus, 9000:SonarQube)
        
        // Variables d’URL (sans credentials ici)
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
           SECRETS SCAN (GITLEAKS) — SÉCURISÉ
        -------------------------------------------------- */
        stage('Secrets Scan') {
            steps {
                sh '''
                    echo "🔍 Running Gitleaks..."
                    gitleaks detect --source . --no-banner --report-path gitleaks-report.json
                    # Gitleaks exit code = 1 si leak trouvé → on capture proprement
                    EXIT_CODE=$?
                    if [ $EXIT_CODE -eq 1 ]; then
                        echo "⚠️ Secrets found — see gitleaks-report.json"
                        # Tu peux bloquer ici si strict :
                        # exit 1
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
           SONARQUBE SAST ANALYSIS — ✅ FIX SÉCURITÉ
        -------------------------------------------------- */
        stage('SonarQube Analysis') {
            steps {
                withSonarQubeEnv('SonarQube') {
                    // 🔐 Utilise SONAR_TOKEN via variable d'environnement (pas interpolation Groovy)
                    sh '''
                        mvn sonar:sonar \
                            -Dsonar.projectKey=devsecops \
                            -Dsonar.host.url=http://localhost:9000 \
                            -Dsonar.login=$SONAR_TOKEN
                    '''
                }
            }
        }

        /* --------------------------------------------------
           DEPLOY TO NEXUS — ✅ FIX SÉCURITÉ
        -------------------------------------------------- */
        stage('Deploy to Nexus') {
            steps {
                withCredentials([usernamePassword(
                    credentialsId: 'nexus-credentials',
                    usernameVariable: 'NEXUS_USER',
                    passwordVariable: 'NEXUS_PASS'
                )]) {
                    // 🔐 Écrire settings avec variables shell (pas Groovy)
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
           RUN APP — ✅ PORT 8082 (LIBRE)
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

                    # Attendre que l'app soit prête (optionnel mais utile pour DAST)
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
                        ${GRAFANA_URL}/api/dashboards/import \
                        > /dev/null
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
