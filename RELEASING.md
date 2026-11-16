# 릴리스 절차

검증을 세 단계로 나눈다: **mavenLocal → JitPack → Maven Central**. 앞 단계에서 문제가 나오면 뒤 단계로 가지 않는다.
Maven Central 에 공개된 버전은 삭제하거나 덮어쓸 수 없다. 문제가 나오면 다음 버전(0.1.1)으로 낸다.

좌표는 단계마다 다르다.

| 단계 | 저장소 | 좌표 | 버전 |
|---|---|---|---|
| 1 | mavenLocal | `io.github.jeongyounghyeon.alimtalk:alimtalk-spring-boot-starter` | `0.1.0-local` |
| 2 | JitPack | `com.github.jeongyounghyeon.nhn-cloud-alimtalk:alimtalk-spring-boot-starter` | 태그 이름 (`v0.1.0-rc.1`) |
| 3 | Maven Central | `io.github.jeongyounghyeon.alimtalk:alimtalk-spring-boot-starter` | `0.1.0` |

## 1. mavenLocal 로 사용처 시험

```bash
./gradlew publishToMavenLocal -Pversion=0.1.0-local
```

사용처에서 `mavenLocal()` 을 **맨 위에** 추가한다. (시험이 끝나면 지운다. 같은 버전 이름이 남아 있으면 이후 단계에서 로컬 파일이 먼저 잡힌다.)

```kotlin
repositories {
    mavenLocal()
    mavenCentral()
}
dependencies {
    implementation("io.github.jeongyounghyeon.alimtalk:alimtalk-spring-boot-starter:0.1.0-local")
}
```

Maven 은 `<version>0.1.0-local</version>` 만 바꾸면 `~/.m2` 를 자동으로 본다.

정리: `rm -rf ~/.m2/repository/io/github/jeongyounghyeon/alimtalk`

## 2. JitPack 으로 원격 해석 시험

저장소가 공개 상태여야 한다.

```bash
git tag v0.1.0-rc.1 && git push origin v0.1.0-rc.1
```

빌드 상태와 로그는 `https://jitpack.io/#jeongyounghyeon/nhn-cloud-alimtalk` 에서 본다. JDK 17 설정은 `jitpack.yml`, 그룹과 버전은 JitPack 이 주는 환경 변수(`GROUP`, `ARTIFACT`, `VERSION`)로 맞춘다 (`build.gradle.kts`).

```kotlin
repositories {
    maven { url = uri("https://jitpack.io") }
    mavenCentral()
}
dependencies {
    implementation("com.github.jeongyounghyeon.nhn-cloud-alimtalk:alimtalk-spring-boot-starter:v0.1.0-rc.1")
}
```

사용처 네트워크에서 `jitpack.io` 가 막혀 있지 않은지 먼저 확인한다. 이 단계는 JitPack 빌드의 결과물이 정식 배포와 같은 파일이라는 뜻이 아니다 (서명, 좌표가 다르다).

## 3. Maven Central 배포

### 처음 한 번만

1. **Central Portal 토큰**: Portal → Account → *Generate User Token*. 로그인 비밀번호가 아니라 이 토큰을 쓴다.
2. **GPG 키 만들기** (본인 PC 에서):
   ```bash
   gpg --full-generate-key                       # RSA 4096 권장, 만료일은 정책에 맞게
   gpg --list-secret-keys --keyid-format long    # 키 ID 확인
   gpg --keyserver keyserver.ubuntu.com --send-keys <KEY_ID>   # 공개키 등록
   ```
3. **환경 변수** (CI 시크릿 또는 개인 셸. 저장소에 넣지 않는다):
   ```bash
   export ORG_GRADLE_PROJECT_mavenCentralUsername=<토큰 사용자 이름>
   export ORG_GRADLE_PROJECT_mavenCentralPassword=<토큰 비밀번호>
   export ORG_GRADLE_PROJECT_signingInMemoryKey="$(gpg --armor --export-secret-keys <KEY_ID>)"
   export ORG_GRADLE_PROJECT_signingInMemoryKeyPassword=<키 비밀번호>
   ```
   서명 키 변수가 없으면 서명 없이 발행되고, Central 은 서명이 없는 업로드를 거부한다.

### 매 릴리스

1. `gradle.properties` 의 `version` 을 릴리스 버전(`0.1.0`)으로 바꾼다.
2. 전체 빌드: `./gradlew clean build`
3. 호환성 확인: `gradle/libs.versions.toml` 의 `spring-boot` 를 `3.1.0` 으로 바꿔 `./gradlew clean build` 를 돌려 보고 되돌린다. 지원하는 가장 낮은 Boot 버전이다.
4. 서명된 결과물을 로컬에서 먼저 확인한다 (키 환경 변수를 설정한 상태에서):
   ```bash
   ./gradlew -Dmaven.repo.local=/tmp/check-repo publishToMavenLocal
   D=/tmp/check-repo/io/github/jeongyounghyeon/alimtalk/alimtalk-core/0.1.0
   for f in $D/*.jar $D/*.pom; do gpg --verify "$f.asc" "$f"; done   # 모두 Good signature
   ```
5. 업로드: `./gradlew publishToMavenCentral` (수동 릴리스). Portal 의 Deployments 에서 검증 결과를 보고 **Publish** 를 누른다. 이 단계가 마지막으로 멈출 수 있는 지점이다.
6. `git tag v0.1.0 && git push origin v0.1.0`
7. 반영에는 시간이 걸린다. 반영 후 사용처의 JitPack 줄과 좌표를 Central 좌표로 바꾸고 빌드, 테스트한다.
8. `gradle.properties` 를 다음 개발 버전(`0.1.1-SNAPSHOT`)으로 바꿔 커밋한다.

Central 업로드 방식과 플러그인 옵션은 [Vanniktech Maven Publish 문서](https://vanniktech.github.io/gradle-maven-publish-plugin/central/) 와 Sonatype 문서를 따른다. 바뀌었을 수 있으니 릴리스 전에 한 번 확인한다.
