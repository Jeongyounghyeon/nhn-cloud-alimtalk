package io.github.jeongyounghyeon.alimtalk.autoconfigure

import io.github.jeongyounghyeon.alimtalk.AlimtalkClientConfig
import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/** `nhn.alimtalk.*` 설정. */
@ConfigurationProperties(prefix = "nhn.alimtalk")
public class AlimtalkProperties {
    /** NHN Cloud 알림톡 앱키. secret-key 와 함께 설정해야 하고, 둘 다 비어 있으면 클라이언트를 만들지 않는다. */
    public var appKey: String? = null

    /** 시크릿 키 (`X-Secret-Key`). app-key 와 함께 설정해야 한다. */
    public var secretKey: String? = null

    /** API 호스트. https 만 허용한다(localhost 는 http 허용). 비어 있으면 기본값을 쓴다. */
    public var baseUrl: String = AlimtalkClientConfig.DEFAULT_BASE_URL
        set(value) {
            field = value.ifBlank { AlimtalkClientConfig.DEFAULT_BASE_URL }
        }

    /** senderKey 를 생략한 호출에서 쓸 기본 발신 프로필 키. 비어 있으면 설정하지 않은 것으로 본다. */
    public var defaultSenderKey: String? = null
        set(value) {
            field = value?.takeIf { it.isNotBlank() }
        }

    /** 연결 타임아웃. */
    public var connectTimeout: Duration = Duration.ofSeconds(3)

    /** 읽기 타임아웃. 요청 하나가 응답을 받기까지 기다리는 최대 시간. */
    public var readTimeout: Duration = Duration.ofSeconds(10)
}
