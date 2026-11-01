plugins {
    java
    id("com.gradleup.shadow") version "9.6.1"
}

group = "com.paimc"
version = "2.1.2"

base {
    archivesName.set("Vault")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
    withSourcesJar()
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.extendedclip.com/content/repositories/placeholderapi/")
    maven("https://jitpack.io")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    compileOnly("me.clip:placeholderapi:2.11.5")

    // Vault API classes are bundled because this plugin provides the Economy service itself.
    implementation("com.github.MilkBowl:VaultAPI:1.7") {
        exclude(group = "org.bukkit", module = "bukkit")
    }

    // Runtime dependencies used by the plugin and the selectable balance backends.
    implementation("org.bstats:bstats-bukkit:3.0.2")
    implementation("com.zaxxer:HikariCP:5.1.0")
    implementation("com.google.code.gson:gson:2.13.2")
    implementation("com.h2database:h2:2.5.252")
    implementation("org.xerial:sqlite-jdbc:3.54.1.0")
    implementation("com.mysql:mysql-connector-j:9.5.0")
    implementation("org.mariadb.jdbc:mariadb-java-client:4.0.0")
    implementation("org.postgresql:postgresql:42.7.14")
}

tasks.processResources {
    filteringCharset = "UTF-8"
    filesMatching("plugin.yml") {
        expand("version" to project.version)
    }
}

tasks.shadowJar {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    filesMatching("META-INF/services/**") {
        duplicatesStrategy = DuplicatesStrategy.INCLUDE
    }

    archiveClassifier.set("")
    mergeServiceFiles()

    relocate("org.bstats", "com.paimc.vault.libs.metrics")
    relocate("com.zaxxer.hikari", "com.paimc.vault.libs.hikari")
    relocate("com.google.gson", "com.paimc.vault.libs.gson")

    exclude(
        "META-INF/*.SF",
        "META-INF/*.DSA",
        "META-INF/*.RSA"
    )
}

tasks.jar {
    enabled = false
}

tasks.build {
    dependsOn(tasks.shadowJar)
}
