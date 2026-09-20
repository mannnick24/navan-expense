plugins {
    java
}

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter:5.13.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.13.4")
    testImplementation("org.assertj:assertj-core:3.27.4")
    testImplementation("com.fasterxml.jackson.core:jackson-databind:2.19.2")
}

sourceSets {
    test {
        resources.srcDir("${rootDir}/fixtures/task-a")
    }
}
