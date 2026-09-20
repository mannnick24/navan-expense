import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

plugins {
    java
    id("org.springframework.boot")
}

dependencies {
    implementation(platform("org.springframework.boot:spring-boot-dependencies:4.1.1"))
    implementation(project(":receipt-parser"))
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-aspectj")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    runtimeOnly("io.micrometer:micrometer-registry-prometheus")
    runtimeOnly("com.h2database:h2")

    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
    testImplementation("org.springframework.boot:spring-boot-starter-actuator-test")
    testImplementation("org.springframework.boot:spring-boot-resttestclient")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

sourceSets {
    main {
        resources.srcDir("${rootDir}/fixtures/task-a")
    }
    create("e2eTest") {
        compileClasspath += sourceSets.test.get().compileClasspath
        runtimeClasspath += sourceSets.test.get().runtimeClasspath
        resources.srcDir("${rootDir}/fixtures/task-a")
    }
}

configurations.named("e2eTestImplementation") {
    extendsFrom(configurations.getByName("testImplementation"))
}
configurations.named("e2eTestRuntimeOnly") {
    extendsFrom(configurations.getByName("testRuntimeOnly"))
}

springBoot {
    mainClass.set("com.navan.expense.ExpenseApplication")
}

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    archiveFileName.set("navan-expense.jar")
}

val e2eRemoteUrl = (findProperty("e2e.baseUrl") as String?)?.takeIf { it.isNotBlank() }
    ?: System.getenv("E2E_BASE_URL")?.takeIf { it.isNotBlank() }
val e2ePort = 18080
val e2eUrl = e2eRemoteUrl ?: "http://127.0.0.1:$e2ePort"
val e2eProcess = AtomicReference<Process>()

val stopE2eApp = tasks.register("stopE2eApp") {
    description = "Stops the fat-jar API started for e2e tests."
    doLast {
        e2eProcess.getAndSet(null)?.let { proc ->
            proc.destroy()
            proc.waitFor(5, TimeUnit.SECONDS)
            if (proc.isAlive) {
                proc.destroyForcibly()
            }
        }
    }
}

val e2eTest = tasks.register<Test>("e2eTest") {
    description = "Runs API contract tests against the fat jar (or E2E_BASE_URL / -Pe2e.baseUrl)."
    group = "verification"
    testClassesDirs = sourceSets["e2eTest"].output.classesDirs
    classpath = sourceSets["e2eTest"].runtimeClasspath
    shouldRunAfter(tasks.test)
    useJUnitPlatform()
    systemProperty("e2e.baseUrl", e2eUrl)
    if (e2eRemoteUrl == null) {
        dependsOn(tasks.named("bootJar"))
        finalizedBy(stopE2eApp)
        val jar = tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar").flatMap { it.archiveFile }
        doFirst {
            val log = layout.buildDirectory.file("e2e-app.log").get().asFile
            log.parentFile.mkdirs()
            val javaBin = File(System.getProperty("java.home"), "bin/java")
            val proc = ProcessBuilder(
                javaBin.absolutePath,
                "-jar",
                jar.get().asFile.absolutePath,
                "--server.port=$e2ePort"
            )
                .redirectErrorStream(true)
                .redirectOutput(log)
                .start()
            e2eProcess.set(proc)
            val deadline = System.currentTimeMillis() + 60_000
            var healthy = false
            while (System.currentTimeMillis() < deadline) {
                if (!proc.isAlive) {
                    throw GradleException("API jar exited before becoming healthy. See ${log.path}")
                }
                try {
                    val conn = URI.create("http://127.0.0.1:$e2ePort/health").toURL()
                        .openConnection() as HttpURLConnection
                    conn.connectTimeout = 500
                    conn.readTimeout = 500
                    conn.requestMethod = "GET"
                    if (conn.responseCode == 200) {
                        healthy = true
                        break
                    }
                } catch (_: Exception) {
                    // still starting
                }
                Thread.sleep(250)
            }
            if (!healthy) {
                proc.destroyForcibly()
                throw GradleException("API jar did not become healthy on port $e2ePort. See ${log.path}")
            }
        }
    }
}

tasks.named("check") {
    dependsOn(e2eTest)
}
