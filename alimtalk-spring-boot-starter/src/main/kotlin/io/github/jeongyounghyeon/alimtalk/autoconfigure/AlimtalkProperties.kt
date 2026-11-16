package io.github.jeongyounghyeon.alimtalk.autoconfigure

import io.github.jeongyounghyeon.alimtalk.AlimtalkClientConfig
import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/** `nhn.alimtalk.*` 설정. */
@ConfigurationProperties(prefix = "nhn.alimtalk")
public class AlimtalkProperties {
    /** NHN Cloud 알림톡 앱키. */
    public var appKey: String? = null

    /** 시크릿 키 (`X-Secret-Key`). */
    public var secretKey: String? = null

    /** API 호스트. https 만 허용한다(localhost 는 http 허용). */
    public var baseUrl: String = AlimtalkClientConfig.DEFAULT_BASE_URL

    /** senderKey 를 생략한 호출에서 쓸 기본 발신 프로필 키. */
    public var defaultSenderKey: String? = null

    /** 연결 타임아웃. */
    public var connectTimeout: Duration = Duration.ofSeconds(3)

    /** 읽기 타임아웃. 요청 하나가 응답을 받기까지 기다리는 최대 시간. */
    public var readTimeout: Duration = Duration.ofSeconds(10)
}
