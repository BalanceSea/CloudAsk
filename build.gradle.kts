plugins {
    id("java-library")
    id("com.gradleup.shadow") version "9.6.1"
}

repositories {
    mavenCentral()
    maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
}

dependencies {
    compileOnly("org.spigotmc:spigot-api:1.20.1-R0.1-SNAPSHOT")
    implementation("redis.clients:jedis:5.2.0")
    implementation("com.google.code.gson:gson:2.11.0")
    implementation("com.cronutils:cron-utils:9.2.1")

    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(17)
}

tasks {
    test {
        useJUnitPlatform()
    }

    shadowJar {
        archiveClassifier.set("")
        relocate("redis.clients.jedis", "github.balncesea.cloudAsk.libs.jedis")
        relocate("org.apache.commons.pool2", "github.balncesea.cloudAsk.libs.pool2")
        relocate("com.google.gson", "github.balncesea.cloudAsk.libs.gson")
        relocate("org.json", "github.balncesea.cloudAsk.libs.json")
        relocate("org.slf4j", "github.balncesea.cloudAsk.libs.slf4j")
        relocate("com.cronutils", "github.balncesea.cloudAsk.libs.cronutils")
    }

    jar {
        archiveClassifier.set("plain")
    }

    build {
        dependsOn(shadowJar)
    }

    processResources {
        val props = mapOf("version" to version)
        filesMatching("plugin.yml") {
            expand(props)
        }
    }
}
