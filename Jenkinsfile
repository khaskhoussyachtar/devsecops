pipeline {
    agent any

    tools {
        jdk 'JDK17'
        maven 'Maven'
    }

    environment {
        // Use credentials binding instead of direct string interpolation
        SONAR_TOKEN = credentials('sonarqube-token')
        NEXUS_USER = credentials('nexus-credentials').username
        NEXUS_PASS = credentials('nexus-credentials').password
    }

    stages {
        stage('Clone Repository') {
            steps {
                retry(3) {
                    git branch: 'main', url: 'https://github.com/khaskhoussyachtar/devsecops.git '
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
                    writeFile file: 'settings-temp.xml', text: """
                        <settings xmlns="http://maven.apache.org/SETTINGS/1.0.0"
                                  xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                                  xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.0.0 https://maven.apache.org/xsd/settings-1.0.0.xsd ">
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

        stage('Publish Test Results') {
            steps {
                junit '**/target/surefire-reports/*.xml'
            }
        }

        stage('Build Docker Image') {
            steps {
                echo '🏗️ Building Docker image using Docker Buildx...'
                sh 'ls -la'  // Confirm presence of Dockerfile

                // Ensure Dockerfile exists
                sh '''
                    if [ ! -f Dockerfile ]; then
                        echo "ERROR: Dockerfile not found!"
                        exit 1
                    fi
                '''

                // Create or reuse a Buildx builder
                sh '''
                    docker buildx inspect mybuilder || docker buildx create --use --name mybuilder
                '''

                // Build the image using Buildx
                sh 'docker buildx build --platform linux/amd64 -t devsecops-springboot:latest .'
            }
        }

        stage('Run with Docker Compose') {
            steps {
                echo '🚀 Starting with Docker Compose...'
                sh 'docker-compose up -d'
            }
        }
    }

    post {
        always {
            echo '🧹 Cleanup: Stopping containers...'
            sh 'docker-compose down || true'
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
