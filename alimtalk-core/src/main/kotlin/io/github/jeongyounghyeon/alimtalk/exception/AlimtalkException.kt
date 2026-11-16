package io.github.jeongyounghyeon.alimtalk.exception

/** 라이브러리가 던지는 모든 예외의 부모. unchecked 이므로 Java 에서도 throws 선언이 필요 없다. */
public open class AlimtalkException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

/**
 * NHN Cloud API 가 실패를 응답한 경우.
 *
 * @property resultCode 응답 header 의 resultCode. 응답 본문을 해석하지 못했다면 [UNKNOWN_RESULT_CODE].
 * @property resultMessage 응답 header 의 resultMessage
 * @property httpStatus HTTP 상태 코드
 */
public class AlimtalkApiException(
    public val resultCode: Int,
    public val resultMessage: String,
    public val httpStatus: Int,
) : AlimtalkException("NHN Cloud AlimTalk API failed: resultCode=$resultCode, resultMessage=$resultMessage, httpStatus=$httpStatus") {
    public companion object {
        public const val UNKNOWN_RESULT_CODE: Int = -1
    }
}

/** 네트워크 오류, 타임아웃, 설정 오류 등 API 응답을 받기 전에 실패한 경우. */
public class AlimtalkClientException(message: String, cause: Throwable? = null) : AlimtalkException(message, cause)
