plugins {
    id("org.springframework.boot") version "4.0.4"
    id("io.spring.dependency-management") version "1.1.7"
    java
}

group = "com.rentwrangler"
version = "1.0.0"

java {
    sourceCompatibility = JavaVersion.VERSION_24
}

extra["springCloudVersion"] = "2025.1.0"

configurations {
    compileOnly {
        extendsFrom(configurations.annotationProcessor.get())
    }
}

repositories {
    mavenCentral()
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.cloud:spring-cloud-dependencies:${property("springCloudVersion")}")
    }
}

dependencies {
    // Spring Boot starters
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-aop")

    // Lombok
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    // PostgreSQL + Flyway
    runtimeOnly("org.postgresql:postgresql")
    implementation("org.springframework.boot:spring-boot-starter-flyway")

    // Spring Cloud OpenFeign + OkHttp for connection pooling
    implementation("org.springframework.cloud:spring-cloud-starter-openfeign")
    implementation("io.github.openfeign:feign-okhttp")

    // Resilience4j (circuit breaker + retry)
    implementation("org.springframework.cloud:spring-cloud-starter-circuitbreaker-resilience4j")

    // Caffeine caching
    implementation("org.springframework.boot:spring-boot-starter-cache")
    implementation("com.github.ben-manes.caffeine:caffeine")

    // OpenAPI / Swagger UI
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.0.2")

    // Testing — JUnit 5, AssertJ, Mockito are bundled with spring-boot-starter-test
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")

    // Testcontainers — real PostgreSQL for integration tests
    testImplementation(platform("org.testcontainers:testcontainers-bom:2.0.4"))
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
    testImplementation("org.springframework.boot:spring-boot-testcontainers") // @ServiceConnection

    // WireMock — stub the address validation Feign client in integration tests
    testImplementation("org.wiremock:wiremock-standalone:3.13.2")
}

tasks.named<Test>("test") {
    useJUnitPlatform()
    exclude("**/integration/**")
    description = "Runs unit tests (no Docker required)"
}

tasks.register<Test>("integrationTest") {
    useJUnitPlatform()
    include("**/integration/**")
    description = "Runs integration tests (requires Docker)"
    group = "verification"
    shouldRunAfter("test")
}
