package io.github.jeongyounghyeon.alimtalk.autoconfigure

import io.github.jeongyounghyeon.alimtalk.AlimtalkClientConfig
import io.github.jeongyounghyeon.alimtalk.AlimtalkTemplateClient
import io.github.jeongyounghyeon.alimtalk.internal.JdkHttpAlimtalkTemplateClient
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Conditional
import java.util.function.Consumer

/**
 * [AlimtalkTemplateClient] 를 자동 등록한다.
 *
 * - `nhn.alimtalk.app-key` 와 `nhn.alimtalk.secret-key` 가 **둘 다 없거나 비어 있으면** 빈을 만들지 않고 정상 기동한다.
 *   (`${MY_KEY:}` 처럼 환경 변수가 없을 때 빈 값을 두는 설정을 그대로 쓸 수 있다.)
 * - **둘 다 값이 있으면** 빈을 만든다.
 * - **한쪽만 값이 있으면** 설정 오류일 가능성이 높으므로 기동을 실패시킨다.
 * - 값이 `${VAR}` 처럼 **치환되지 않은 플레이스홀더**로 남아 있으면 기동을 실패시킨다.
 *   Spring Boot 는 `@ConfigurationProperties` 바인딩에서 치환하지 못한 `${VAR}` 를 글자 그대로 값으로 넘기므로,
 *   막지 않으면 키가 `${VAR}` 라는 문자열인 채로 클라이언트가 만들어져 호출 때에야 인증 오류가 난다.
 * - 앱이 같은 타입의 빈을 직접 정의하면 그쪽이 우선한다.
 *
 * 환경 변수 이름은 라이브러리가 정하지 않는다. Spring Boot 가 프로퍼티 이름에서 만든 이름
 * (예: `NHN_ALIMTALK_APP_KEY` → `nhn.alimtalk.app-key`)으로 바인딩한다.
 *
 * Spring Boot 3.1 에서도 쓸 수 있도록 Boot 3.1 에 있는 API 만 사용한다.
 */
@AutoConfiguration
@Conditional(AlimtalkConfiguredCondition::class)
@EnableConfigurationProperties(AlimtalkProperties::class)
public class AlimtalkAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean(AlimtalkTemplateClient::class)
    public fun alimtalkTemplateClient(
        properties: AlimtalkProperties,
        customizers: ObjectProvider<AlimtalkHttpClientCustomizer>,
    ): AlimtalkTemplateClient {
        failOnUnresolvedPlaceholder("nhn.alimtalk.app-key", properties.appKey)
        failOnUnresolvedPlaceholder("nhn.alimtalk.secret-key", properties.secretKey)
        failOnUnresolvedPlaceholder("nhn.alimtalk.base-url", properties.baseUrl)
        failOnUnresolvedPlaceholder("nhn.alimtalk.default-sender-key", properties.defaultSenderKey)
        val appKey = properties.appKey?.takeIf { it.isNotBlank() }
        val secretKey = properties.secretKey?.takeIf { it.isNotBlank() }
        if (appKey == null || secretKey == null) {
            val blank = if (appKey == null) "nhn.alimtalk.app-key" else "nhn.alimtalk.secret-key"
            throw IllegalStateException(
                "nhn.alimtalk.app-key and nhn.alimtalk.secret-key must be set together, but $blank is blank or missing. " +
                    "Set both, or leave both empty to disable the AlimTalk client.",
            )
        }
        val config = AlimtalkClientConfig(
            appKey = appKey,
            secretKey = secretKey,
            baseUrl = properties.baseUrl,
            defaultSenderKey = properties.defaultSenderKey,
            connectTimeout = properties.connectTimeout,
            readTimeout = properties.readTimeout,
        )
        return JdkHttpAlimtalkTemplateClient(
            config,
            Consumer { builder -> customizers.orderedStream().forEach { it.customize(builder) } },
        )
    }
}

private val UNRESOLVED_PLACEHOLDER = Regex("\\$\\{[^}]*}")

/**
 * 값에 치환되지 않은 `${...}` 가 남아 있으면 실패시킨다. 메시지에는 플레이스홀더 부분만 싣고
 * 값 전체는 싣지 않는다(일부가 비밀 값일 수 있다).
 */
private fun failOnUnresolvedPlaceholder(property: String, value: String?) {
    val placeholder = value?.let { UNRESOLVED_PLACEHOLDER.find(it)?.value } ?: return
    throw IllegalStateException(
        "$property has an unresolved placeholder '$placeholder'. Define the environment variable it refers to, " +
            "or give the placeholder a default such as '\${VAR:}'. " +
            "Environment variables like NHN_ALIMTALK_APP_KEY are bound to nhn.alimtalk.app-key automatically, so no placeholder is needed.",
    )
}
