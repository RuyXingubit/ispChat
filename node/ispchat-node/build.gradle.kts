plugins {
    java
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

val grpcVersion = "1.71.0"

// Enable plain JAR alongside bootJar so :ispchat-principal composes :ispchat-node directly
tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    archiveFileName.set("ispchat-node.jar")
}

tasks.named<Jar>("jar") {
    enabled = true
    archiveClassifier.set("plain")
}

dependencies {
    implementation(project(":ispchat-common"))

    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("io.micrometer:micrometer-core")
    implementation("io.grpc:grpc-netty-shaded:${grpcVersion}")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("io.grpc:grpc-testing:${grpcVersion}")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
