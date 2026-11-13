import org.springframework.boot.gradle.tasks.bundling.BootJar

plugins {
    java
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

tasks.named<BootJar>("bootJar") {
    archiveFileName.set("ispchat-erp-bridge.jar")
    mainClass.set("br.dev.ispchat.erp.ErpBridgeApplication")
}

tasks.named<Jar>("jar") {
    enabled = true
}

dependencies {
    implementation(project(":ispchat-common"))

    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("io.grpc:grpc-netty-shaded:1.71.0")
    implementation("io.micrometer:micrometer-core")

    testImplementation(project(":ispchat-node"))
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
