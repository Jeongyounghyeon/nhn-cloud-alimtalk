package io.github.jeongyounghyeon.alimtalk.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

/** 템플릿 카테고리 대분류. 하위 카테고리의 [TemplateSubCategory.code] 를 `categoryCode` 로 쓴다. */
@JsonIgnoreProperties(ignoreUnknown = true)
public data class TemplateCategory(
    val name: String? = null,
    val subCategories: List<TemplateSubCategory> = emptyList(),
)

/** 템플릿 카테고리 하위 항목. */
@JsonIgnoreProperties(ignoreUnknown = true)
public data class TemplateSubCategory(
    /** 템플릿 등록/수정 요청의 `categoryCode` 에 넣는 값. */
    val code: String? = null,
    val name: String? = null,
    val groupName: String? = null,
    /** 이 카테고리에 해당하는 템플릿 설명. */
    val inclusion: String? = null,
    /** 이 카테고리에 해당하지 않는 템플릿 설명. */
    val exclusion: String? = null,
)
