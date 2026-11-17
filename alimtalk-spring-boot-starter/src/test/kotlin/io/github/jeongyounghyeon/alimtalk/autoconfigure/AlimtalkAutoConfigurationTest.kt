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
import org.springframework.boot.test.util.TestPropertyValues
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
    fun `둘 다 빈 값이면 기동에 성공하고 빈을 만들지 않는다`() {
        runner.withPropertyValues("nhn.alimtalk.app-key=", "nhn.alimtalk.secret-key=")
            .run { ctx ->
                assertThat(ctx).hasNotFailed()
                assertThat(ctx).doesNotHaveBean(AlimtalkTemplateClient::class.java)
            }
    }

    @Test
    fun `환경 변수가 없을 때 빈 값을 두는 플레이스홀더 설정으로도 기동에 성공한다`() {
        // application.yml 의 `app-key: ${NHN_ALIMTALK_APP_KEY:}` 와 같은 형태
        runner.withPropertyValues(
            "nhn.alimtalk.app-key=\${NHN_ALIMTALK_APP_KEY_NOT_DEFINED:}",
            "nhn.alimtalk.secret-key=\${NHN_ALIMTALK_SECRET_KEY_NOT_DEFINED:}",
            "nhn.alimtalk.base-url=\${NHN_ALIMTALK_BASE_URL_NOT_DEFINED:}",
            "nhn.alimtalk.default-sender-key=\${NHN_ALIMTALK_SENDER_KEY_NOT_DEFINED:}",
        ).run { ctx ->
            assertThat(ctx).hasNotFailed()
            assertThat(ctx).doesNotHaveBean(AlimtalkTemplateClient::class.java)
        }
    }

    @Test
    fun `공백만 있는 값도 빈 값으로 본다`() {
        runner.withPropertyValues("nhn.alimtalk.app-key=   ", "nhn.alimtalk.secret-key=   ")
            .run { ctx ->
                assertThat(ctx).hasNotFailed()
                assertThat(ctx).doesNotHaveBean(AlimtalkTemplateClient::class.java)
            }
    }

    @Test
    fun `한쪽만 설정되면 두 키를 함께 설정하라는 메시지로 기동이 실패한다`() {
        val together = "nhn.alimtalk.app-key and nhn.alimtalk.secret-key must be set together"
        // secret-key 가 없음, secret-key 가 빈 값, app-key 가 빈 값
        listOf(
            arrayOf("nhn.alimtalk.app-key=A") to "nhn.alimtalk.secret-key",
            arrayOf("nhn.alimtalk.app-key=A", "nhn.alimtalk.secret-key=") to "nhn.alimtalk.secret-key",
            arrayOf("nhn.alimtalk.app-key=", "nhn.alimtalk.secret-key=S") to "nhn.alimtalk.app-key",
            arrayOf("nhn.alimtalk.secret-key=S") to "nhn.alimtalk.app-key",
        ).forEach { (props, blankKey) ->
            runner.withPropertyValues(*props).run { ctx ->
                assertThat(ctx).hasFailed()
                assertThat(ctx).failure.rootCause()
                    .hasMessageContaining(together)
                    .hasMessageContaining("but $blankKey is blank or missing")
            }
        }
    }

    @Test
    fun `치환되지 않은 플레이스홀더가 남은 값은 기동을 실패시키고 메시지에 플레이스홀더만 싣는다`() {
        listOf(
            arrayOf("nhn.alimtalk.app-key=\${NHN_UNDEFINED_APP}", "nhn.alimtalk.secret-key=S") to "nhn.alimtalk.app-key",
            arrayOf("nhn.alimtalk.app-key=A", "nhn.alimtalk.secret-key=\${NHN_UNDEFINED_SECRET}") to "nhn.alimtalk.secret-key",
            arrayOf("nhn.alimtalk.app-key=A", "nhn.alimtalk.secret-key=S", "nhn.alimtalk.base-url=\${NHN_UNDEFINED_URL}") to "nhn.alimtalk.base-url",
            arrayOf("nhn.alimtalk.app-key=A", "nhn.alimtalk.secret-key=S", "nhn.alimtalk.default-sender-key=\${NHN_UNDEFINED_SENDER}") to "nhn.alimtalk.default-sender-key",
        ).forEach { (props, property) ->
            runner.withPropertyValues(*props).run { ctx ->
                assertThat(ctx).hasFailed()
                assertThat(ctx).failure.rootCause()
                    .hasMessageContaining("$property has an unresolved placeholder")
                    .hasMessageContaining("\${VAR:}")
            }
        }
        // 값의 일부만 플레이스홀더여도 막고, 메시지에는 값 전체(앞의 비밀 값)를 싣지 않는다.
        runner.withPropertyValues("nhn.alimtalk.app-key=SECRETPART\${NHN_UNDEFINED_APP}", "nhn.alimtalk.secret-key=S")
            .run { ctx ->
                assertThat(ctx).hasFailed()
                assertThat(ctx).failure.rootCause().message().contains("\${NHN_UNDEFINED_APP}").doesNotContain("SECRETPART")
            }
    }

    @Test
    fun `기본값이 있는 플레이스홀더는 치환된 값을 쓰고 달러 문자가 있는 키는 막지 않는다`() {
        runner.withPropertyValues("nhn.alimtalk.app-key=\${NHN_UNDEFINED_APP:fallback-key}", "nhn.alimtalk.secret-key=ab\$cd")
            .run { ctx ->
                assertThat(ctx).hasNotFailed()
                assertThat(ctx).hasSingleBean(AlimtalkTemplateClient::class.java)
                assertThat(ctx.getBean(AlimtalkProperties::class.java).appKey).isEqualTo("fallback-key")
            }
    }

    @Test
    fun `환경 변수 이름은 프로퍼티 이름에서 정해지고 yml 없이 환경 변수만으로 설정된다`() {
        runner.withInitializer { ctx ->
            TestPropertyValues.of(
                "NHN_ALIMTALK_APP_KEY=A",
                "NHN_ALIMTALK_SECRET_KEY=S",
                "NHN_ALIMTALK_DEFAULT_SENDER_KEY=SK",
                "NHN_ALIMTALK_READ_TIMEOUT=30s",
            ).applyTo(ctx.environment, TestPropertyValues.Type.SYSTEM_ENVIRONMENT)
        }.run { ctx ->
            assertThat(ctx).hasSingleBean(AlimtalkTemplateClient::class.java)
            val props = ctx.getBean(AlimtalkProperties::class.java)
            assertThat(props.defaultSenderKey).isEqualTo("SK")
            assertThat(props.readTimeout.seconds).isEqualTo(30)
        }
    }

    @Test
    fun `NHN 접두사가 없는 환경 변수는 읽지 않는다`() {
        runner.withInitializer { ctx ->
            TestPropertyValues.of("ALIMTALK_APP_KEY=A", "ALIMTALK_SECRET_KEY=S")
                .applyTo(ctx.environment, TestPropertyValues.Type.SYSTEM_ENVIRONMENT)
        }.run { ctx ->
            assertThat(ctx).hasNotFailed()
            assertThat(ctx).doesNotHaveBean(AlimtalkTemplateClient::class.java)
        }
    }

    @Test
    fun `base-url 이 빈 값이면 기본값을 쓰고 default-sender-key 가 빈 값이면 설정하지 않은 것으로 본다`() {
        runner.withPropertyValues(
            "nhn.alimtalk.app-key=A",
            "nhn.alimtalk.secret-key=S",
            "nhn.alimtalk.base-url=",
            "nhn.alimtalk.default-sender-key=",
        ).run { ctx ->
            assertThat(ctx).hasNotFailed()
            assertThat(ctx).hasSingleBean(AlimtalkTemplateClient::class.java)
            val props = ctx.getBean(AlimtalkProperties::class.java)
            assertThat(props.baseUrl).isEqualTo(AlimtalkClientConfig.DEFAULT_BASE_URL)
            assertThat(props.defaultSenderKey).isNull()
        }
    }

    @Test
    fun `키가 비어 있어도 사용자가 정의한 빈은 그대로 쓸 수 있다`() {
        runner.withPropertyValues("nhn.alimtalk.app-key=", "nhn.alimtalk.secret-key=")
            .withUserConfiguration(CustomClientConfig::class.java)
            .run { ctx ->
                assertThat(ctx).hasNotFailed()
                assertThat(ctx.getBean(AlimtalkTemplateClient::class.java)).isSameAs(CustomClientConfig.CLIENT)
            }
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
