node {
    stage('Checkout') {
        checkout scm
    }

    stage('Clean') {
        gradlew("clean")
    }

    stage('Build') {
        def ver = version()
        echo "Building Mossy version ${ver} on branch ${env.BRANCH_NAME}"
        gradlew("-PBUILD_NUMBER=${env.BUILD_NUMBER}")
    }

    stage('Archive') {
        artifactPath = 'build/libs/*.jar'
        step([$class: 'ArtifactArchiver', artifacts: artifactPath, excludes: 'build/libs/*-base.jar',
              fingerprint: true])
    }
}

def version() {
    def matcher = readFile('build.gradle') =~ 'version = \'(.+)\''
    matcher ? matcher[0][1] : null
}

def gradlew(args) {
    if (System.properties['os.name'].startsWith('Windows')) {
        bat "gradlew ${args}"
    } else {
        sh "./gradlew ${args}"
    }
}
