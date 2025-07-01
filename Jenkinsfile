pipeline {
    agent any

    stages {
        stage('Clone') {
            steps {
                git 'https://github.com/khaskhoussyachtar/devsecops.git '
                echo "Code récupéré depuis Git"
            }
        }

        stage('Afficher la date') {
            steps {
                sh 'echo Date système : $(date)'
            }
        }

        stage('Build Java') {
            steps {
                sh 'javac App.java'
                sh 'java App'
            }
        }
    }

    post {
        success {
            echo '✅ Build réussi !'
        }
        failure {
            echo '❌ Le build a échoué.'
        }
    }
}
pipeline {
    agent any
    stages {
        stage('Hello') {
            steps {
                echo 'Hello from Jenkins!'
                sh 'date'
            }
        }
    }
}
