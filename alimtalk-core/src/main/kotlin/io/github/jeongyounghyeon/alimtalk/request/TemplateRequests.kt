package io.github.jeongyounghyeon.alimtalk.request

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonUnwrapped
import io.github.jeongyounghyeon.alimtalk.model.TemplateStatus

/** 템플릿 등록 요청. `CreateTemplateRequest.builder()` 로 만든다. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CreateTemplateRequest private constructor(
    public val templateCode: String,
    @get:JsonUnwrapped public val body: TemplateBody,
) {
    public class Builder internal constructor() : TemplateBodyBuilder<Builder>() {
        private var templateCode: String? = null

        /** 필수. 20자 이내. */
        public fun templateCode(value: String): Builder = apply { templateCode = value }

        public fun build(): CreateTemplateRequest {
            val code = requireNotNull(templateCode?.takeIf { it.isNotBlank() }) { "templateCode is required" }
            require(code.length <= MAX_CODE) { "templateCode must be at most $MAX_CODE characters" }
            return CreateTemplateRequest(code, buildBody())
        }
    }

    public companion object {
        private const val MAX_CODE = 20

        @JvmStatic
        public fun builder(): Builder = Builder()
    }
}

/** 템플릿 수정 요청. 수정 대상 코드는 호출 시 경로로 전달하므로 본문에는 없다. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UpdateTemplateRequest private constructor(
    @get:JsonUnwrapped public val body: TemplateBody,
) {
    public class Builder internal constructor() : TemplateBodyBuilder<Builder>() {
        public fun build(): UpdateTemplateRequest = UpdateTemplateRequest(buildBody())
    }

    public companion object {
        @JvmStatic
        public fun builder(): Builder = Builder()
    }
}

/** 템플릿 목록 조회 조건. 모두 선택이며 `pageNum` 기본 1, `pageSize` 기본 15(최대 1,000). */
public class TemplateSearchCondition private constructor(
    public val templateCode: String?,
    public val templateName: String?,
    public val templateStatus: TemplateStatus?,
    public val pageNum: Int?,
    public val pageSize: Int?,
) {
    public class Builder internal constructor() {
        private var templateCode: String? = null
        private var templateName: String? = null
        private var templateStatus: TemplateStatus? = null
        private var pageNum: Int? = null
        private var pageSize: Int? = null

        public fun templateCode(value: String): Builder = apply { templateCode = value }

        public fun templateName(value: String): Builder = apply { templateName = value }

        public fun templateStatus(value: TemplateStatus): Builder = apply { templateStatus = value }

        public fun pageNum(value: Int): Builder = apply { pageNum = value }

        public fun pageSize(value: Int): Builder = apply { pageSize = value }

        public fun build(): TemplateSearchCondition {
            pageNum?.let { require(it >= 1) { "pageNum must be >= 1" } }
            pageSize?.let { require(it in 1..MAX_PAGE_SIZE) { "pageSize must be in 1..$MAX_PAGE_SIZE" } }
            return TemplateSearchCondition(templateCode, templateName, templateStatus, pageNum, pageSize)
        }
    }

    public companion object {
        private const val MAX_PAGE_SIZE = 1000

        @JvmStatic
        public fun builder(): Builder = Builder()

        /** 조건 없이 첫 페이지. */
        @JvmStatic
        public fun all(): TemplateSearchCondition = builder().build()
    }
}
