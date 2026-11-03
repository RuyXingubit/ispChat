plugins {
	java
	id("org.springframework.boot") version "3.5.0"
	id("io.spring.dependency-management") version "1.1.7"
	id("org.graalvm.buildtools.native") version "0.10.6"
	id("com.google.protobuf") version "0.9.5"
}

group = "br.dev.ispchat"
version = "0.0.1-SNAPSHOT"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(25)
	}
}

repositories {
	mavenCentral()
}

val grpcVersion = "1.71.0"
val protobufVersion = "4.30.2"
val uuidCreatorVersion = "6.0.0"

dependencies {
	implementation("org.springframework.boot:spring-boot-starter")
	implementation("org.springframework.boot:spring-boot-starter-data-redis")

	// UUIDv7 (RFC 9562)
	implementation("com.github.f4b6a3:uuid-creator:${uuidCreatorVersion}")

	// gRPC & Protobuf
	implementation("com.google.protobuf:protobuf-java:${protobufVersion}")
	implementation("io.grpc:grpc-netty-shaded:${grpcVersion}")
	implementation("io.grpc:grpc-protobuf:${grpcVersion}")
	implementation("io.grpc:grpc-stub:${grpcVersion}")
	compileOnly("javax.annotation:javax.annotation-api:1.3.2")

	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testImplementation("io.grpc:grpc-testing:${grpcVersion}")
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

tasks.withType<Test> {
	useJUnitPlatform()
}
