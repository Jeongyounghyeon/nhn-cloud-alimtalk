# nhn-cloud-alimtalk

NHN Cloud KakaoTalk Bizmessage **알림톡 템플릿 관리**(API v2.3)를 Spring Boot 에서 쓰는 라이브러리.
Kotlin 으로 작성했지만 Java 에서 쓰는 것을 1순위로 설계했다(builder, unchecked 예외, 동기 API).

v1 범위는 템플릿 관리만이다. 발송과 발송 결과 조회는 포함하지 않는다.

> **비공식 라이브러리.** NHN Cloud, NHN과 제휴하거나 승인받은 프로젝트가 아니다. API 사양은 NHN Cloud 공식 문서(KakaoTalk Bizmessage AlimTalk API v2.3)를 따른다.

## 모듈

| 모듈 | 설명 |
|---|---|
| `alimtalk-core` | `AlimtalkTemplateClient`, 요청/응답 모델, 예외. **Spring 비의존**. HTTP 는 JDK 의 `java.net.http.HttpClient`, JSON 은 Jackson |
| `alimtalk-spring-boot-starter` | 자동 설정(`nhn.alimtalk.*`). Spring Boot 3.1 에서 3.5 까지 빌드·테스트로 확인(3.0 은 미확인) |
| `sample-java-app` | Java 사용 예제 (배포하지 않음) |

## 사용

```groovy
implementation 'io.github.jeongyounghyeon.alimtalk:alimtalk-spring-boot-starter:<version>'
```

```xml
<dependency>
  <groupId>io.github.jeongyounghyeon.alimtalk</groupId>
  <artifactId>alimtalk-spring-boot-starter</artifactId>
  <version>&lt;version&gt;</version>
</dependency>
```

Spring 없이 쓰려면 `alimtalk-core` 만 의존한다. 배포 전에 로컬에서 시험하는 방법은 [RELEASING.md](RELEASING.md).

```yaml
nhn:
  alimtalk:
    app-key: ${ALIMTALK_APP_KEY}
    secret-key: ${ALIMTALK_SECRET_KEY}
    default-sender-key: ${ALIMTALK_SENDER_KEY}   # 선택. 있으면 senderKey 인자를 생략할 수 있다
    # base-url: https://kakaotalk-bizmessage.api.nhncloudservice.com
    # connect-timeout: 3s
    # read-timeout: 10s
```

`app-key` 와 `secret-key` 가 모두 있을 때만 빈이 등록된다. 같은 타입의 빈을 직접 정의하면 그쪽이 우선한다.

```java
@Service
class TemplateService {
    private final AlimtalkTemplateClient client;

    TemplateService(AlimtalkTemplateClient client) { this.client = client; }

    void register() {
        client.createTemplate(CreateTemplateRequest.builder()
                .templateCode("ORDER_DONE")
                .templateName("주문 완료")
                .templateContent("#{이름}님, 주문이 완료되었습니다.")
                .buttons(List.of(TemplateButton.web(1, "주문 조회", "https://example.com/orders")))
                .build());
    }

    TemplatePage approved() {
        return client.listTemplates(TemplateSearchCondition.builder()
                .templateStatus(TemplateStatus.APPROVED).pageSize(50).build());
    }
}
```

| 메서드 | API |
|---|---|
| `createTemplate` | `POST .../templates` |
| `updateTemplate` | `PUT .../templates/{templateCode}` |
| `deleteTemplate` | `DELETE .../templates/{templateCode}` |
| `listTemplateCategories` | `GET .../template/categories` (`categoryCode` 로 쓸 코드 조회. `senderKey` 불필요) |
| `getTemplate` | `GET .../templates/{templateCode}` |
| `listTemplates` | `GET .../templates` |
| `listTemplateModifications` | `GET .../templates/{templateCode}/modifications` |
| `addTemplateComment` | `POST .../templates/{templateCode}/comments` |

### 예외

모두 unchecked 이며 부모는 `AlimtalkException`.
- `AlimtalkApiException`: API 가 실패를 응답함 (`resultCode`, `resultMessage`, `httpStatus`)
- `AlimtalkClientException`: 네트워크/타임아웃/응답 해석 실패

### 확장

`AlimtalkHttpClientCustomizer` 빈을 등록하면 알림톡 클라이언트의 `java.net.http.HttpClient.Builder` 만 조정할 수 있다(프록시, SSL, executor 등). 앱의 다른 HTTP 클라이언트 설정에는 영향을 주지 않는다.

```java
@Bean
AlimtalkHttpClientCustomizer alimtalkProxy() {
    return builder -> builder.proxy(ProxySelector.of(new InetSocketAddress("proxy.internal", 8080)));
}
```

프록시를 따로 지정하지 않으면 JDK 기본 `ProxySelector` 가 쓰여서 `https.proxyHost` 같은 JVM 시스템 프로퍼티를 따른다.

Spring 없이 쓰려면 core 만 의존하고 직접 만든다.

```java
AlimtalkTemplateClient client = new JdkHttpAlimtalkTemplateClient(
        new AlimtalkClientConfig("appKey", "secretKey"));
```

## 보안 주의

- **`base-url` 은 `https` 만 허용한다.** 시크릿 키가 `X-Secret-Key` 헤더로 나가기 때문이다. 로컬 목 서버용으로 `localhost`, `127.0.0.1`, `[::1]` 에 한해 `http` 를 허용한다.
- **리다이렉트(3xx)는 항상 따라가지 않는다.** 따라가면 시크릿 헤더가 다른 호스트로 전달될 수 있다. 커스터마이저에서 `followRedirects` 를 켜도 클라이언트가 마지막에 덮어쓴다. 3xx 응답은 `AlimtalkClientException` 이 된다.
- **앱 전역 HTTP 설정을 이어받지 않는다.** 이 라이브러리는 자체 `HttpClient` 를 만들어 쓰므로, 앱 전역 `RestClient`/`RestTemplate` 인터셉터나 로깅에 `X-Secret-Key` 가 기록되지 않는다. 반대로 앱의 프록시, 관측(메트릭, 트레이싱) 설정도 적용되지 않으니 필요하면 `AlimtalkHttpClientCustomizer` 로 지정한다. JVM 에 `HttpClient` 요청 헤더를 남기는 에이전트나 디버그 로깅(`jdk.httpclient.HttpClient.log`)을 켜면 헤더가 기록될 수 있다.
- **예외의 원인(cause)에 요청 정보가 포함될 수 있다.** 네트워크 실패 시 `AlimtalkClientException` 의 cause 는 JDK 예외(`IOException` 계열)다. 메시지에 URL 을 거의 담지 않지만 보장은 아니며, URL 경로에는 `appKey` 가 있다. 시크릿 키는 헤더라서 들어가지 않는다. 예외를 외부 로그 시스템으로 보낸다면 이 점을 고려한다.
- **비 JSON 응답 본문 일부(앞 200자)가 `AlimtalkApiException` 메시지에 포함될 수 있다.** 프록시나 게이트웨이의 오류 페이지가 그대로 로그에 남을 수 있다.
- **시크릿 키는 코드/저장소에 두지 말고** 환경 변수나 시크릿 관리 도구로 주입한다.

## v1 에서 지원하지 않는 것

- 템플릿 이미지 업로드(multipart: `template-image`, `item-highlight`)와 첨부 파일 댓글(`comments_file`). 이미 업로드한 이미지의 이름/URL 을 `templateImage(name, url)` 로 지정하는 것은 가능하다.
- 발송, 발송 결과 조회

## 라이선스

[Apache License 2.0](LICENSE)

## 개발

```bash
./gradlew build                                   # 전체 빌드와 테스트
./gradlew :sample-java-app:bootRun --args='--demo.enabled=true'   # 실제 API 로 목록 조회(읽기 전용)
```

로컬 저장소(`~/.m2`)에 발행해 다른 프로젝트에서 시험하려면 `./gradlew publishToMavenLocal -Pversion=0.1.0-local`.

실제 호출에는 `ALIMTALK_APP_KEY`, `ALIMTALK_SECRET_KEY`, `ALIMTALK_SENDER_KEY` 환경 변수가 필요하다.

JDK 17, Gradle wrapper 8.14.3, Kotlin 2.3. 빌드 기준은 Spring Boot 3.5 이고, 호환성 확인을 위해 `gradle/libs.versions.toml` 의 `spring-boot` 를 `3.1.0` 으로 바꿔 전체 빌드와 테스트를 돌려 볼 수 있다.
