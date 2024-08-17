def img_tag = "esuat_start.app.1.0.$BUILD_NUMBER"
pipeline {
    agent any
    stages {
        stage('checkout') {
           steps {
                checkout([$class: 'GitSCM', branches: [[name: '*/uat']], userRemoteConfigs: [[url: 'https://git-codecommit.ap-south-1.amazonaws.com/v1/repos/pp-earlysalary',credentialsId: '************']]])
            }
       }

        stage('Build') {
            steps {
               sh 'sudo -S docker build -t pp-earlysalary-uat .'
            }
        }

        stage('ECR push') {
            steps {
               sh 'aws ecr get-login-password --region ap-south-1 --profile UAT | sudo docker login --username AWS --password-stdin ***account_number***.dkr.ecr.ap-south-1.amazonaws.com'
               sh "sudo docker tag ***dockerimagename***:latest ***account_number***.dkr.ecr.ap-south-1.amazonaws.com/***ecr_name***:${img_tag}"
               sh "sudo docker push ***account_number***.dkr.ecr.ap-south-1.amazonaws.com/***ecr_name***:${img_tag}"
            }
        }

        stage('deploy') {
             steps {
                        checkout([$class: 'GitSCM', branches: [[name: '*/master']], userRemoteConfigs: [[credentialsId: 'fac7fd7b-e8a4-406f-bb6e-44fd8ee321b9', url: 'https://git-codecommit.ap-south-1.amazonaws.com/v1/repos/hvpartners']]])
                        sh "sudo echo ${img_tag} > app-tag.txt"
                        sh "sudo sh app.sh"
                        //sh "export AWS_PROFILE=default"
                        sh "aws eks update-kubeconfig --name ***EKS-NAME*** --region ap-south-1 --profile UAT"
                        sh "helm upgrade --install ***envname*** ***appname*** -n ***namespace***"
                    }
        }
        stage("wait for deployment"){
            steps {
                sleep time: 180, unit: 'SECONDS'
            }
        }


        stage("Deployment Status"){
            steps {
                    sh "aws eks update-kubeconfig --name ***EKS-NAME*** --region ap-south-1 --profile UAT"
                    sh "kubectl get pods -n ***namespace***"
            }
        }

    }
}