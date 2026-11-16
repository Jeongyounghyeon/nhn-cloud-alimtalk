package io.github.jeongyounghyeon.alimtalk.model

/** 템플릿 메시지 유형. */
public enum class TemplateMessageType {
    /** 기본형 */
    BA,

    /** 부가정보형 */
    EX,

    /** 광고 추가형 */
    AD,

    /** 복합형 */
    MI,
}

/** 템플릿 강조 유형. 수정 API 는 [ITEM_LIST] 를 지원하지 않는다. */
public enum class TemplateEmphasizeType {
    NONE,
    TEXT,
    IMAGE,
    ITEM_LIST,
}

/** 템플릿 검수 상태 코드 (목록 조회 필터용). */
public enum class TemplateStatus(public val code: String) {
    REQUESTED("TSC01"),
    REVIEWING("TSC02"),
    APPROVED("TSC03"),
    REJECTED("TSC04"),
}
