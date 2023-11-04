pipeline {

    agent any
/*
	tools {
        maven "maven3"
    }
*/
    environment {
        registry = '154114/vprofile-app'  //dockerhub username/repo-name
        registryCredential = 'dockerhub' // the credential we added for our docker in jenkins configure credentials page
    }

    stages{
        stage('BUILD'){
            steps {
                sh 'mvn clean install -DskipTests'
            }
            post {
                success {
                    echo 'Now Archiving...'
                    archiveArtifacts artifacts: '**/target/*.war'
                }
            }
        }

        stage('UNIT TEST'){
            steps {
                sh 'mvn test'
            }
        }

        stage('INTEGRATION TEST'){
            steps {
                sh 'mvn verify -DskipUnitTests'
            }
        }

        stage ('CODE ANALYSIS WITH CHECKSTYLE'){
            steps {
                sh 'mvn checkstyle:checkstyle'
            }
            post {
                success {
                    echo 'Generated Analysis Result'
                }
            }
        }

        stage('CODE ANALYSIS with SONARQUBE') {
                // remember the tool name must match the name of the tool i.e. sonarQUbe specified in the configuration tools page.
            environment {
                scannerHome = tool 'mysonarscanner4'  // must tally with the SonarQube Scanner name in /manage jenkins > tools page
            }

            steps {
                withSonarQubeEnv('sonar-pro') {  // must tally with the SonarQube Scanner env  in /manage jenkins > configure global settings page
                    sh '''${scannerHome}/bin/sonar-scanner -Dsonar.projectKey=vprofile \
                   -Dsonar.projectName=vprofile-repo \
                   -Dsonar.projectVersion=1.0 \
                   -Dsonar.sources=src/ \
                   -Dsonar.java.binaries=target/test-classes/com/visualpathit/account/controllerTest/ \
                   -Dsonar.junit.reportsPath=target/surefire-reports/ \
                   -Dsonar.jacoco.reportsPath=target/jacoco.exec \
                   -Dsonar.java.checkstyle.reportPaths=target/checkstyle-result.xml'''
                }

                timeout(time: 10, unit: 'MINUTES') {
                    waitForQualityGate abortPipeline: true
                }
            }
        }

        stage("Build App Image") {
            steps {
                script {
                    dockerImage = docker.build registry + ":V$BUILD_NUMBER"
                }
            }
        }

        stage("Upload Image to Docker Hub") {
            steps {
                script {
                    docker.withRegistry('', registryCredential) {
                        dockerImage.push("V$BUILD_NUMBER")
                        dockerImage.push('latest')
                    }
                }
            }
        }
            //this stage is important because as we keep u
        stage("Remove Docker Image") {  
            steps {
                sh "docker rmi $registry:V$BUILD_NUMBER"
            }
        }

        stage("Deploy App To Kubernetes") {
            agent{label 'KOPS'}
            steps {
            sh "helm upgrade --install --force vprofile-stack helm/vprofile-charts --set appimage=${registry}:V${BUILD_NUMBER} --namespace prod"
            }
        }

    }


}
