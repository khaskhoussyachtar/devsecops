pipeline {
    agent any

    tools {
        jdk 'JDK17'
        maven 'Maven'
    }

    environment {
        SONAR_TOKEN = credentials('sonarqube-token')
        PROMETHEUS_URL = 'http://192.168.56.10:9090'
        GRAFANA_URL = 'http://192.168.56.10:3000'
    }

    stages {

        /* --------------------------------------------------
           CLONE DU REPO
        -------------------------------------------------- */
        stage('Clone Repository') {
            steps {
                retry(3) {
                    git branch: 'main', url: 'https://github.com/khaskhoussyachtar/devsecops.git'
                }
            }
        }

        /* --------------------------------------------------
           ✅ DEVSECOPS — SCAN DES SECRETS (GITLEAKS)
        -------------------------------------------------- */
        stage('Secrets Scan') {
            steps {
                sh 'gitleaks detect --source . --no-banner --report-path gitleaks-report.json || true'
            }
            post {
                always {
                    archiveArtifacts artifacts: 'gitleaks-report.json', fingerprint: true
                }
                unsuccessful {
                    error "❌ Des secrets ont été détectés par Gitleaks !"
                }
            }
        }

        /* --------------------------------------------------
           BUILD & TEST
        -------------------------------------------------- */
        stage('Build & Test') {
            steps {
                timeout(time: 10, unit: 'MINUTES') {
                    sh 'mvn clean verify'
                }
            }
        }

        /* --------------------------------------------------
           ANALYSE SONARQUBE (SAST)
        -------------------------------------------------- */
        stage('SonarQube Analysis') {
            steps {
                timeout(time: 5, unit: 'MINUTES') {
                    withSonarQubeEnv('SonarQube') {
                        sh """
                            mvn sonar:sonar \
                                -Dsonar.projectKey=devsecops \
                                -Dsonar.host.url=http://localhost:9000 \
                                -Dsonar.login=${SONAR_TOKEN} \
                                -Dsonar.java.coveragePlugin=jacoco \
                                -Dsonar.coverage.jacoco.xmlReportPaths=target/site/jacoco/jacoco.xml
                        """
                    }
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
                    writeFile file: 'settings-temp.xml', text: """<?xml version="1.0" encoding="UTF-8"?>
<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0"
          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
          xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.0.0 https://maven.apache.org/xsd/settings-1.0.0.xsd">
  <servers>
    <server>
      <id>nexus-snapshots</id>
      <username>${NEXUS_USER}</username>
      <password>${NEXUS_PASS}</password>
    </server>
    <server>
      <id>nexus-releases</id>
      <username>${NEXUS_USER}</username>
      <password>${NEXUS_PASS}</password>
    </server>
  </servers>
</settings>
                    """.stripIndent()
                    sh 'mvn deploy -DskipTests -s settings-temp.xml'
                }
            }
        }

        /* --------------------------------------------------
           TEST RESULTS
        -------------------------------------------------- */
        stage('Publish Test Results') {
            steps {
                junit '**/target/surefire-reports/*.xml'
            }
        }

        /* --------------------------------------------------
           DOCKER BUILD
        -------------------------------------------------- */
        stage('Build Docker Image') {
            steps {
                echo '🏗️ Building Docker Image...'
                sh '''
                    if [ ! -f Dockerfile ]; then
                        echo "ERROR: Dockerfile not found!"
                        exit 1
                    fi
                    docker buildx inspect mybuilder || docker buildx create --use --name mybuilder
                    docker buildx build --platform linux/amd64 -t devsecops-springboot:latest .
                '''
            }
        }

        /* --------------------------------------------------
           DOCKER COMPOSE
        -------------------------------------------------- */
        stage('Run with Docker Compose') {
            steps {
                dir("${WORKSPACE}") {
                    sh 'docker-compose up -d'
                }
            }
        }

        /* --------------------------------------------------
           PROMETHEUS
        -------------------------------------------------- */
        stage('Configure Prometheus Metrics') {
            steps {
                sh 'curl -I http://localhost:8080/prometheus || true'
            }
        }

        /* --------------------------------------------------
           GRAFANA
        -------------------------------------------------- */
        stage('Import Grafana Dashboard') {
            steps {
                sh """
                    curl -X POST \
                        -H "Content-Type: application/json" \
                        -d '{"dashboard": {"title": "Jenkins Monitoring", "panels": []}, "folderId": 0, "overwrite": true}' \
                        ${GRAFANA_URL}/api/dashboards/import
                """
            }
        }
    }

    /* --------------------------------------------------
       POST ACTIONS
    -------------------------------------------------- */
    post {
        always {
            echo '🧹 Cleanup: Stopping containers...'
            dir("${WORKSPACE}") {
                sh 'docker-compose down --remove-orphans || true'
            }
            sh 'rm -f settings-temp.xml || true'
            cleanWs()
        }
        failure {
            echo '❌ Pipeline failed!'
        }
        success {
            echo '✅ Pipeline succeeded!'
        }
    }
}
