plugins {
  java
}

group = "dev.hytalemodding"
version = "1.0.0"

java {
  toolchain {
    languageVersion.set(JavaLanguageVersion.of(21))
  }
}

repositories {
  mavenCentral()
  maven("https://maven.hytale.com/release")
}

dependencies {
  compileOnly("com.hypixel.hytale:Server:+")
  implementation("com.google.code.gson:gson:2.11.0")
  implementation("com.mysql:mysql-connector-j:8.4.0")
}

tasks.processResources {
  from("manifest.json") {
    into("")
  }
}

tasks.withType<JavaCompile>().configureEach {
  options.encoding = "UTF-8"
}
