package io.github.jeongyounghyeon.alimtalk.request

import com.fasterxml.jackson.annotation.JsonInclude
import io.github.jeongyounghyeon.alimtalk.model.TemplateButton
import io.github.jeongyounghyeon.alimtalk.model.TemplateEmphasizeType
import io.github.jeongyounghyeon.alimtalk.model.TemplateItem
import io.github.jeongyounghyeon.alimtalk.model.TemplateItemHighlight
import io.github.jeongyounghyeon.alimtalk.model.TemplateMessageType
import io.github.jeongyounghyeon.alimtalk.model.TemplateQuickReply
import io.github.jeongyounghyeon.alimtalk.model.TemplateRepresentLink

/** 템플릿 등록/수정 요청이 공통으로 가지는 본문. 직접 만들지 않고 각 요청의 builder 를 쓴다. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TemplateBody internal constructor(
    public val templateName: String,
    public val templateContent: String,
    public val templateMessageType: TemplateMessageType?,
    public val templateEmphasizeType: TemplateEmphasizeType?,
    public val templateExtra: String?,
    public val templateTitle: String?,
    public val templateSubtitle: String?,
    public val templateHeader: String?,
    public val templateItem: TemplateItem?,
    public val templateItemHighlight: TemplateItemHighlight?,
    public val templateRepresentLink: TemplateRepresentLink?,
    public val templateImageName: String?,
    public val templateImageUrl: String?,
    public val securityFlag: Boolean?,
    public val categoryCode: String?,
    public val buttons: List<TemplateButton>?,
    public val quickReplies: List<TemplateQuickReply>?,
)

/**
 * 등록/수정 builder 의 공통 부분. self-type 제네릭이라 Java 에서도 메서드 체이닝이 그대로 이어진다.
 */
@Suppress("TooManyFunctions")
public abstract class TemplateBodyBuilder<SELF : TemplateBodyBuilder<SELF>> internal constructor() {
    private var templateName: String? = null
    private var templateContent: String? = null
    private var templateMessageType: TemplateMessageType? = null
    private var templateEmphasizeType: TemplateEmphasizeType? = null
    private var templateExtra: String? = null
    private var templateTitle: String? = null
    private var templateSubtitle: String? = null
    private var templateHeader: String? = null
    private var templateItem: TemplateItem? = null
    private var templateItemHighlight: TemplateItemHighlight? = null
    private var templateRepresentLink: TemplateRepresentLink? = null
    private var templateImageName: String? = null
    private var templateImageUrl: String? = null
    private var securityFlag: Boolean? = null
    private var categoryCode: String? = null
    private var buttons: List<TemplateButton>? = null
    private var quickReplies: List<TemplateQuickReply>? = null

    @Suppress("UNCHECKED_CAST")
    private fun self(): SELF = this as SELF

    /** 필수. 150자 이내. */
    public fun templateName(value: String): SELF = apply { templateName = value }.let { self() }

    /** 필수. 1,300자 이내. 변수는 `#{변수명}` 형식. */
    public fun templateContent(value: String): SELF = apply { templateContent = value }.let { self() }

    public fun templateMessageType(value: TemplateMessageType): SELF = apply { templateMessageType = value }.let { self() }

    public fun templateEmphasizeType(value: TemplateEmphasizeType): SELF = apply { templateEmphasizeType = value }.let { self() }

    public fun templateExtra(value: String): SELF = apply { templateExtra = value }.let { self() }

    /** 강조 유형이 TEXT 일 때 필수. 50자 이내. */
    public fun templateTitle(value: String): SELF = apply { templateTitle = value }.let { self() }

    public fun templateSubtitle(value: String): SELF = apply { templateSubtitle = value }.let { self() }

    public fun templateHeader(value: String): SELF = apply { templateHeader = value }.let { self() }

    public fun templateItem(value: TemplateItem): SELF = apply { templateItem = value }.let { self() }

    public fun templateItemHighlight(value: TemplateItemHighlight): SELF = apply { templateItemHighlight = value }.let { self() }

    public fun templateRepresentLink(value: TemplateRepresentLink): SELF = apply { templateRepresentLink = value }.let { self() }

    /** 이미지는 별도 업로드 API 로 먼저 올려야 하며, v1 에서는 업로드를 지원하지 않는다. 이미 올린 이미지의 이름과 URL 만 지정할 수 있다. */
    public fun templateImage(name: String, url: String): SELF =
        apply {
            templateImageName = name
            templateImageUrl = url
        }.let { self() }

    public fun securityFlag(value: Boolean): SELF = apply { securityFlag = value }.let { self() }

    /**
     * 템플릿 카테고리 코드. [io.github.jeongyounghyeon.alimtalk.AlimtalkTemplateClient.listTemplateCategories] 가 돌려준
     * 하위 카테고리의 `code` 를 쓴다. 생략하면 "기타"(`999999`)로 등록되어 검수 우선순위가 가장 낮다.
     */
    public fun categoryCode(value: String): SELF = apply { categoryCode = value }.let { self() }

    /** 최대 5개. */
    public fun buttons(value: List<TemplateButton>): SELF = apply { buttons = value.toList() }.let { self() }

    /** 최대 5개. */
    public fun quickReplies(value: List<TemplateQuickReply>): SELF = apply { quickReplies = value.toList() }.let { self() }

    internal fun buildBody(): TemplateBody {
        val name = requireNotNull(templateName?.takeIf { it.isNotBlank() }) { "templateName is required" }
        val content = requireNotNull(templateContent?.takeIf { it.isNotBlank() }) { "templateContent is required" }
        require(name.length <= MAX_NAME) { "templateName must be at most $MAX_NAME characters" }
        require(content.length <= MAX_CONTENT) { "templateContent must be at most $MAX_CONTENT characters" }
        buttons?.let { require(it.size <= MAX_BUTTONS) { "buttons must be at most $MAX_BUTTONS" } }
        quickReplies?.let { require(it.size <= MAX_QUICK_REPLIES) { "quickReplies must be at most $MAX_QUICK_REPLIES" } }
        return TemplateBody(
            templateName = name,
            templateContent = content,
            templateMessageType = templateMessageType,
            templateEmphasizeType = templateEmphasizeType,
            templateExtra = templateExtra,
            templateTitle = templateTitle,
            templateSubtitle = templateSubtitle,
            templateHeader = templateHeader,
            templateItem = templateItem,
            templateItemHighlight = templateItemHighlight,
            templateRepresentLink = templateRepresentLink,
            templateImageName = templateImageName,
            templateImageUrl = templateImageUrl,
            securityFlag = securityFlag,
            categoryCode = categoryCode,
            buttons = buttons,
            quickReplies = quickReplies,
        )
    }

    private companion object {
        const val MAX_NAME = 150
        const val MAX_CONTENT = 1300
        const val MAX_BUTTONS = 5
        const val MAX_QUICK_REPLIES = 5
    }
}
