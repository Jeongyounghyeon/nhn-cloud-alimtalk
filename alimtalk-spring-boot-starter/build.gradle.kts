dependencies {
    api(project(":alimtalk-core"))
    // 사용처가 Spring Boot 앱이라 autoconfigure 는 이미 있다. pom 에 버전을 고정해 사용처 Boot 를 끌어올리지 않도록 compileOnly.
    compileOnly(libs.spring.boot.autoconfigure)

    testImplementation(libs.spring.boot.starter.test)
    testRuntimeOnly(libs.junit.platform.launcher)
}
