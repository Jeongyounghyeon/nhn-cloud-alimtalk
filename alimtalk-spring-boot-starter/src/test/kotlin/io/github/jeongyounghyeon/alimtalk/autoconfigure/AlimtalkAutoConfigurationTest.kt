package io.github.jeongyounghyeon.alimtalk.autoconfigure

import com.sun.net.httpserver.HttpServer
import io.github.jeongyounghyeon.alimtalk.AlimtalkClientConfig
import io.github.jeongyounghyeon.alimtalk.AlimtalkTemplateClient
import io.github.jeongyounghyeon.alimtalk.exception.AlimtalkException
import io.github.jeongyounghyeon.alimtalk.internal.JdkHttpAlimtalkTemplateClient
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.http.HttpClient
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class AlimtalkAutoConfigurationTest {
    private val runner = ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(AlimtalkAutoConfiguration::class.java))

    @Test
    fun `app-key 와 secret-key 가 있으면 클라이언트를 등록한다`() {
        runner.withPropertyValues("nhn.alimtalk.app-key=A", "nhn.alimtalk.secret-key=S")
            .run { ctx -> assertThat(ctx).hasSingleBean(AlimtalkTemplateClient::class.java) }
    }

    @Test
    fun `설정이 없으면 등록하지 않는다`() {
        runner.run { ctx -> assertThat(ctx).doesNotHaveBean(AlimtalkTemplateClient::class.java) }
    }

    @Test
    fun `secret-key 가 없으면 등록하지 않는다`() {
        runner.withPropertyValues("nhn.alimtalk.app-key=A")
            .run { ctx -> assertThat(ctx).doesNotHaveBean(AlimtalkTemplateClient::class.java) }
    }

    @Test
    fun `앱이 직접 정의한 빈이 우선한다`() {
        runner.withPropertyValues("nhn.alimtalk.app-key=A", "nhn.alimtalk.secret-key=S")
            .withUserConfiguration(CustomClientConfig::class.java)
            .run { ctx ->
                assertThat(ctx).hasSingleBean(AlimtalkTemplateClient::class.java)
                assertThat(ctx.getBean(AlimtalkTemplateClient::class.java)).isSameAs(CustomClientConfig.CLIENT)
            }
    }

    @Test
    fun `프로퍼티가 바인딩되고 기본값이 적용된다`() {
        runner.withPropertyValues(
            "nhn.alimtalk.app-key=A",
            "nhn.alimtalk.secret-key=S",
            "nhn.alimtalk.default-sender-key=SK",
            "nhn.alimtalk.read-timeout=30s",
        ).run { ctx ->
            val props = ctx.getBean(AlimtalkProperties::class.java)
            assertThat(props.defaultSenderKey).isEqualTo("SK")
            assertThat(props.readTimeout.seconds).isEqualTo(30)
            assertThat(props.connectTimeout.seconds).isEqualTo(3)
            assertThat(props.baseUrl).contains("nhncloudservice.com")
        }
    }

    @Test
    fun `http 인 base-url 은 컨텍스트 시작 시 거부한다`() {
        runner.withPropertyValues(
            "nhn.alimtalk.app-key=A",
            "nhn.alimtalk.secret-key=S",
            "nhn.alimtalk.base-url=http://api.example.com",
        ).run { ctx -> assertThat(ctx).hasFailed() }
    }

    @Test
    fun `AlimtalkHttpClientCustomizer 빈이 적용된다`() {
        CustomizerConfig.calls.set(0)
        runner.withPropertyValues("nhn.alimtalk.app-key=A", "nhn.alimtalk.secret-key=S")
            .withUserConfiguration(CustomizerConfig::class.java)
            .run { ctx ->
                assertThat(ctx).hasSingleBean(AlimtalkTemplateClient::class.java)
                assertThat(CustomizerConfig.calls.get()).isEqualTo(1)
            }
    }

    @Test
    fun `리다이렉트를 따라가지 않아 시크릿 헤더가 다른 곳으로 나가지 않는다`() {
        val leaked = AtomicReference<String?>(null)
        val loopback = InetAddress.getLoopbackAddress()
        val target = HttpServer.create(InetSocketAddress(loopback, 0), 0).apply {
            createContext("/") { ex ->
                leaked.set(ex.requestHeaders.getFirst("X-Secret-Key"))
                ex.sendResponseHeaders(200, -1)
                ex.close()
            }
            start()
        }
        val redirector = HttpServer.create(InetSocketAddress(loopback, 0), 0).apply {
            createContext("/") { ex ->
                ex.responseHeaders.add("Location", "http://127.0.0.1:${target.address.port}/other")
                ex.sendResponseHeaders(302, -1)
                ex.close()
            }
            start()
        }
        try {
            runner.withPropertyValues(
                "nhn.alimtalk.app-key=A",
                "nhn.alimtalk.secret-key=TOP-SECRET",
                "nhn.alimtalk.default-sender-key=S",
                "nhn.alimtalk.base-url=http://127.0.0.1:${redirector.address.port}",
            )
                // 커스터마이저가 리다이렉트를 켜려 해도 따라가지 않아야 한다.
                .withUserConfiguration(FollowRedirectsCustomizerConfig::class.java)
                .run { ctx ->
                    val client = ctx.getBean(AlimtalkTemplateClient::class.java)
                    try {
                        client.deleteTemplate("T")
                    } catch (_: AlimtalkException) {
                        // 3xx 는 본문(header)이 없어 예외가 나는 것이 정상이다.
                    }
                    assertThat(leaked.get()).describedAs("redirect 대상으로 전달된 X-Secret-Key").isNull()
                }
        } finally {
            redirector.stop(0)
            target.stop(0)
        }
    }

    @Configuration(proxyBeanMethods = false)
    open class CustomClientConfig {
        @Bean
        open fun custom(): AlimtalkTemplateClient = CLIENT

        companion object {
            val CLIENT: AlimtalkTemplateClient = JdkHttpAlimtalkTemplateClient(AlimtalkClientConfig("X", "Y"))
        }
    }

    @Configuration(proxyBeanMethods = false)
    open class CustomizerConfig {
        @Bean
        open fun customizer(): AlimtalkHttpClientCustomizer =
            AlimtalkHttpClientCustomizer { _: HttpClient.Builder -> calls.incrementAndGet() }

        companion object {
            val calls = AtomicInteger()
        }
    }

    @Configuration(proxyBeanMethods = false)
    open class FollowRedirectsCustomizerConfig {
        @Bean
        open fun customizer(): AlimtalkHttpClientCustomizer =
            AlimtalkHttpClientCustomizer { b: HttpClient.Builder -> b.followRedirects(HttpClient.Redirect.ALWAYS) }
    }
}
