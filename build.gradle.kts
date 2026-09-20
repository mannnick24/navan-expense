plugins {
    id("org.springframework.boot") version "4.1.1" apply false
}

allprojects {
    group = "com.navan.expense"
    version = "0.1.0"
}

subprojects {
    plugins.withId("java") {
        extensions.configure<JavaPluginExtension> {
            toolchain {
                languageVersion.set(JavaLanguageVersion.of(21))
            }
        }
        repositories {
            mavenCentral()
        }
        tasks.withType<Test>().configureEach {
            useJUnitPlatform()
        }
    }
}

tasks.register("bootRun") {
    group = "application"
    description = "Runs the API Spring Boot application."
    dependsOn(":api:bootRun")
}

tasks.register("bootJar") {
    group = "application"
    description = "Builds the runnable fat jar (api/build/libs/navan-expense.jar)."
    dependsOn(":api:bootJar")
}

tasks.register("e2eTest") {
    group = "verification"
    description = "Runs API contract tests against the fat jar."
    dependsOn(":api:e2eTest")
}
