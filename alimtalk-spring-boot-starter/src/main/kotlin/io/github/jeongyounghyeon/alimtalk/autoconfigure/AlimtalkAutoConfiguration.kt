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
 *   (`${ALIMTALK_APP_KEY:}` 처럼 환경 변수가 없을 때 빈 값을 두는 설정을 그대로 쓸 수 있다.)
 * - **둘 다 값이 있으면** 빈을 만든다.
 * - **한쪽만 값이 있으면** 설정 오류일 가능성이 높으므로 기동을 실패시킨다.
 * - 앱이 같은 타입의 빈을 직접 정의하면 그쪽이 우선한다.
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
