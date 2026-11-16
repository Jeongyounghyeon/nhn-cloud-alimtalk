package io.github.jeongyounghyeon.alimtalk

import java.net.URI
import java.net.URISyntaxException
import java.time.Duration

/**
 * 클라이언트 설정.
 *
 * @property appKey NHN Cloud 알림톡 앱키 (URL 경로에 들어간다)
 * @property secretKey 시크릿 키 (`X-Secret-Key` 헤더). [toString] 에서는 마스킹된다.
 * @property baseUrl API 호스트. 시크릿 키가 헤더로 나가므로 `https` 만 허용한다
 *   (로컬 목 서버용으로 `localhost`, `127.0.0.1`, `[::1]` 에 한해 `http` 허용).
 * @property defaultSenderKey senderKey 를 생략한 호출에서 쓸 기본 발신 프로필 키
 * @property connectTimeout 연결 타임아웃
 * @property readTimeout 요청 하나가 응답(헤더)을 받기까지 기다리는 최대 시간
 */
public class AlimtalkClientConfig @JvmOverloads constructor(
    public val appKey: String,
    public val secretKey: String,
    public val baseUrl: String = DEFAULT_BASE_URL,
    public val defaultSenderKey: String? = null,
    public val connectTimeout: Duration = DEFAULT_CONNECT_TIMEOUT,
    public val readTimeout: Duration = DEFAULT_READ_TIMEOUT,
) {
    init {
        require(appKey.isNotBlank()) { "appKey must not be blank" }
        require(secretKey.isNotBlank()) { "secretKey must not be blank" }
        require(baseUrl.isNotBlank()) { "baseUrl must not be blank" }
        require(!connectTimeout.isZero && !connectTimeout.isNegative) { "connectTimeout must be positive" }
        require(!readTimeout.isZero && !readTimeout.isNegative) { "readTimeout must be positive" }
        requireSecureBaseUrl(baseUrl)
    }

    override fun toString(): String =
        "AlimtalkClientConfig(appKey=$appKey, secretKey=****, baseUrl=$baseUrl, defaultSenderKey=$defaultSenderKey)"

    private fun requireSecureBaseUrl(url: String) {
        // 메시지에 url 을 그대로 싣지 않는다(userinfo 에 자격 증명이 들어 있을 수 있다).
        val uri =
            try {
                URI(url)
            } catch (e: URISyntaxException) {
                throw IllegalArgumentException("baseUrl is not a valid URL", e)
            }
        val scheme = uri.scheme?.lowercase()
        val host = uri.host?.lowercase()
        require(host != null) { "baseUrl must include a host" }
        val secure = scheme == "https" || (scheme == "http" && host in LOOPBACK_HOSTS)
        require(secure) { "baseUrl must use https (http is allowed only for localhost); the secret key is sent as a header" }
    }

    public companion object {
        private val LOOPBACK_HOSTS = setOf("localhost", "127.0.0.1", "[::1]")

        public const val DEFAULT_BASE_URL: String = "https://kakaotalk-bizmessage.api.nhncloudservice.com"
        public val DEFAULT_CONNECT_TIMEOUT: Duration = Duration.ofSeconds(3)
        public val DEFAULT_READ_TIMEOUT: Duration = Duration.ofSeconds(10)
    }
}
