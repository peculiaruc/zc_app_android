package com.tolstoy.zurichat.ui.dm_chat.model.response.member

data class MemberResponse(
    val `data`: Data,
    val message: String,
    val status: Int
)