package io.github.jeongyounghyeon.alimtalk

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.jeongyounghyeon.alimtalk.exception.AlimtalkApiException
import io.github.jeongyounghyeon.alimtalk.exception.AlimtalkClientException
import io.github.jeongyounghyeon.alimtalk.internal.JdkHttpAlimtalkTemplateClient
import io.github.jeongyounghyeon.alimtalk.model.TemplateButton
import io.github.jeongyounghyeon.alimtalk.model.TemplateEmphasizeType
import io.github.jeongyounghyeon.alimtalk.model.TemplateMessageType
import io.github.jeongyounghyeon.alimtalk.model.TemplateStatus
import io.github.jeongyounghyeon.alimtalk.request.CreateTemplateRequest
import io.github.jeongyounghyeon.alimtalk.request.TemplateSearchCondition
import io.github.jeongyounghyeon.alimtalk.request.UpdateTemplateRequest
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.IOException
import java.net.http.HttpClient
import java.net.http.HttpTimeoutException
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Consumer

/** 로컬 가짜 서버로 실제 JDK HttpClient 의 요청/응답을 검증한다. 외부 NHN 서버와는 통신하지 않는다. */
class JdkHttpAlimtalkTemplateClientTest {
    private val prefix = "/alimtalk/v2.3/appkeys/APPKEY/senders/SENDER/templates"
    private val ok = """{"header":{"resultCode":0,"resultMessage":"SUCCESS","isSuccessful":true}}"""
    private val mapper = ObjectMapper()

    private lateinit var server: FakeAlimtalkServer
    private lateinit var client: AlimtalkTemplateClient

    @BeforeEach
    fun setUp() {
        server = FakeAlimtalkServer()
        client = JdkHttpAlimtalkTemplateClient(config())
    }

    @AfterEach
    fun tearDown() {
        server.close()
    }

    private fun config(readTimeout: Duration = Duration.ofSeconds(5)) =
        AlimtalkClientConfig("APPKEY", "SECRET", server.baseUrl, "SENDER", readTimeout = readTimeout)

    @Test
    fun `등록은 POST 로 코드와 본문을 보내고 null 필드는 생략한다`() {
        server.enqueue(body = ok)

        client.createTemplate(
            CreateTemplateRequest.builder()
                .templateCode("ORDER_01")
                .templateName("주문 완료")
                .templateContent("#{이름}님 주문이 완료되었습니다.")
                .templateMessageType(TemplateMessageType.BA)
                .templateEmphasizeType(TemplateEmphasizeType.NONE)
                .buttons(listOf(TemplateButton.web(1, "주문 조회", "https://example.com/orders")))
                .build(),
        )

        val req = server.requests.single()
        assertThat(req.method).isEqualTo("POST")
        assertThat(req.path).isEqualTo(prefix)
        assertThat(req.headers["x-secret-key"]).isEqualTo("SECRET")
        assertThat(req.headers["content-type"]).isEqualTo("application/json;charset=UTF-8")
        val json = req.json()
        assertThat(json["templateCode"].asText()).isEqualTo("ORDER_01")
        assertThat(json["templateName"].asText()).isEqualTo("주문 완료")
        assertThat(json["templateContent"].asText()).isEqualTo("#{이름}님 주문이 완료되었습니다.")
        assertThat(json["templateMessageType"].asText()).isEqualTo("BA")
        assertThat(json["templateEmphasizeType"].asText()).isEqualTo("NONE")
        assertThat(json["buttons"][0]["type"].asText()).isEqualTo("WL")
        assertThat(json["buttons"][0].has("linkPc")).isFalse()
        assertThat(json.has("templateExtra")).isFalse()
    }

    @Test
    fun `수정은 PUT 으로 코드 경로를 쓰고 본문에 templateCode 가 없다`() {
        server.enqueue(body = ok)

        client.updateTemplate(
            "SENDER",
            "ORDER_01",
            UpdateTemplateRequest.builder().templateName("주문 완료 v2").templateContent("본문").build(),
        )

        val req = server.requests.single()
        assertThat(req.method).isEqualTo("PUT")
        assertThat(req.path).isEqualTo("$prefix/ORDER_01")
        assertThat(req.json()["templateName"].asText()).isEqualTo("주문 완료 v2")
        assertThat(req.json().has("templateCode")).isFalse()
    }

    @Test
    fun `삭제는 DELETE 이고 본문이 없다`() {
        server.enqueue(body = ok)

        client.deleteTemplate("ORDER_01") // 기본 senderKey 사용

        val req = server.requests.single()
        assertThat(req.method).isEqualTo("DELETE")
        assertThat(req.path).isEqualTo("$prefix/ORDER_01")
        assertThat(req.headers["x-secret-key"]).isEqualTo("SECRET")
        assertThat(req.body).isEmpty()
    }

    @Test
    fun `목록은 조건을 쿼리로 보내고 응답을 파싱한다`() {
        server.enqueue(
            body = """
                {"header":{"resultCode":0,"resultMessage":"SUCCESS","isSuccessful":true},
                 "templateListResponse":{"totalCount":7,"templates":[
                   {"senderKey":"SENDER","templateCode":"ORDER_01","templateName":"주문 완료","status":"APR",
                    "unknownFutureField":123,
                    "buttons":[{"ordering":1,"type":"WL","name":"조회","linkMo":"https://e.com"}]}
                 ]}}
            """.trimIndent(),
        )

        val page = client.listTemplates(
            TemplateSearchCondition.builder()
                .templateName("주문")
                .templateStatus(TemplateStatus.APPROVED)
                .pageNum(2)
                .pageSize(50)
                .build(),
        )

        val req = server.requests.single()
        assertThat(req.method).isEqualTo("GET")
        assertThat(req.path).isEqualTo(prefix)
        assertThat(req.query).isEqualTo("templateName=%EC%A3%BC%EB%AC%B8&templateStatus=TSC03&pageNum=2&pageSize=50")
        assertThat(page.totalCount).isEqualTo(7)
        assertThat(page.templates).hasSize(1)
        assertThat(page.templates[0].templateCode).isEqualTo("ORDER_01")
        assertThat(page.templates[0].buttons[0].name).isEqualTo("조회")
        assertThat(page.templates[0].comments).isEmpty()
    }

    @Test
    fun `단건 조회는 template 과 templates 키 모두 읽는다`() {
        server.enqueue(
            body = """{"header":{"resultCode":0,"resultMessage":"SUCCESS","isSuccessful":true},
                       "template":{"templateCode":"ORDER_01","templateName":"주문","comments":[{"id":1,"content":"문의","status":"REQ"}]}}""",
        )
        server.enqueue(
            body = """{"header":{"resultCode":0,"resultMessage":"SUCCESS","isSuccessful":true},
                       "templates":[{"templateCode":"ORDER_02","templateName":"주문2"}]}""",
        )

        val t = client.getTemplate("ORDER_01")
        assertThat(t.templateName).isEqualTo("주문")
        assertThat(t.comments[0].content).isEqualTo("문의")
        assertThat(client.getTemplate("ORDER_02").templateCode).isEqualTo("ORDER_02")
    }

    @Test
    fun `응답의 명시적 null 필드는 기본값으로 처리한다`() {
        server.enqueue(
            body = """{"header":{"resultCode":0,"resultMessage":"SUCCESS","isSuccessful":true},
                       "template":{"templateCode":"ORDER_01","buttons":null,"quickReplies":null,"comments":null,
                                   "templateItem":null,"securityFlag":null}}""",
        )

        val t = client.getTemplate("ORDER_01")

        assertThat(t.templateCode).isEqualTo("ORDER_01")
        assertThat(t.buttons).isEmpty()
        assertThat(t.quickReplies).isEmpty()
        assertThat(t.comments).isEmpty()
        assertThat(t.templateItem).isNull()
    }

    @Test
    fun `수정 이력 조회는 activated 를 포함해 파싱한다`() {
        server.enqueue(
            body = """{"header":{"resultCode":0,"resultMessage":"SUCCESS","isSuccessful":true},
                       "templateModificationsResponse":{"totalCount":1,"templates":[{"templateCode":"ORDER_01","activated":true}]}}""",
        )

        val history = client.listTemplateModifications("ORDER_01")

        assertThat(server.requests.single().path).isEqualTo("$prefix/ORDER_01/modifications")
        assertThat(history.totalCount).isEqualTo(1)
        assertThat(history.templates.single().activated).isTrue()
    }

    @Test
    fun `문의 댓글은 comment 만 담아 POST 한다`() {
        server.enqueue(body = ok)

        client.addTemplateComment("ORDER_01", "재검수 부탁드립니다")

        val req = server.requests.single()
        assertThat(req.method).isEqualTo("POST")
        assertThat(req.path).isEqualTo("$prefix/ORDER_01/comments")
        assertThat(req.json()).isEqualTo(mapper.readTree("""{"comment":"재검수 부탁드립니다"}"""))
        assertThatThrownBy { client.addTemplateComment("ORDER_01", " ") }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `senderKey 를 생략한 모든 오버로드는 기본 senderKey 를 쓴다`() {
        val header = """"header":{"resultCode":0,"resultMessage":"S","isSuccessful":true}"""
        server.enqueue(body = ok) // create
        server.enqueue(body = ok) // update
        server.enqueue(body = """{$header,"template":{"templateCode":"C1"}}""") // get
        server.enqueue(body = """{$header,"templateListResponse":{"totalCount":0,"templates":[]}}""") // list
        server.enqueue(body = """{$header,"templateModificationsResponse":{"totalCount":0,"templates":[]}}""") // history
        server.enqueue(body = ok) // comment

        client.createTemplate(CreateTemplateRequest.builder().templateCode("C1").templateName("n").templateContent("c").build())
        client.updateTemplate("C1", UpdateTemplateRequest.builder().templateName("n").templateContent("c").build())
        client.getTemplate("C1")
        client.listTemplates(TemplateSearchCondition.all())
        client.listTemplateModifications("C1")
        client.addTemplateComment("C1", "문의")

        assertThat(server.requests.map { it.method to it.path }).containsExactly(
            "POST" to prefix,
            "PUT" to "$prefix/C1",
            "GET" to "$prefix/C1",
            "GET" to prefix,
            "GET" to "$prefix/C1/modifications",
            "POST" to "$prefix/C1/comments",
        )
    }

    @Test
    fun `경로 변수는 인코딩되어 요청 경로를 바꾸지 못한다`() {
        server.enqueue(body = ok)

        client.deleteTemplate("SENDER", "../../other?x=1#frag 한글")

        assertThat(server.requests.single().target)
            .isEqualTo("$prefix/..%2F..%2Fother%3Fx%3D1%23frag%20%ED%95%9C%EA%B8%80")
    }

    @Test
    fun `카테고리 조회는 senderKey 없이 GET 하고 중첩 응답을 파싱한다`() {
        server.enqueue(
            body = """
                {"header":{"resultCode":0,"resultMessage":"SUCCESS","isSuccessful":true},
                 "categories":[
                   {"name":"서비스이용","subCategories":[
                      {"code":"001001","name":"이용안내/신청","groupName":"회원","inclusion":"가입 안내","exclusion":null,"unknownField":1},
                      {"code":"001002","name":"사용방법/기능"}]},
                   {"name":"기타","subCategories":null}
                 ]}
            """.trimIndent(),
        )

        val categories = client.listTemplateCategories()

        val req = server.requests.single()
        assertThat(req.method).isEqualTo("GET")
        assertThat(req.path).isEqualTo("/alimtalk/v2.3/appkeys/APPKEY/template/categories")
        assertThat(req.query).isEmpty()
        assertThat(req.headers["x-secret-key"]).isEqualTo("SECRET")
        assertThat(categories).hasSize(2)
        assertThat(categories[0].name).isEqualTo("서비스이용")
        assertThat(categories[0].subCategories.map { it.code }).containsExactly("001001", "001002")
        assertThat(categories[0].subCategories[0].inclusion).isEqualTo("가입 안내")
        assertThat(categories[0].subCategories[0].exclusion).isNull()
        assertThat(categories[0].subCategories[1].groupName).isNull()
        assertThat(categories[1].subCategories).isEmpty()
    }

    @Test
    fun `카테고리 응답이 빈 배열이면 빈 목록, 본문이 없으면 ClientException`() {
        val head = """"header":{"resultCode":0,"resultMessage":"SUCCESS","isSuccessful":true}"""
        server.enqueue(body = """{$head,"categories":[]}""")
        server.enqueue(body = """{$head}""")
        server.enqueue(body = """{$head,"categories":null}""")

        assertThat(client.listTemplateCategories()).isEmpty()
        assertThatThrownBy { client.listTemplateCategories() }.isInstanceOf(AlimtalkClientException::class.java)
        assertThatThrownBy { client.listTemplateCategories() }.isInstanceOf(AlimtalkClientException::class.java)
    }

    @Test
    fun `isSuccessful false 이면 AlimtalkApiException`() {
        server.enqueue(body = """{"header":{"resultCode":-1006,"resultMessage":"Template not found","isSuccessful":false}}""")

        assertThatThrownBy { client.deleteTemplate("ORDER_01") }
            .isInstanceOfSatisfying(AlimtalkApiException::class.java) {
                assertThat(it.resultCode).isEqualTo(-1006)
                assertThat(it.resultMessage).isEqualTo("Template not found")
                assertThat(it.httpStatus).isEqualTo(200)
            }
    }

    @Test
    fun `HTTP 4xx 에서도 header 사유를 예외로 올린다`() {
        server.enqueue(
            status = 401,
            body = """{"header":{"resultCode":-4001,"resultMessage":"Invalid secret key","isSuccessful":false}}""",
        )

        assertThatThrownBy { client.deleteTemplate("ORDER_01") }
            .isInstanceOfSatisfying(AlimtalkApiException::class.java) {
                assertThat(it.resultCode).isEqualTo(-4001)
                assertThat(it.httpStatus).isEqualTo(401)
            }
    }

    @Test
    fun `JSON 이 아닌 5xx 응답은 UNKNOWN 코드의 ApiException`() {
        server.enqueue(status = 502, body = "<html>bad gateway</html>", contentType = "text/html")

        assertThatThrownBy { client.deleteTemplate("ORDER_01") }
            .isInstanceOfSatisfying(AlimtalkApiException::class.java) {
                assertThat(it.resultCode).isEqualTo(AlimtalkApiException.UNKNOWN_RESULT_CODE)
                assertThat(it.httpStatus).isEqualTo(502)
            }
    }

    @Test
    fun `200 인데 본문이 JSON 이 아니면 ClientException`() {
        server.enqueue(body = "<html>not json</html>", contentType = "text/html")

        assertThatThrownBy { client.deleteTemplate("ORDER_01") }.isInstanceOf(AlimtalkClientException::class.java)
    }

    @Test
    fun `성공 응답에 header 가 없으면 ClientException`() {
        server.enqueue(body = "{}")

        assertThatThrownBy { client.deleteTemplate("ORDER_01") }.isInstanceOf(AlimtalkClientException::class.java)
    }

    @Test
    fun `연결 실패는 AlimtalkClientException 으로 감싸고 원인을 보존한다`() {
        val closed = FakeAlimtalkServer()
        val deadConfig = AlimtalkClientConfig("APPKEY", "SECRET", closed.baseUrl, "SENDER")
        closed.close()

        assertThatThrownBy { JdkHttpAlimtalkTemplateClient(deadConfig).deleteTemplate("ORDER_01") }
            .isInstanceOf(AlimtalkClientException::class.java)
            .hasCauseInstanceOf(IOException::class.java)
            .message().doesNotContain("SECRET")
    }

    @Test
    fun `응답이 readTimeout 보다 늦으면 ClientException`() {
        server.enqueue(body = ok, delayMillis = 1500)
        val slow = JdkHttpAlimtalkTemplateClient(config(readTimeout = Duration.ofMillis(300)))

        assertThatThrownBy { slow.deleteTemplate("ORDER_01") }
            .isInstanceOf(AlimtalkClientException::class.java)
            .hasCauseInstanceOf(HttpTimeoutException::class.java)
    }

    @Test
    fun `리다이렉트를 따라가지 않아 시크릿 헤더가 다른 곳으로 나가지 않는다`() {
        FakeAlimtalkServer().use { target ->
            target.enqueue(body = ok)
            repeat(2) { server.enqueue(status = 302, body = null, contentType = null, headers = mapOf("Location" to "${target.baseUrl}/other")) }

            val defaultClient = JdkHttpAlimtalkTemplateClient(config())
            // 커스터마이저가 리다이렉트를 켜도 마지막에 덮어쓴다.
            val customized = JdkHttpAlimtalkTemplateClient(config(), Consumer { it.followRedirects(HttpClient.Redirect.ALWAYS) })

            listOf(defaultClient, customized).forEach { c ->
                assertThatThrownBy { c.deleteTemplate("ORDER_01") }
                    .isInstanceOf(AlimtalkClientException::class.java)
                    .hasMessageContaining("redirect")
                    .hasMessageContaining("302")
            }
            assertThat(target.requests).describedAs("리다이렉트 대상에 도달한 요청").isEmpty()
        }
    }

    @Test
    fun `httpClientCustomizer 는 한 번 호출된다`() {
        val calls = AtomicInteger()
        JdkHttpAlimtalkTemplateClient(config(), Consumer { calls.incrementAndGet() })
        assertThat(calls.get()).isEqualTo(1)
    }

    @Test
    fun `기본 senderKey 가 없으면 IllegalStateException`() {
        val noDefault = JdkHttpAlimtalkTemplateClient(AlimtalkClientConfig("APPKEY", "SECRET", server.baseUrl))
        assertThatThrownBy { noDefault.deleteTemplate("ORDER_01") }.isInstanceOf(IllegalStateException::class.java)
    }

    @Test
    fun `요청 builder 는 필수값과 길이를 검증한다`() {
        assertThatThrownBy { CreateTemplateRequest.builder().templateName("n").templateContent("c").build() }
            .hasMessageContaining("templateCode")
        assertThatThrownBy { CreateTemplateRequest.builder().templateCode("X".repeat(21)).templateName("n").templateContent("c").build() }
            .hasMessageContaining("at most 20")
        assertThatThrownBy { UpdateTemplateRequest.builder().templateName("n").build() }
            .hasMessageContaining("templateContent")
        assertThatThrownBy { TemplateSearchCondition.builder().pageSize(1001).build() }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `baseUrl 은 https 만 허용하고 localhost 에 한해 http 를 허용한다`() {
        assertThatThrownBy { AlimtalkClientConfig("A", "S", "http://api.example.com") }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("https")
        assertThatThrownBy { AlimtalkClientConfig("A", "S", "ftp://api.example.com") }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { AlimtalkClientConfig("A", "S", "api.example.com") }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { AlimtalkClientConfig("A", "S", "http://localhost.evil.com") }
            .isInstanceOf(IllegalArgumentException::class.java)
        // 메시지에 URL 을 그대로 싣지 않는다
        assertThatThrownBy { AlimtalkClientConfig("A", "S", "http://user:pw@api.example.com") }
            .message().doesNotContain("pw")

        AlimtalkClientConfig("A", "S", "https://api.example.com")
        AlimtalkClientConfig("A", "S", "http://localhost:8080")
        AlimtalkClientConfig("A", "S", "http://127.0.0.1:8080")
        AlimtalkClientConfig("A", "S", "http://[::1]:8080")
    }

    @Test
    fun `타임아웃은 양수여야 한다`() {
        assertThatThrownBy { AlimtalkClientConfig("A", "S", readTimeout = Duration.ZERO) }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { AlimtalkClientConfig("A", "S", connectTimeout = Duration.ofSeconds(-1)) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `설정 toString 은 시크릿 키를 마스킹한다`() {
        assertThat(AlimtalkClientConfig("APPKEY", "TOP-SECRET").toString()).doesNotContain("TOP-SECRET")
    }
}
