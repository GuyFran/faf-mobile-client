package com.faforever.mobile.chat.model

data class ChatChannel(
    val name: String,
    val topic: String = "",
    val users: MutableList<ChatUser> = mutableListOf(),
    val messages: MutableList<ChatMessage> = mutableListOf(),
    val unreadCount: Int = 0,
)

data class ChatUser(
    val name: String,
    val elevation: UserElevation = UserElevation.NORMAL,
    val isAway: Boolean = false,
)

data class ChatMessage(
    val id: String = "",
    val sender: String,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val type: MessageType = MessageType.MESSAGE,
)

enum class MessageType {
    MESSAGE,
    ACTION,
    SYSTEM,
}

enum class UserElevation {
    OWNER,
    ADMIN,
    OPERATOR,
    HALF_OP,
    VOICE,
    NORMAL,
}

fun Char.toElevation(): UserElevation = when (this) {
    '~' -> UserElevation.OWNER
    '&' -> UserElevation.ADMIN
    '@' -> UserElevation.OPERATOR
    '%' -> UserElevation.HALF_OP
    '+' -> UserElevation.VOICE
    else -> UserElevation.NORMAL
}
