// vars/pipelineCall.groovy
def call(Map config) {

    node {
		def removePrefix 
		def mvnMap = ['mvnHome': '/data/jenkins/apache-maven', 'mvnRemovePrefix': "${config.removePrefix}"] 
		def antMap = ['antHome': '/data/jenkins/apache-ant', 'antRemovePrefix': 'svn/build'] 
		def proMap = ['serverName': "${config.serverName}", 'report': '', 'job': ''] //定义项目字典
        def sqlMap = ['execCommand': '/data/.jenkins/sql/preExecSql.sh'] 
		def publisherMap = ['execCommand': "${config.publisherCommand}", 'execTimeout': '500000', 'sourceFiles': "${config.sourceFiles}"] //定义发布脚本
        def dingMap = ['accessToken': 'c1c0fcb4-51d3-4772-9f27-8f1f72319605', 'jenkinsUrl': 'http://10.15.22.14:8080/jenkins/']
		
        try {
            if (config.buildType == 'sql') {
                stage('代码获取') { // for display purposes
                    // Get some code from a SubversionSCM repository
                    checkout([$class: 'SubversionSCM', additionalCredentials: [], excludedCommitMessages: '', excludedRegions: '', excludedRevprop: '', excludedUsers: '', filterChangelog: false, ignoreDirPropChanges: false, includedRegions: '', locations: [[cancelProcessOnExternalsFail: true, credentialsId: "${config.credentialsId}", depthOption: 'infinity', ignoreExternalsOption: true, local: 'svn/', remote: "${config.codePath}"]], quietOperation: true, workspaceUpdater: [$class: 'UpdateUpdater']])
                }

                stage('构建') {
                    sh label: '',
                    script: "sh ${sqlMap.execCommand} ${WORKSPACE}" // Run the build
                    withEnv(['"ANT_HOME=${antMap.antHome}"']) {
                        if (config.buildTool == 'ant') {
                            if (isUnix()) {
                                sh '"$ANT_HOME/bin/ant" -f svn/build.xml'
                            } else {
                                bat(/"%ANT_HOME%\bin\ant" /)
                            }
                        } else {
                            echo "Undefined build tool ......"
                        }
                    }
                }
            } else {
                stage('清理本地仓库') {
                    //sh "/home/jenkins/del_lastUpdated.sh"
                    sh("ls -al ${env.WORKSPACE}") 
					deleteDir() // clean up current work directory
                    sh("ls -al ${env.WORKSPACE}")
                }
				
                stage('代码获取') { // for display purposes
                    if (config.codeManageType == 'svn') {
                        // Get some code from a SubversionSCM repository
                        checkout([$class: 'SubversionSCM', additionalCredentials: [], excludedCommitMessages: '', excludedRegions: '', excludedRevprop: '', excludedUsers: '', filterChangelog: false, ignoreDirPropChanges: false, includedRegions: '', locations: [[cancelProcessOnExternalsFail: true, credentialsId: "${config.credentialsId}", depthOption: 'infinity', ignoreExternalsOption: true, local: 'svn/', remote: "${config.codePath}"]], quietOperation: true, workspaceUpdater: [$class: 'UpdateUpdater']])
                    } else if (config.codeManageType == 'git') {
                        // Get some code from a Git repository
                        git credentialsId: "${config.credentialsId}",
                        url: "${config.codePath}"
                    } else {
                        echo "Undefined codeManage tool ......"
                    }
                }

                stage('构建') {
                    // Run the build
                    withEnv(["MVN_HOME=${mvnMap.mvnHome}", "ANT_HOME=${antMap.antHome}"]) {
                        if (config.buildTool == 'maven') {
                            echo "starting build with maven......"
                            if (isUnix()) {
                                sh '"$MVN_HOME/bin/mvn" -f./svn/pom.xml clean package sonar:sonar -Dmaven.test.skip=true'
                            } else {
                                bat(/"%MVN_HOME%\bin\mvn" -Dmaven.test.skip=true clean package/)
                            }
                            removePrefix = mvnMap.mvnRemovePrefix
                        } else if (config.buildTool == 'ant') {
                            echo "starting build with ant......"
                            if (isUnix()) {
                                sh '"$ANT_HOME/bin/ant" -f svn/build.xml'
                            } else {
                                bat(/"%ANT_HOME%\bin\ant" /)
                            }
                            removePrefix = antMap.antRemovePrefix
                        } else {
                            echo "Undefined build tool ......"
                        }
                    }
                }

                stage('部署测试环境') {
                    echo "starting publish with ssh......"
					def sshServerList = config.serverIP.split("\n") 
					echo "sshServerList length: ${sshServerList.length}......"
                    if (sshServerList.length == 1) {
                        sshPublisher(publishers: [sshPublisherDesc(configName: "${sshServerList[0]}", transfers: [sshTransfer(cleanRemote: false, excludes: '', execCommand: "sh ${publisherMap.execCommand} ${proMap.serverName}", execTimeout: "${publisherMap.execTimeout}", flatten: false, makeEmptyDirs: false, noDefaultExcludes: false, patternSeparator: '[, ]+', remoteDirectory: 'war/', remoteDirectorySDF: false, removePrefix: "${removePrefix}", sourceFiles: "${publisherMap.sourceFiles}")], usePromotionTimestamp: false, useWorkspaceInPromotion: false, verbose: false)])
                    } else if (sshServerList.length == 2) {
                        sshPublisher(publishers: [sshPublisherDesc(configName: "${sshServerList[0]}", transfers: [sshTransfer(cleanRemote: false, excludes: '', execCommand: "sh ${publisherMap.execCommand} ${proMap.serverName}", execTimeout: "${publisherMap.execTimeout}", flatten: false, makeEmptyDirs: false, noDefaultExcludes: false, patternSeparator: '[, ]+', remoteDirectory: 'war/', remoteDirectorySDF: false, removePrefix: "${removePrefix}", sourceFiles: "${publisherMap.sourceFiles}")], usePromotionTimestamp: false, useWorkspaceInPromotion: false, verbose: false), sshPublisherDesc(configName: "${sshServerList[1]}", transfers: [sshTransfer(cleanRemote: false, excludes: '', execCommand: "sh ${publisherMap.execCommand} ${proMap.serverName}", execTimeout: "${publisherMap.execTimeout}", flatten: false, makeEmptyDirs: false, noDefaultExcludes: false, patternSeparator: ' [, ] + ', remoteDirectory: 'war/ ', remoteDirectorySDF: false, removePrefix: "${removePrefix}", sourceFiles: "${publisherMap.sourceFiles}")], usePromotionTimestamp: false, useWorkspaceInPromotion: false, verbose: false)])
                    } else {
                        echo "Undefined sshServer Length ......"
                    }
                }

                stage('UI自动化测试') {
                    echo "starting UI testing with ......"
                }
            }
        } finally {
			wrap([$class: 'BuildUser']) {
			def user = env.BUILD_USER_ID
				if (currentBuild.result == 'SUCCESS') {
					dingtalk (
							robot: "${dingMap.accessToken}",
							type: 'ACTION_CARD',
							title: "${env.JOB_NAME} ${currentBuild.displayName}构建成功",
							text: [
								"### [${env.JOB_NAME}](${env.JOB_URL}) ",
								'---',
								"- 任务：[${currentBuild.displayName}](${env.BUILD_URL})",
								'- 状态：<font color=#00CD00 >成功</font>',
								"- 持续时间：${currentBuild.durationString}".split("and counting")[0],
								"- 执行人：${user}",
							],
						)
				} else if (currentBuild.result == 'FAILURE') {
					dingtalk (
							robot: "${dingMap.accessToken}",
							type: 'ACTION_CARD',
							title: "${env.JOB_NAME} ${currentBuild.displayName}构建失败",
							text: [
								"### [${env.JOB_NAME}](${env.JOB_URL}) ",
								'---',
								"- 任务：[${currentBuild.displayName}](${env.BUILD_URL})",
								'- 状态：<font color=#EE0000 >失败</font>',
								"- 持续时间：${currentBuild.durationString}".split("and counting")[0],
								"- 执行人：${user}",
							],
						)
				} else {
					echo "${env.JOB_NAME} ${currentBuild.displayName} ${currentBuild.result}"
				}
			}
			withEnv(["QA_EMAIL=${config.QA_EMAIL}"]) {
				emailext body: '''${DEFAULT_CONTENT}''', subject: '''${DEFAULT_SUBJECT}''', to: "$QA_EMAIL"
			}
        }
    }
}