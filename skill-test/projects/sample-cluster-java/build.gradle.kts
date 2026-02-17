plugins {
    java
    id("org.springframework.boot") version "3.5.0"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "cluster.java"
version = "1.0.0"

repositories {
    mavenCentral()
}

val akkaVersion = "2.7.0"
val scalaBinaryVersion = "2.13"

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.5")

    implementation("com.typesafe.akka:akka-actor_$scalaBinaryVersion:$akkaVersion")
    implementation("com.typesafe.akka:akka-cluster_$scalaBinaryVersion:$akkaVersion")
    implementation("com.typesafe.akka:akka-cluster-tools_$scalaBinaryVersion:$akkaVersion")
    implementation("com.typesafe.akka:akka-stream_$scalaBinaryVersion:$akkaVersion")
    implementation("com.typesafe.akka:akka-stream-kafka_$scalaBinaryVersion:4.0.2")
    implementation("com.typesafe.akka:akka-slf4j_$scalaBinaryVersion:$akkaVersion")

    testImplementation("com.typesafe.akka:akka-testkit_$scalaBinaryVersion:$akkaVersion")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

tasks.test {
    useJUnitPlatform()
}
