pipeline {
    agent any

    tools {
        jdk 'JDK17'
        maven 'Maven'
    }

    environment {
        SONAR_TOKEN = credentials('sonarqube-token')
        NEXUS = credentials('nexus-credentials') // Un seul credential Username+Password
    }

    stages {
        stage('Clone') {
            steps {
                git branch: 'main', url: 'https://github.com/khaskhoussyachtar/devsecops.git'
            }
        }

        stage('Build and Test') {
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
                    writeFile file: 'settings-temp.xml', text: """
                    <settings xmlns="http://maven.apache.org/SETTINGS/1.0.0"
                              xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                              xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.0.0 https://maven.apache.org/xsd/settings-1.0.0.xsd">
                      <servers>
                        <server>
                          <id>nexus-snapshots</id>
                          <username>${env.NEXUS_USR}</username>
                          <password>${env.NEXUS_PSW}</password>
                        </server>
                        <server>
                          <id>nexus-releases</id>
                          <username>${env.NEXUS_USR}</username>
                          <password>${env.NEXUS_PSW}</password>
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
    }

    post {
        always {
            echo '✅ Pipeline terminé.'
        }
        failure {
            echo '❌ Pipeline échoué.'
        }
    }
}
