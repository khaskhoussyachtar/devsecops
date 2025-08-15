pipeline {
    agent any

    tools {
        jdk 'JDK17'
        maven 'Maven'
    }

    environment {
        // SonarQube & Monitoring URLs
        SONAR_TOKEN = credentials('sonarqube-token') // Create this in Jenkins
        PROMETHEUS_URL = 'http://192.168.56.10:9090'
        GRAFANA_URL = 'http://192.168.56.10:3000'
    }

    stages {
        stage('Clone Repository') {
            steps {
                retry(3) {
                    git branch: 'main', url: 'https://github.com/khaskhoussyachtar/devsecops.git'
                }
            }
        }

        stage('Build & Test') {
            steps {
                timeout(time: 10, unit: 'MINUTES') {
                    sh 'mvn clean verify'
                }
            }
        }

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

        stage('Deploy to Nexus') {
            steps {
                withCredentials([usernamePassword(
                    credentialsId: 'nexus-credentials', // Create this with user=admin, pass=admin
                    usernameVariable: 'NEXUS_USER',
                    passwordVariable: 'NEXUS_PASS'
                )]) {
                    writeFile file: 'settings-temp.xml', text: """
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
                    """
                    sh 'mvn deploy -DskipTests -s settings-temp.xml'
                }
            }
        }

        stage('Publish Test Results') {
            steps {
                junit '**/target/surefire-reports/*.xml'
            }
        }

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

        stage('Run with Docker Compose') {
            steps {
                dir("${WORKSPACE}") { // ensure correct directory
                    sh 'docker-compose up -d'
                }
            }
        }

        stage('Configure Prometheus Metrics') {
            steps {
                echo '📊 Configuring Prometheus metrics...'
                sh 'curl -I http://localhost:8080/prometheus || true'
            }
        }

        stage('Import Grafana Dashboard') {
            steps {
                echo '📊 Importing Grafana Dashboard...'
                sh """
                    curl -X POST \
                        -H "Content-Type: application/json" \
                        -d '{"dashboard": {"title": "Jenkins Monitoring", "panels": []}, "folderId": 0, "overwrite": true}' \
                        ${GRAFANA_URL}/api/dashboards/import
                """
            }
        }
    }

    post {
        always {
            echo '🧹 Cleanup: Stopping containers...'
            dir("${WORKSPACE}") {
                sh 'docker-compose down || true'
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
