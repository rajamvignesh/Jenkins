def img_tag = "uat.app.1.0.$BUILD_NUMBER"
node {

    echo "I'll go through the script for the discipline. I'm all set to read the pipeline script"

    echo "JiraIssueKey"

    echo "***************DEVOPS********************************"

        stage('checkout') {
           try {
            jiraAddComment site: 'vivegam.atlassian.net', idOrKey: "$JiraIssueKey", comment: 'EKS DEPLOYMENT STARTED'

                   checkout([$class: 'GitSCM', branches: [[name: '*/uat']], userRemoteConfigs: [[url: 'https://git-codecommit.ap-south-1.amazonaws.com/v1/repos/********',credentialsId: '***************']]])
           }
           catch (Exception ex) {
            jiraAddComment site: 'vivegam.atlassian.net', idOrKey: "$JiraIssueKey", comment:'Unable to checkout the source code, please contact the devops team.'
            jiraTransitionIssue site: 'vivegam.atlassian.net', idOrKey: "$JiraIssueKey", input: [transition: [id: '161' ]]
            error 'Unable to checkout the source code, kindly verify the code'
            }
}

        stage('Build') {
            try {
            
                sh 'sudo -S docker build -t docker_name .'
            }
            catch (Exception ex) {
            jiraAddComment site: 'vivegam.atlassian.net', idOrKey: "$JiraIssueKey", comment: 'Build failed, please contact the devops team.'
            jiraTransitionIssue site: 'vivegam.atlassian.net', idOrKey: "$JiraIssueKey", input: [transition: [id: '161' ]]
            error 'Build failed, kindly verify Docker file'
            }
        }

        stage('ECR push') {
            try {
               sh 'aws ecr get-login-password --region ap-south-1 --profile UAT | sudo docker login --username AWS --password-stdin ***account_number***.dkr.ecr.ap-south-1.amazonaws.com'
               sh "sudo docker tag ***dockerimagename***:latest ***account_number***.dkr.ecr.ap-south-1.amazonaws.com/***ecr_name***:${img_tag}"
               sh "sudo docker push ***account_number***.dkr.ecr.ap-south-1.amazonaws.com/***ecr_name***:${img_tag}"
            }
            catch (Exception ex) {
            jiraAddComment site: 'vivegam.atlassian.net', idOrKey: "$JiraIssueKey", comment: 'ECR push Failed, please contact the devops team.'
            jiraTransitionIssue site: 'vivegam.atlassian.net', idOrKey: "$JiraIssueKey", input: [transition: [id: '161' ]]
            error 'ECR NOT FOUND, kindly verify ECR REPO'
            }
            
        }

        stage('deploy') {
             try {
                        checkout([$class: 'GitSCM', branches: [[name: '*/master']], userRemoteConfigs: [[credentialsId: 'fac7fd7b-e8a4-406f-bb6e-44fd8ee321b9', url: 'https://git-codecommit.ap-south-1.amazonaws.com/v1/repos/helm_earlysalary']]])
                        sh "sudo echo ${img_tag} > app-tag.txt"
                        sh "sudo sh app.sh"
                        //sh "export AWS_PROFILE=default"
                        sh "aws eks update-kubeconfig --name UAT-CP-EKS --region ap-south-1 --profile UAT"
                        sh "helm upgrade --install ***envname*** ***appname*** -n ***namespace***"
                    }
                    catch (Exception ex) {
            jiraAddComment site: 'vivegam.atlassian.net', idOrKey: "$JiraIssueKey", comment: 'EKS Deployment failed'
            jiraTransitionIssue site: 'vivegam.atlassian.net', idOrKey: "$JiraIssueKey", input: [transition: [id: '161' ]]
            error 'EKS DEPLOYEMENT FAILED kindly look on deployment file'
            }
        }
        stage("wait for deployment"){
            try {
                sleep time: 180, unit: 'SECONDS'
            }
            catch (Exception ex) {
            jiraAddComment site: 'vivegam.atlassian.net', idOrKey: "$JiraIssueKey", comment:'Unable to checkout the source code, please contact the devops team.'
            jiraTransitionIssue site: 'vivegam.atlassian.net', idOrKey: "$JiraIssueKey", input: [transition: [id: '161' ]]
            error 'Unable to do wait for deployment, kindly verify the file'
            }
        }

        stage("Deployment Status"){
            try {
                 script {
                        deploy_status = sh (
                        script: 'kubectl get pods -n ***namespace*** --no-headers -o custom-columns=\":status.phase\"',
                        returnStdout: true).trim()
                        echo "deployment status: ${deploy_status}" 
                    if (deploy_status == 'Running') {
                        echo 'pod deployed successfully'
                        deploy_id = sh (
                        script: 'kubectl get pods -n ***namespace*** --no-headers -o custom-columns=\":metadata.name\"',
                        returnStdout: true).trim()
                        echo "deployment id: ${deploy_id}"
                        deploy_logs = sh ( script: 'kubectl logs --tail=50 '+deploy_id+' -n ***namespace***',returnStdout: true).trim()
                        echo "deployment logs: ${deploy_logs}"
                        writeFile file: "$JiraIssueKey"+'.log', text: "${deploy_logs}"
                        echo "$JiraIssueKey"+'.log'
                        sh 'ls -l "$JiraIssueKey"'+'.log'
                        sh 'cat "$JiraIssueKey"'+'.log'
                        jiraUploadAttachment site: 'vivegam.atlassian.net', idOrKey: "$JiraIssueKey", file: "$JiraIssueKey"+'.log'
                       jiraAddComment site: 'vivegam.atlassian.net', idOrKey: "$JiraIssueKey", comment: 'EKS Deployment done successfully. kindly check with log attached in the ticket'
                       jiraTransitionIssue site: 'vivegam.atlassian.net', idOrKey: "$JiraIssueKey", input: [transition: [id: '51' ]]
                    } else {
                        jiraAddComment site: 'vivegam.atlassian.net', idOrKey: "$JiraIssueKey", comment: 'EKS Deployment running. kindly check with team'
                        jiraTransitionIssue site: 'vivegam.atlassian.net', idOrKey: "$JiraIssueKey", input: [transition: [id: '51' ]]
                    }
                }
            }
            catch (Exception ex) {
            jiraAddComment site: 'vivegam.atlassian.net', idOrKey: "$JiraIssueKey", comment:'Unable to checkout the source code, please contact the devops team.'
            jiraTransitionIssue site: 'vivegam.atlassian.net', idOrKey: "$JiraIssueKey", input: [transition: [id: '161' ]]
            error 'Unable to do Deployment Status, kindly verify the file'
        }
}
}       
pipeline {
     agent any
     stages {
         stage('BuildInfo') {
             steps {
                 echo 'Building...'
             }
             post {
                 always {
                     jiraSendBuildInfo site: 'vivegam.atlassian.net', branch: "$JiraIssueKey"
                 }
             }
         }
         }
}