package io.github.jeongyounghyeon.alimtalk.autoconfigure

import io.github.jeongyounghyeon.alimtalk.AlimtalkClientConfig
import io.github.jeongyounghyeon.alimtalk.AlimtalkTemplateClient
import io.github.jeongyounghyeon.alimtalk.internal.JdkHttpAlimtalkTemplateClient
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import java.util.function.Consumer

/**
 * `nhn.alimtalk.app-key` 와 `nhn.alimtalk.secret-key` 가 모두 있을 때만 [AlimtalkTemplateClient] 를 등록한다.
 * 앱이 같은 타입의 빈을 직접 정의하면 그쪽이 우선한다.
 *
 * Spring Boot 3.1 에서도 쓸 수 있도록 Boot 3.1 에 있는 API 만 사용한다.
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "nhn.alimtalk", name = ["app-key", "secret-key"])
@EnableConfigurationProperties(AlimtalkProperties::class)
public class AlimtalkAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean(AlimtalkTemplateClient::class)
    public fun alimtalkTemplateClient(
        properties: AlimtalkProperties,
        customizers: ObjectProvider<AlimtalkHttpClientCustomizer>,
    ): AlimtalkTemplateClient {
        val config = AlimtalkClientConfig(
            appKey = requireNotNull(properties.appKey),
            secretKey = requireNotNull(properties.secretKey),
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
