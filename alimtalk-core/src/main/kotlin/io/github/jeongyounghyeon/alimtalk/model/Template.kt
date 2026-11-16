package io.github.jeongyounghyeon.alimtalk.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude

/** 템플릿 버튼. 요청과 응답에서 같이 쓴다. Java 에서는 [web] 같은 팩토리나 생성자 오버로드를 쓴다. */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public data class TemplateButton @JvmOverloads constructor(
    val ordering: Int,
    /** 버튼 유형 코드. 예: WL(웹 링크), AL(앱 링크), DS(배송 조회), BK(봇 키워드), MD(메시지 전달), AC(채널 추가), BC(상담톡 전환), BT(봇 전환) */
    val type: String,
    val name: String,
    val linkMo: String? = null,
    val linkPc: String? = null,
    val schemeIos: String? = null,
    val schemeAndroid: String? = null,
    val bizFormId: Int? = null,
    val pluginId: String? = null,
    val telNumber: String? = null,
) {
    public companion object {
        /** 웹 링크(WL) 버튼. */
        @JvmStatic
        @JvmOverloads
        public fun web(ordering: Int, name: String, linkMo: String, linkPc: String? = null): TemplateButton =
            TemplateButton(ordering = ordering, type = "WL", name = name, linkMo = linkMo, linkPc = linkPc)

        /** 채널 추가(AC) 버튼. AD/MI 유형 템플릿은 이 버튼이 첫 번째여야 한다. */
        @JvmStatic
        public fun addChannel(ordering: Int): TemplateButton =
            TemplateButton(ordering = ordering, type = "AC", name = "채널 추가")
    }
}

/** 퀵 리플라이. */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public data class TemplateQuickReply @JvmOverloads constructor(
    val ordering: Int,
    val type: String,
    val name: String,
    val linkMo: String? = null,
    val linkPc: String? = null,
    val schemeIos: String? = null,
    val schemeAndroid: String? = null,
    val bizFormId: Int? = null,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public data class TemplateItemEntry(val title: String, val description: String)

/** 아이템 리스트형 강조 템플릿의 아이템 정보. */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public data class TemplateItem @JvmOverloads constructor(
    /** 2~10개. 각 title 6자, description 23자 이내. */
    val list: List<TemplateItemEntry>? = null,
    val summary: TemplateItemEntry? = null,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public data class TemplateItemHighlight @JvmOverloads constructor(
    val title: String,
    val description: String,
    val imageUrl: String? = null,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public data class TemplateRepresentLink @JvmOverloads constructor(
    val linkMo: String? = null,
    val linkPc: String? = null,
    val schemeIos: String? = null,
    val schemeAndroid: String? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
public data class TemplateCommentAttachment(
    val originalFileName: String? = null,
    val filePath: String? = null,
)

/** 검수 문의/답변 댓글. */
@JsonIgnoreProperties(ignoreUnknown = true)
public data class TemplateComment(
    val id: Long? = null,
    val content: String? = null,
    val userName: String? = null,
    val createdAt: String? = null,
    val attachment: List<TemplateCommentAttachment>? = null,
    val status: String? = null,
)

/**
 * 템플릿 조회 결과. 조회 API 별로 내려오지 않는 필드가 있어 대부분 nullable 이다.
 * 알 수 없는 필드는 무시한다(NHN 이 필드를 추가해도 깨지지 않도록).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public data class Template(
    val plusFriendId: String? = null,
    val senderKey: String? = null,
    val plusFriendType: String? = null,
    val templateCode: String? = null,
    val kakaoTemplateCode: String? = null,
    val templateName: String? = null,
    val templateMessageType: String? = null,
    val templateEmphasizeType: String? = null,
    val templateContent: String? = null,
    val templateExtra: String? = null,
    val templateAd: String? = null,
    val templateTitle: String? = null,
    val templateSubtitle: String? = null,
    val templateHeader: String? = null,
    val templateItem: TemplateItem? = null,
    val templateItemHighlight: TemplateItemHighlight? = null,
    val templateRepresentLink: TemplateRepresentLink? = null,
    val templateImageName: String? = null,
    val templateImageUrl: String? = null,
    val buttons: List<TemplateButton> = emptyList(),
    val quickReplies: List<TemplateQuickReply> = emptyList(),
    val comments: List<TemplateComment> = emptyList(),
    /** 검수 상태 코드. 값 체계는 API 버전에 따라 다를 수 있어 문자열 그대로 둔다. */
    val status: String? = null,
    val statusName: String? = null,
    val securityFlag: Boolean? = null,
    val categoryCode: String? = null,
    val block: Boolean? = null,
    val dormant: Boolean? = null,
    /** 수정 이력 조회에서만 내려온다. */
    val activated: Boolean? = null,
    val createDate: String? = null,
    val updateDate: String? = null,
)

/** 목록 조회 결과. */
public data class TemplatePage(
    val templates: List<Template>,
    val totalCount: Int,
)
