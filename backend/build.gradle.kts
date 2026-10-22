plugins {
    java
    jacoco
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

val timefoldVersion = "2.7.1"

dependencyManagement {
    imports {
        mavenBom("ai.timefold.solver:timefold-solver-bom:$timefoldVersion")
    }
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
    implementation("org.springframework.boot:spring-boot-starter-websocket")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    implementation("org.hibernate.orm:hibernate-spatial")
    implementation("ai.timefold.solver:timefold-solver-core")
    implementation("com.github.ben-manes.caffeine:caffeine")
    implementation("org.apache.commons:commons-csv:1.14.1")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.17")
    runtimeOnly("org.postgresql:postgresql")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
    maxHeapSize = "1g"
    // Colima exposes Docker on a per-user socket; point Testcontainers at it when DOCKER_HOST is not set.
    val colimaSocket = file("${System.getProperty("user.home")}/.colima/default/docker.sock")
    if (System.getenv("DOCKER_HOST") == null && colimaSocket.exists()) {
        environment("DOCKER_HOST", "unix://${colimaSocket.absolutePath}")
        environment("TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE", "/var/run/docker.sock")
    }
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
    doLast {
        // Print overall coverage so `make test` shows the number without opening the HTML report.
        val xml = reports.xml.outputLocation.get().asFile
        if (xml.exists()) {
            val text = xml.readText()
            listOf("INSTRUCTION", "LINE", "BRANCH").forEach { type ->
                Regex("<counter type=\"$type\" missed=\"(\\d+)\" covered=\"(\\d+)\"/>").findAll(text).lastOrNull()?.let {
                    val missed = it.groupValues[1].toDouble()
                    val covered = it.groupValues[2].toDouble()
                    println("JaCoCo $type coverage: %.1f%% (%d of %d)".format(100 * covered / (missed + covered),
                        covered.toLong(), (missed + covered).toLong()))
                }
            }
        }
    }
    classDirectories.setFrom(files(classDirectories.files.map {
        fileTree(it) {
            // Entry points and the benchmark CLI are exercised by running them, not by unit tests.
            exclude("com/routedispatch/RouteDispatchApplication*", "com/routedispatch/benchmark/**",
                "com/routedispatch/demo/SampleDataExporter.class")
        }
    }))
}

tasks.register<JavaExec>("benchmark") {
    group = "verification"
    description = "Runs the routing benchmark on the seeded Chicago day (see README)."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.routedispatch.benchmark.BenchmarkApp")
    jvmArgs("-Xmx2g")
    workingDir = rootProject.projectDir
    args = (project.findProperty("benchmarkArgs") as String?)?.split(" ")?.filter { it.isNotBlank() } ?: emptyList()
}

tasks.register<JavaExec>("sampleData") {
    group = "application"
    description = "Regenerates sample-data/chicago-200-orders.csv from the seeded generator."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.routedispatch.demo.SampleDataExporter")
    workingDir = rootProject.projectDir
}

tasks.bootJar {
    archiveFileName.set("route-dispatch-backend.jar")
}

tasks.jar {
    enabled = false
}
