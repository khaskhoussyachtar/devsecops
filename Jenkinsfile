pipeline {
    agent any

    tools {
        jdk 'JDK17'
        maven 'Maven'
    }

    environment {
        // Securely inject credentials for Nexus, SonarQube, Prometheus, and Grafana
        SONAR_TOKEN = credentials('sonarqube-token')
        NEXUS_USER = credentials('nexus-username')
        NEXUS_PASS = credentials('nexus-password')
        PROMETHEUS_URL = 'http://192.168.56.10:9090' // Replace with your Prometheus server IP
        GRAFANA_URL = 'http://192.168.56.10:3000'   // Replace with your Grafana server IP
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
                                -Dsonar.login=\$SONAR_TOKEN \
                                -Dsonar.java.coveragePlugin=jacoco \
                                -Dsonar.coverage.jacoco.xmlReportPaths=target/site/jacoco/jacoco.xml
                        """
                    }
                }
            }
        }

        stage('Deploy to Nexus') {
            steps {
                script {
                    withCredentials([usernamePassword(
                        credentialsId: 'nexus-credentials',
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
                                  <username>\${NEXUS_USER}</username>
                                  <password>\${NEXUS_PASS}</password>
                                </server>
                                <server>
                                  <id>nexus-releases</id>
                                  <username>\${NEXUS_USER}</username>
                                  <password>\${NEXUS_PASS}</password>
                                </server>
                              </servers>
                            </settings>
                        """

                        sh 'mvn deploy -DskipTests -s settings-temp.xml'
                    }
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
                echo '🏗️ Building Docker Image using Docker Buildx...'
                sh 'ls -la'  // Check if Dockerfile exists

                // Ensure Dockerfile exists before proceeding
                sh '''
                    if [ ! -f Dockerfile ]; then
                        echo "ERROR: Dockerfile not found!"
                        exit 1
                    fi
                '''

                // Create or reuse a Buildx builder instance
                sh '''
                    docker buildx inspect mybuilder || docker buildx create --use --name mybuilder
                '''

                // Build the Docker image using Buildx
                sh 'docker buildx build --platform linux/amd64 -t devsecops-springboot:latest .'
            }
        }

        stage('Run with Docker Compose') {
            steps {
                echo '🚀 Starting with Docker Compose...'
                sh 'docker-compose up -d'
            }
        }

        stage('Configure Prometheus Metrics') {
            steps {
                echo '📊 Configuring Prometheus metrics...'
                // Install Prometheus Metrics Plugin in Jenkins (if not already installed)
                sh '''
                    if ! curl -sSL http://localhost:8080/pluginManager/api/json?depth=1 | grep -q 'prometheus'; then
                        echo "Installing Prometheus Metrics Plugin..."
                        curl -X POST http://localhost:8080/pluginManager/installNecessaryPlugins --data '<jenkins><install plugin="prometheus@latest"/></jenkins>' --header 'Content-Type: text/xml'
                        sleep 30  // Wait for plugin installation
                    fi
                '''

                // Restart Jenkins to apply changes
                sh 'sudo systemctl restart jenkins'

                // Verify Prometheus metrics endpoint
                sh 'curl -I http://localhost:8080/prometheus'
            }
        }

        stage('Import Grafana Dashboard') {
            steps {
                echo '📊 Importing Grafana Dashboard...'
                // Import a pre-built dashboard (ID: 9964) for Jenkins monitoring
                sh '''
                    curl -X POST \
                        -H "Content-Type: application/json" \
                        -d '{"dashboard": {"id": null, "uid": null, "title": "Jenkins Monitoring", "panels": []}, "folderId": 0, "overwrite": false}' \
                        ${GRAFANA_URL}/api/dashboards/import
                '''
            }
        }
    }

    post {
        always {
            echo '🧹 Cleanup: Stopping containers...'
            script {
                node {
                    sh 'docker-compose down || true'
                    sh 'rm -f settings-temp.xml || true'
                    cleanWs()
                }
            }
        }

        failure {
            echo '❌ Pipeline failed!'
        }

        success {
            echo '✅ Pipeline succeeded!'
        }
    }
}
