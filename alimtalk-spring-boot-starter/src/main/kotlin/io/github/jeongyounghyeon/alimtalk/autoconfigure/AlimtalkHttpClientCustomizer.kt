package io.github.jeongyounghyeon.alimtalk.autoconfigure

import java.net.http.HttpClient

/**
 * 알림톡 클라이언트가 쓰는 [HttpClient.Builder] 를 손보는 확장 포인트(프록시, SSL, executor 등).
 * 빈으로 등록하면 모두 적용되고, 알림톡 클라이언트에만 영향을 준다.
 *
 * 리다이렉트는 여기서 `followRedirects` 를 바꿔도 항상 따라가지 않는다(시크릿 헤더 보호).
 */
public fun interface AlimtalkHttpClientCustomizer {
    public fun customize(builder: HttpClient.Builder)
}
