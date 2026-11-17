package io.github.jeongyounghyeon.alimtalk.autoconfigure

import org.apache.commons.logging.LogFactory
import org.springframework.boot.autoconfigure.condition.ConditionMessage
import org.springframework.boot.autoconfigure.condition.ConditionOutcome
import org.springframework.boot.autoconfigure.condition.SpringBootCondition
import org.springframework.boot.context.properties.bind.Binder
import org.springframework.context.annotation.ConditionContext
import org.springframework.core.type.AnnotatedTypeMetadata

/**
 * `nhn.alimtalk.app-key` 와 `nhn.alimtalk.secret-key` 중 **하나라도 값(공백이 아닌 문자)이 있을 때만** 일치한다.
 *
 * `@ConditionalOnProperty` 는 빈 문자열도 "있음"으로 보기 때문에, `app-key: ${MY_KEY:}` 처럼
 * 환경 변수가 없을 때 빈 값을 두는 흔한 설정에서 빈 생성이 시작되었다가 실패했다.
 * 둘 다 비었거나 없으면 클라이언트를 만들지 않고, 하나만 있으면 일치시켜서 빈 생성 단계에서
 * 두 키를 함께 설정하라는 메시지로 실패시킨다.
 *
 * 프로퍼티 바인딩과 같은 [Binder] 로 읽어서 `app-key`, `appKey`, `APP_KEY` 같은 표기를 같게 취급한다.
 */
internal class AlimtalkConfiguredCondition : SpringBootCondition() {
    override fun getMatchOutcome(context: ConditionContext, metadata: AnnotatedTypeMetadata): ConditionOutcome {
        val binder = Binder.get(context.environment)
        val appKey = binder.bind(APP_KEY, String::class.java).orElse(null)
        val secretKey = binder.bind(SECRET_KEY, String::class.java).orElse(null)
        val message = ConditionMessage.forCondition("NHN Cloud AlimTalk keys")

        if (!appKey.isNullOrBlank() || !secretKey.isNullOrBlank()) {
            return ConditionOutcome.match(message.because("$APP_KEY or $SECRET_KEY is set"))
        }
        LOG.info("NHN Cloud AlimTalk client is not created: $APP_KEY and $SECRET_KEY are not set. Set both to enable it.")
        return ConditionOutcome.noMatch(message.because("neither $APP_KEY nor $SECRET_KEY has a value"))
    }

    private companion object {
        const val APP_KEY = "nhn.alimtalk.app-key"
        const val SECRET_KEY = "nhn.alimtalk.secret-key"
        val LOG = LogFactory.getLog(AlimtalkConfiguredCondition::class.java)
    }
}
