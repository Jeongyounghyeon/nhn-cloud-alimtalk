package io.github.jeongyounghyeon.alimtalk

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/** 테스트가 받은 요청 한 건. 네트워크로 실제 전달된 값을 그대로 담는다. */
class RecordedRequest(
    val method: String,
    /** 인코딩된 그대로의 경로와 쿼리 (예: `/a/b?x=1`). */
    val target: String,
    /** 헤더 이름은 소문자. */
    val headers: Map<String, String>,
    val body: String,
) {
    val path: String get() = target.substringBefore('?')
    val query: String get() = target.substringAfter('?', "")
    fun json(): JsonNode = ObjectMapper().readTree(body)
}

/** 로컬 루프백에 뜨는 가짜 NHN 서버. 큐에 넣은 응답을 순서대로 돌려주고 받은 요청을 기록한다. */
class FakeAlimtalkServer : AutoCloseable {
    class Response(
        val status: Int,
        val body: String?,
        val contentType: String?,
        val delayMillis: Long,
        val headers: Map<String, String>,
    )

    private val queue = ConcurrentLinkedQueue<Response>()
    val requests = CopyOnWriteArrayList<RecordedRequest>()
    private val pool: ExecutorService = Executors.newCachedThreadPool()
    private val server: HttpServer = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0).apply {
        createContext("/") { ex -> handle(ex) }
        executor = pool
        start()
    }

    val port: Int get() = server.address.port
    val baseUrl: String get() = "http://127.0.0.1:$port"

    fun enqueue(
        status: Int = 200,
        body: String? = null,
        contentType: String? = "application/json",
        delayMillis: Long = 0,
        headers: Map<String, String> = emptyMap(),
    ) {
        queue.add(Response(status, body, contentType, delayMillis, headers))
    }

    private fun handle(ex: HttpExchange) {
        try {
            val body = ex.requestBody.readBytes().toString(Charsets.UTF_8)
            val target = ex.requestURI.rawPath + (ex.requestURI.rawQuery?.let { "?$it" } ?: "")
            val headers = ex.requestHeaders.entries.associate { it.key.lowercase() to it.value.first() }
            requests.add(RecordedRequest(ex.requestMethod, target, headers, body))

            val r = queue.poll() ?: Response(500, "no response queued", "text/plain", 0, emptyMap())
            if (r.delayMillis > 0) Thread.sleep(r.delayMillis)
            r.headers.forEach { (k, v) -> ex.responseHeaders.add(k, v) }
            r.contentType?.let { ex.responseHeaders.add("Content-Type", it) }
            val bytes = r.body?.toByteArray(Charsets.UTF_8)
            if (bytes == null) {
                ex.sendResponseHeaders(r.status, -1)
            } else {
                ex.sendResponseHeaders(r.status, bytes.size.toLong())
                ex.responseBody.use { it.write(bytes) }
            }
        } catch (_: Exception) {
            // 클라이언트가 타임아웃으로 먼저 끊은 경우 등. 테스트 서버에서는 무시한다.
        } finally {
            ex.close()
        }
    }

    override fun close() {
        server.stop(0)
        pool.shutdownNow()
    }
}
