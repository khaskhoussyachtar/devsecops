pipeline {
    agent any

    tools {
        jdk 'JDK17'
        maven 'Maven'
    }

    environment {
        SONAR_TOKEN = credentials('sonarqube-token')
        NEXUS_USER = credentials('nexus-username')
        NEXUS_PASS = credentials('nexus-password')
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
                    // Créer un settings.xml temporaire avec credentials
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
                    // Déployer avec le fichier temporaire
                    sh 'mvn deploy -DskipTests -s settings-temp.xml'
                }
            }
        }

        stage('Publish Test Results') {
            steps {
                junit '**/target/surefire-reports/*.xml'
            }
        }
    }

    post {
        always {
            echo '✅ Pipeline terminé.'
            sh 'rm -f settings-temp.xml || true'
        }
        failure {
            echo '❌ Pipeline échoué.'
        }
    }
}
