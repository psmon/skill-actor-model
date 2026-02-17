plugins {
    kotlin("jvm") version "1.9.24"
    application
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
    implementation("ch.qos.logback:logback-classic:1.4.14")

    testImplementation("org.apache.pekko:pekko-actor-testkit-typed_$scalaBinaryVersion:$pekkoVersion")
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
