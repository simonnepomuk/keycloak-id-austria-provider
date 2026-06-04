plugins {
    `java-library`
    id("com.github.ben-manes.versions") version "0.53.0"
}

sourceSets {
    main {
        java {
            setSrcDirs(listOf("src/main"))
        }
        resources {
            setSrcDirs(listOf("src/main/resources"))
        }
    }
    test {
        java {
            setSrcDirs(listOf("src/test"))
        }
        resources {
            setSrcDirs(listOf("src/test/resources"))
        }
    }
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(libs.junit.jupiter)
    testImplementation("org.wiremock:wiremock:3.13.2")
    testImplementation("org.mockito:mockito-core:5.23.0")
    testImplementation("org.mockito:mockito-junit-jupiter:5.23.0")
    testImplementation("io.rest-assured:rest-assured:6.0.0")
    testImplementation("com.github.dasniko:testcontainers-keycloak:4.2.0")
    testImplementation("org.apache.httpcomponents.client5:httpclient5:5.6")

    testRuntimeOnly("org.junit.platform:junit-platform-launcher:6.0.3")

    implementation("org.keycloak:keycloak-server-spi:26.6.3")
    implementation("org.keycloak:keycloak-services:26.6.3")
    implementation("org.keycloak:keycloak-model-jpa:26.6.3")
    implementation("com.fasterxml.jackson.core:jackson-core:2.21.2")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

tasks.test {
    useJUnitPlatform()
}
