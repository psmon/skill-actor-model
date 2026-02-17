plugins {
    kotlin("jvm") version "1.9.24"
    kotlin("plugin.spring") version "1.9.24"
    id("org.springframework.boot") version "3.5.0"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "cluster.kotlin"
version = "1.0.0"

repositories {
    mavenCentral()
}

val scalaBinaryVersion = "2.13"
val pekkoVersion = "1.4.0"
val pekkoManagementVersion = "1.2.0"

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("org.springdoc:springdoc-openapi-starter-webflux-ui:2.8.5")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor:1.8.1")

    implementation("org.apache.pekko:pekko-actor-typed_$scalaBinaryVersion:$pekkoVersion")
    implementation("org.apache.pekko:pekko-cluster-typed_$scalaBinaryVersion:$pekkoVersion")
    implementation("org.apache.pekko:pekko-cluster-sharding-typed_$scalaBinaryVersion:$pekkoVersion")
    implementation("org.apache.pekko:pekko-stream_$scalaBinaryVersion:$pekkoVersion")
    implementation("org.apache.pekko:pekko-discovery_$scalaBinaryVersion:$pekkoVersion")
    implementation("org.apache.pekko:pekko-slf4j_$scalaBinaryVersion:$pekkoVersion")
    implementation("org.apache.pekko:pekko-management_$scalaBinaryVersion:$pekkoManagementVersion")
    implementation("org.apache.pekko:pekko-management-cluster-bootstrap_$scalaBinaryVersion:$pekkoManagementVersion")
    implementation("org.apache.pekko:pekko-discovery-kubernetes-api_$scalaBinaryVersion:$pekkoManagementVersion")
    implementation("org.apache.pekko:pekko-connectors-kafka_$scalaBinaryVersion:1.1.0")

    testImplementation("org.apache.pekko:pekko-actor-testkit-typed_$scalaBinaryVersion:$pekkoVersion")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(21)
}
