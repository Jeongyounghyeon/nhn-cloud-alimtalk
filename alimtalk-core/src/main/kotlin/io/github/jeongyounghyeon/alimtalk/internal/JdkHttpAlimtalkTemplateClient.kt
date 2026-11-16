package io.github.jeongyounghyeon.alimtalk.internal

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinFeature
import com.fasterxml.jackson.module.kotlin.KotlinModule
import io.github.jeongyounghyeon.alimtalk.AlimtalkClientConfig
import io.github.jeongyounghyeon.alimtalk.AlimtalkTemplateClient
import io.github.jeongyounghyeon.alimtalk.exception.AlimtalkApiException
import io.github.jeongyounghyeon.alimtalk.exception.AlimtalkClientException
import io.github.jeongyounghyeon.alimtalk.model.Template
import io.github.jeongyounghyeon.alimtalk.model.TemplateCategory
import io.github.jeongyounghyeon.alimtalk.model.TemplatePage
import io.github.jeongyounghyeon.alimtalk.request.CreateTemplateRequest
import io.github.jeongyounghyeon.alimtalk.request.TemplateSearchCondition
import io.github.jeongyounghyeon.alimtalk.request.UpdateTemplateRequest
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpRequest.BodyPublishers
import java.net.http.HttpResponse
import java.net.http.HttpResponse.BodyHandlers
import java.util.function.Consumer

/**
 * JDK 17 의 [HttpClient] 기반 구현. Spring 에 의존하지 않는다.
 *
 * - `X-Secret-Key` 와 요청 타임아웃은 요청마다 이 클래스가 붙인다.
 * - **리다이렉트는 항상 따라가지 않는다.** 커스터마이저가 `followRedirects` 를 바꿔도 마지막에 덮어쓴다.
 *   따라가면 시크릿 헤더가 다른 호스트로 전달될 수 있기 때문이다.
 * - 요청/응답 JSON 은 이 클래스의 mapper 로 직접 변환한다.
 *
 * @param httpClientCustomizer 프록시, SSL, executor 등 [HttpClient.Builder] 를 조정하는 확장 포인트.
 *   연결 타임아웃은 설정값이 먼저 적용되고 커스터마이저가 덮어쓸 수 있다.
 */
public class JdkHttpAlimtalkTemplateClient @JvmOverloads constructor(
    private val config: AlimtalkClientConfig,
    httpClientCustomizer: Consumer<HttpClient.Builder> = Consumer { },
) : AlimtalkTemplateClient {
    private val httpClient: HttpClient =
        HttpClient.newBuilder()
            .connectTimeout(config.connectTimeout)
            .also { httpClientCustomizer.accept(it) }
            .followRedirects(HttpClient.Redirect.NEVER)
            .build()

    private val mapper: ObjectMapper =
        ObjectMapper()
            // NHN 응답은 값이 없는 필드를 키 생략이 아니라 명시적 null 로 내려주기도 한다.
            // null 이면 모델의 기본값(빈 리스트 등)을 쓰도록 한다.
            .registerModule(KotlinModule.Builder().configure(KotlinFeature.NullIsSameAsDefault, true).build())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .setSerializationInclusion(JsonInclude.Include.NON_NULL)

    override fun createTemplate(senderKey: String, request: CreateTemplateRequest) {
        call(Method.POST, templatesPath(senderKey), body = request)
    }

    override fun createTemplate(request: CreateTemplateRequest): Unit = createTemplate(defaultSender(), request)

    override fun updateTemplate(senderKey: String, templateCode: String, request: UpdateTemplateRequest) {
        call(Method.PUT, templatePath(senderKey, templateCode), body = request)
    }

    override fun updateTemplate(templateCode: String, request: UpdateTemplateRequest): Unit =
        updateTemplate(defaultSender(), templateCode, request)

    override fun deleteTemplate(senderKey: String, templateCode: String) {
        call(Method.DELETE, templatePath(senderKey, templateCode))
    }

    override fun deleteTemplate(templateCode: String): Unit = deleteTemplate(defaultSender(), templateCode)

    override fun getTemplate(senderKey: String, templateCode: String): Template {
        val root = call(Method.GET, templatePath(senderKey, templateCode))
        // 단건 조회 응답의 본문 키가 문서에 모호하게 적혀 있어 `template`/`templates` 모두 허용하고, 배열이면 첫 원소를 쓴다.
        val node = (root.get("template") ?: root.get("templates"))?.let { if (it.isArray) it.firstOrNull() else it }
        return node?.let { read(it, Template::class.java) }
            ?: throw AlimtalkClientException("Template response has no template body: $templateCode")
    }

    override fun getTemplate(templateCode: String): Template = getTemplate(defaultSender(), templateCode)

    override fun listTemplates(senderKey: String, condition: TemplateSearchCondition): TemplatePage {
        val query = buildMap {
            condition.templateCode?.let { put("templateCode", it) }
            condition.templateName?.let { put("templateName", it) }
            condition.templateStatus?.let { put("templateStatus", it.code) }
            condition.pageNum?.let { put("pageNum", it.toString()) }
            condition.pageSize?.let { put("pageSize", it.toString()) }
        }
        val root = call(Method.GET, templatesPath(senderKey), query = query)
        return toPage(root.get("templateListResponse"))
    }

    override fun listTemplates(condition: TemplateSearchCondition): TemplatePage = listTemplates(defaultSender(), condition)

    override fun listTemplateModifications(senderKey: String, templateCode: String): TemplatePage {
        val root = call(Method.GET, templatePath(senderKey, templateCode) + "/modifications")
        return toPage(root.get("templateModificationsResponse"))
    }

    override fun listTemplateModifications(templateCode: String): TemplatePage =
        listTemplateModifications(defaultSender(), templateCode)

    override fun listTemplateCategories(): List<TemplateCategory> {
        val root = call(Method.GET, "$API_PREFIX/appkeys/${encode(config.appKey)}/template/categories")
        val categories = root.get("categories")?.takeIf { it.isArray }
            ?: throw AlimtalkClientException("Response has no categories body")
        return categories.map { read(it, TemplateCategory::class.java) }
    }

    override fun addTemplateComment(senderKey: String, templateCode: String, comment: String) {
        requireText(comment, "comment")
        call(Method.POST, templatePath(senderKey, templateCode) + "/comments", body = mapOf("comment" to comment))
    }

    override fun addTemplateComment(templateCode: String, comment: String): Unit =
        addTemplateComment(defaultSender(), templateCode, comment)

    private fun defaultSender(): String =
        checkNotNull(config.defaultSenderKey?.takeIf { it.isNotBlank() }) {
            "default-sender-key is not configured; pass senderKey explicitly"
        }

    private fun templatesPath(senderKey: String): String =
        "$API_PREFIX/appkeys/${encode(config.appKey)}/senders/${encode(requireText(senderKey, "senderKey"))}/templates"

    private fun templatePath(senderKey: String, templateCode: String): String =
        templatesPath(senderKey) + "/" + encode(requireText(templateCode, "templateCode"))

    private fun requireText(value: String, name: String): String {
        require(value.isNotBlank()) { "$name must not be blank" }
        return value
    }

    private fun toPage(node: JsonNode?): TemplatePage {
        if (node == null) throw AlimtalkClientException("Response has no template list body")
        val templates = node.get("templates")?.map { read(it, Template::class.java) } ?: emptyList()
        return TemplatePage(templates, node.get("totalCount")?.asInt() ?: templates.size)
    }

    private fun <T> read(node: JsonNode, type: Class<T>): T =
        try {
            mapper.treeToValue(node, type)
        } catch (e: JsonProcessingException) {
            throw AlimtalkClientException("Failed to parse AlimTalk response", e)
        }

    /** 요청을 보내고, 응답 header 를 검사한 뒤 응답 전체 JSON 을 돌려준다. */
    private fun call(
        method: Method,
        path: String,
        query: Map<String, String> = emptyMap(),
        body: Any? = null,
    ): JsonNode {
        val request = buildRequest(method, path, query, body)
        val response: HttpResponse<String> =
            try {
                httpClient.send(request, BodyHandlers.ofString(Charsets.UTF_8))
            } catch (e: IOException) {
                throw AlimtalkClientException("AlimTalk request failed: ${e.javaClass.simpleName}", e)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                throw AlimtalkClientException("AlimTalk request was interrupted", e)
            }

        val status = response.statusCode()
        val raw: String? = response.body()
        if (status in REDIRECT_RANGE) {
            // Location 은 로그에 남기지 않는다.
            throw AlimtalkClientException("AlimTalk API responded with a redirect (httpStatus=$status); redirects are not followed")
        }

        val root: JsonNode? =
            try {
                raw?.takeIf { it.isNotBlank() }?.let { mapper.readTree(it) }
            } catch (_: JsonProcessingException) {
                null
            }
        val header = root?.get("header")
        if (header == null) {
            if (status >= HTTP_ERROR_THRESHOLD) {
                throw AlimtalkApiException(AlimtalkApiException.UNKNOWN_RESULT_CODE, raw?.take(SNIPPET_LENGTH).orEmpty(), status)
            }
            throw AlimtalkClientException("AlimTalk response has no header (httpStatus=$status)")
        }
        if (header.get("isSuccessful")?.asBoolean() != true) {
            throw AlimtalkApiException(
                header.get("resultCode")?.asInt() ?: AlimtalkApiException.UNKNOWN_RESULT_CODE,
                header.get("resultMessage")?.asText().orEmpty(),
                status,
            )
        }
        return root
    }

    private fun buildRequest(method: Method, path: String, query: Map<String, String>, body: Any?): HttpRequest {
        val url = StringBuilder(config.baseUrl.trimEnd('/')).append(path)
        if (query.isNotEmpty()) {
            url.append('?').append(query.entries.joinToString("&") { "${encode(it.key)}=${encode(it.value)}" })
        }
        val builder = HttpRequest.newBuilder(URI.create(url.toString()))
            .timeout(config.readTimeout)
            .header(SECRET_KEY_HEADER, config.secretKey)
            .header("Accept", "application/json")
        val publisher =
            if (body == null) {
                null
            } else {
                try {
                    BodyPublishers.ofString(mapper.writeValueAsString(body), Charsets.UTF_8)
                } catch (e: JsonProcessingException) {
                    throw AlimtalkClientException("Failed to serialize AlimTalk request", e)
                }
            }
        if (publisher != null) builder.header("Content-Type", JSON_UTF8)
        return when (method) {
            Method.GET -> builder.GET()
            Method.DELETE -> builder.DELETE()
            Method.POST -> builder.POST(publisher ?: BodyPublishers.noBody())
            Method.PUT -> builder.PUT(publisher ?: BodyPublishers.noBody())
        }.build()
    }

    private enum class Method { GET, POST, PUT, DELETE }

    private companion object {
        const val SECRET_KEY_HEADER = "X-Secret-Key"
        const val API_PREFIX = "/alimtalk/v2.3"
        const val JSON_UTF8 = "application/json;charset=UTF-8"
        const val HTTP_ERROR_THRESHOLD = 400
        const val SNIPPET_LENGTH = 200
        val REDIRECT_RANGE = 300..399
        const val HEX = "0123456789ABCDEF"

        /**
         * 경로 세그먼트/쿼리 값 인코딩. 영숫자와 `-._~` 만 그대로 두고 나머지(`/`, `?`, `#`, 공백 포함)는
         * UTF-8 퍼센트 인코딩한다. 값에 `../` 나 `?` 가 들어 있어도 요청 경로가 바뀌지 않는다.
         */
        fun encode(value: String): String {
            val out = StringBuilder(value.length + 8)
            for (byte in value.toByteArray(Charsets.UTF_8)) {
                val c = byte.toInt() and 0xFF
                val unreserved = c in 'a'.code..'z'.code || c in 'A'.code..'Z'.code || c in '0'.code..'9'.code ||
                    c == '-'.code || c == '.'.code || c == '_'.code || c == '~'.code
                if (unreserved) {
                    out.append(c.toChar())
                } else {
                    out.append('%').append(HEX[c shr 4]).append(HEX[c and 0x0F])
                }
            }
            return out.toString()
        }
    }
}
