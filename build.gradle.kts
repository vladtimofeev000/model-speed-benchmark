plugins {
    id("java")
}

group = "com.timofeev"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.slf4j:slf4j-api:2.0.17")
    implementation("org.apache.logging.log4j:log4j-core:2.25.1")
    implementation("org.apache.logging.log4j:log4j-slf4j2-impl:2.25.1")

    implementation("org.jetbrains:annotations:26.0.2")

    implementation("org.apache.commons:commons-collections4:4.5.0")
    implementation("commons-cli:commons-cli:1.9.0")
    implementation("ai.djl.huggingface:tokenizers:0.33.0")
    implementation("com.google.code.gson:gson:2.13.1")

    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

tasks.test {
    useJUnitPlatform()
}