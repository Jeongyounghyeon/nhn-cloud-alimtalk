import com.vanniktech.maven.publish.MavenPublishBaseExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.spring.boot) apply false
    alias(libs.plugins.maven.publish) apply false
    alias(libs.plugins.dokka) apply false
}

// 서명 키가 있을 때만 서명한다. 키가 없는 로컬 빌드와 publishToMavenLocal 은 서명 없이 동작한다.
val hasSigningKey = listOf("signingInMemoryKey", "signing.gnupg.keyName", "signing.keyId")
    .any { providers.gradleProperty(it).isPresent }

// Maven Central 은 서명 없는 업로드를 거부한다. 키 설정을 빠뜨린 채 업로드까지 가지 않도록 시작 전에 막는다.
gradle.taskGraph.whenReady {
    val toCentral = allTasks.any { it.name.contains("MavenCentral") }
    if (toCentral && !hasSigningKey) {
        throw GradleException(
            "서명 키가 설정되지 않았습니다. Maven Central 업로드 전에 ORG_GRADLE_PROJECT_signingInMemoryKey" +
                "(와 ORG_GRADLE_PROJECT_signingInMemoryKeyPassword) 환경 변수를 설정하세요. RELEASING.md 참고.",
        )
    }
}

// JitPack 빌드에서는 com.github.<user>.<repo> 그룹과 태그 버전으로 발행해야 모듈을 찾는다.
val onJitpack = System.getenv("JITPACK") == "true" || System.getenv("GROUP")?.startsWith("com.github") == true

allprojects {
    repositories {
        mavenCentral()
    }
    if (onJitpack) {
        group = "${System.getenv("GROUP")}.${System.getenv("ARTIFACT")}"
        version = System.getenv("VERSION")
    } else {
        group = "io.github.jeongyounghyeon.alimtalk"
        // version 은 gradle.properties
    }
}

// 라이브러리 모듈(core, starter) 공통 설정. 샘플 앱은 제외한다.
configure(listOf(project(":alimtalk-core"), project(":alimtalk-spring-boot-starter"))) {
    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "org.jetbrains.dokka")
    apply(plugin = "com.vanniktech.maven.publish")

    extensions.configure<KotlinJvmProjectExtension> {
        jvmToolchain(17)
        compilerOptions {
            freeCompilerArgs.add("-Xjsr305=strict")
        }
        // 공개 API 는 명시적으로 선언하도록 강제해, 실수로 Kotlin 전용 타입이 노출되는 것을 막는다.
        explicitApi()
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }

    // Spring Boot BOM 은 빌드(버전 정렬) 전용이다. 발행되는 pom/module 메타데이터에는 노출하지 않으려고
    // 소비 불가능한 configuration 을 만들어 classpath 해석에만 연결한다.
    val bom = configurations.create("internalBom") {
        isCanBeConsumed = false
        isCanBeResolved = false
    }
    dependencies.add(bom.name, dependencies.platform(rootProject.libs.spring.boot.dependencies))
    configurations.matching { it.name.endsWith("Classpath", ignoreCase = true) }.configureEach {
        if (isCanBeResolved) extendsFrom(bom)
    }

    val repoUrl = "https://github.com/jeongyounghyeon/nhn-cloud-alimtalk"
    extensions.configure<MavenPublishBaseExtension> {
        publishToMavenCentral()
        if (hasSigningKey) signAllPublications()

        pom {
            name.set(project.name)
            description.set(
                when (project.name) {
                    "alimtalk-core" ->
                        "Unofficial client for NHN Cloud KakaoTalk Bizmessage AlimTalk template management API (v2.3). No Spring dependency."
                    else ->
                        "Spring Boot starter for the unofficial NHN Cloud KakaoTalk Bizmessage AlimTalk template management client."
                },
            )
            inceptionYear.set("2026")
            url.set(repoUrl)
            licenses {
                license {
                    name.set("The Apache License, Version 2.0")
                    url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                    distribution.set("repo")
                }
            }
            developers {
                developer {
                    id.set("jeongyounghyeon")
                    name.set("jeongyounghyeon")
                    url.set("https://github.com/jeongyounghyeon")
                }
            }
            scm {
                url.set(repoUrl)
                connection.set("scm:git:git://github.com/jeongyounghyeon/nhn-cloud-alimtalk.git")
                developerConnection.set("scm:git:ssh://git@github.com/jeongyounghyeon/nhn-cloud-alimtalk.git")
            }
        }
    }
}
