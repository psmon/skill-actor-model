plugins {
    java
    application
}

group = "cluster.java"
version = "1.0.0"

repositories {
    mavenCentral()
}

val akkaVersion = "2.7.0"

dependencies {
    implementation("com.typesafe.akka:akka-actor_2.13:$akkaVersion")
    implementation("com.typesafe.akka:akka-cluster_2.13:$akkaVersion")
    implementation("com.typesafe.akka:akka-cluster-tools_2.13:$akkaVersion")
    implementation("com.typesafe.akka:akka-stream_2.13:$akkaVersion")
    implementation("com.typesafe.akka:akka-stream-kafka_2.13:4.0.2")
    implementation("com.typesafe.akka:akka-slf4j_2.13:$akkaVersion")
    implementation("ch.qos.logback:logback-classic:1.4.14")

    testImplementation("com.typesafe.akka:akka-testkit_2.13:$akkaVersion")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
}

application {
    mainClass.set("cluster.java.Main")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

tasks.test {
    useJUnitPlatform()
}
