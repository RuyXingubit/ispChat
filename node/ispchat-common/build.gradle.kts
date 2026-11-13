plugins {
    `java-library`
    id("com.google.protobuf")
}

val grpcVersion = "1.71.0"
val protobufVersion = "4.30.2"
val uuidCreatorVersion = "6.0.0"
val bouncyCastleVersion = "1.80"

dependencies {
    // UUIDv7 (RFC 9562)
    api("com.github.f4b6a3:uuid-creator:${uuidCreatorVersion}")

    // gRPC & Protobuf shared wire contract
    api("com.google.protobuf:protobuf-java:${protobufVersion}")
    api("io.grpc:grpc-protobuf:${grpcVersion}")
    api("io.grpc:grpc-stub:${grpcVersion}")

    // Memory-hard Argon2id blind indexing (self-hosted, zero external KMS dependency)
    implementation("org.bouncycastle:bcprov-jdk18on:${bouncyCastleVersion}")

    // Required for @javax.annotation.Generated emitted by protoc-gen-grpc-java on Java 25
    compileOnly("javax.annotation:javax.annotation-api:1.3.2")

    testImplementation(platform("org.junit:junit-bom:5.12.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:${protobufVersion}"
    }
    plugins {
        create("grpc") {
            artifact = "io.grpc:protoc-gen-grpc-java:${grpcVersion}"
        }
    }
    generateProtoTasks {
        all().forEach {
            it.plugins {
                create("grpc")
            }
        }
    }
}

// Exclude generated Protobuf/gRPC classes from JaCoCo coverage metrics
tasks.withType<JacocoReport> {
    classDirectories.setFrom(
        files(classDirectories.files.map {
            fileTree(it) {
                exclude("br/dev/ispchat/grpc/**")
            }
        })
    )
}
