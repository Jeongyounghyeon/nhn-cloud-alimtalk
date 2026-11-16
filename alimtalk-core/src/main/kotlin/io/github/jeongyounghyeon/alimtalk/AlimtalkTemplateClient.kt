package io.github.jeongyounghyeon.alimtalk

import io.github.jeongyounghyeon.alimtalk.model.Template
import io.github.jeongyounghyeon.alimtalk.model.TemplateCategory
import io.github.jeongyounghyeon.alimtalk.model.TemplatePage
import io.github.jeongyounghyeon.alimtalk.request.CreateTemplateRequest
import io.github.jeongyounghyeon.alimtalk.request.TemplateSearchCondition
import io.github.jeongyounghyeon.alimtalk.request.UpdateTemplateRequest

/**
 * NHN Cloud KakaoTalk Bizmessage 알림톡 템플릿 관리 클라이언트 (API v2.3).
 *
 * `senderKey` 를 받지 않는 오버로드는 설정의 `default-sender-key` 를 쓰며, 설정이 없으면
 * [IllegalStateException] 을 던진다. 실패는 모두 unchecked 인
 * [io.github.jeongyounghyeon.alimtalk.exception.AlimtalkException] 하위 예외다.
 */
public interface AlimtalkTemplateClient {
    /** 템플릿을 등록한다. 등록 후 검수가 진행된다. */
    public fun createTemplate(senderKey: String, request: CreateTemplateRequest)

    public fun createTemplate(request: CreateTemplateRequest)

    /** 템플릿을 수정한다. 승인된 템플릿은 수정하면 다시 검수된다. */
    public fun updateTemplate(senderKey: String, templateCode: String, request: UpdateTemplateRequest)

    public fun updateTemplate(templateCode: String, request: UpdateTemplateRequest)

    /** 템플릿을 삭제한다. 최근 3일간 발송 이력이 없는 템플릿만 삭제할 수 있다. */
    public fun deleteTemplate(senderKey: String, templateCode: String)

    public fun deleteTemplate(templateCode: String)

    public fun getTemplate(senderKey: String, templateCode: String): Template

    public fun getTemplate(templateCode: String): Template

    public fun listTemplates(senderKey: String, condition: TemplateSearchCondition): TemplatePage

    public fun listTemplates(condition: TemplateSearchCondition): TemplatePage

    /** 템플릿 수정 이력을 조회한다. */
    public fun listTemplateModifications(senderKey: String, templateCode: String): TemplatePage

    public fun listTemplateModifications(templateCode: String): TemplatePage

    /**
     * 템플릿 카테고리를 조회한다. 각 [io.github.jeongyounghyeon.alimtalk.model.TemplateSubCategory.code] 를
     * 등록/수정 요청의 `categoryCode` 로 쓴다. `categoryCode` 를 생략하면 "기타"(`999999`)로 등록되어 검수 우선순위가
     * 가장 낮다. 카테고리는 자주 바뀌지 않으므로 호출하는 쪽에서 캐시하는 것을 권한다.
     * `senderKey` 가 필요 없는 API 다.
     */
    public fun listTemplateCategories(): List<TemplateCategory>

    /** 검수 문의 댓글을 등록한다. 반려된 템플릿에 등록하면 재검수 요청이 된다. */
    public fun addTemplateComment(senderKey: String, templateCode: String, comment: String)

    public fun addTemplateComment(templateCode: String, comment: String)
}
