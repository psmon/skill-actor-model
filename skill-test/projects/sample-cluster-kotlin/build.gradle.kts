plugins {
    kotlin("jvm") version "1.9.24"
    application
}

group = "cluster.kotlin"
version = "1.0.0"

repositories {
    mavenCentral()
}

val pekkoVersion = "1.1.3"

dependencies {
    implementation("org.apache.pekko:pekko-actor-typed_2.13:$pekkoVersion")
    implementation("org.apache.pekko:pekko-cluster-typed_2.13:$pekkoVersion")
    implementation("org.apache.pekko:pekko-cluster-sharding-typed_2.13:$pekkoVersion")
    implementation("org.apache.pekko:pekko-stream_2.13:$pekkoVersion")
    implementation("org.apache.pekko:pekko-connectors-kafka_2.13:1.1.0")
    implementation("org.apache.pekko:pekko-slf4j_2.13:$pekkoVersion")
    implementation("ch.qos.logback:logback-classic:1.4.14")

    testImplementation("org.apache.pekko:pekko-actor-testkit-typed_2.13:$pekkoVersion")
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.10.2")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.2")
}

application {
    mainClass.set("cluster.kotlin.MainKt")
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(17)
}
