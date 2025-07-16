pipeline {
    agent any

    tools {
        jdk 'JDK17'
        maven 'Maven'
    }

    environment {
        SONAR_TOKEN = credentials('sonarqube-token')
    }

    stages {
        stage('Clone') {
            steps {
                git branch: 'main', url: 'https://github.com/khaskhoussyachtar/devsecops.git'
            }
        }

        stage('Build & Test') {
            steps {
                sh 'mvn clean verify'
            }
        }

        stage('SonarQube Analysis') {
            steps {
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
        }

        stage('Publish Test Results') {
            steps {
                junit '**/target/surefire-reports/*.xml'
            }
        }

        stage('Build Docker Image') {
            steps {
                echo '🏗️ Construction de l’image Docker...'
                sh 'docker build -t devsecops-springboot:latest .'
            }
        }

        stage('Run with Docker Compose') {
            steps {
                echo '🚀 Démarrage avec Docker Compose...'
                sh 'docker-compose up -d'
            }
        }
    }

    post {
        always {
            echo '🧹 Nettoyage : Arrêt des conteneurs Docker'
            sh 'docker-compose down || true'
            sh 'rm -f settings-temp.xml || true'
        }
        failure {
            echo '❌ Pipeline échoué.'
        }
    }
}
