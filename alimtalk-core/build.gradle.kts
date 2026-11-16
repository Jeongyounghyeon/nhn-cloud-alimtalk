// core 는 Spring 에 의존하지 않는다. HTTP 는 JDK 17 의 java.net.http.HttpClient 를 쓴다.
dependencies {
    api(libs.jackson.annotations)
    implementation(libs.jackson.module.kotlin)
    implementation(libs.kotlin.reflect)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(libs.mockito.core)
    testRuntimeOnly(libs.junit.platform.launcher)
}
