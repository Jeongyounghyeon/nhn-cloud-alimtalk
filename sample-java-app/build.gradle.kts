plugins {
    java
    alias(libs.plugins.spring.boot)
}

java {
    toolchain { languageVersion.set(JavaLanguageVersion.of(17)) }
}

dependencies {
    implementation(platform(libs.spring.boot.dependencies))
    implementation(project(":alimtalk-spring-boot-starter"))
    implementation(libs.spring.boot.starter)

    testImplementation(libs.spring.boot.starter.test)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
}
