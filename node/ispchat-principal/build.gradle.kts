import org.springframework.boot.gradle.tasks.bundling.BootJar

plugins {
    java
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

tasks.named<BootJar>("bootJar") {
    archiveFileName.set("ispchat-principal.jar")
    mainClass.set("br.dev.ispchat.principal.PrincipalApplication")
}

tasks.named<Jar>("jar") {
    enabled = true
}

dependencies {
    implementation(project(":ispchat-common"))
    implementation(project(":ispchat-node"))

    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("com.fasterxml.jackson.core:jackson-databind")

    implementation("io.grpc:grpc-netty-shaded:1.71.0")
    implementation("io.micrometer:micrometer-core")

    implementation("org.postgresql:postgresql")
    implementation("org.flywaydb:flyway-core")
    runtimeOnly("org.flywaydb:flyway-database-postgresql")

    testImplementation(project(":ispchat-erp-bridge"))
    testImplementation("org.springframework.boot:spring-boot-starter-web")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation(platform("io.zonky.test.postgres:embedded-postgres-binaries-bom:17.2.0"))
    testImplementation("io.zonky.test:embedded-postgres:2.1.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
