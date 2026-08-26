package net.diyigemt.arona.runtime

interface MessageSender {
  suspend fun send(target: MessageTarget, message: OutgoingMessage): MessageReceipt
}

data class MessageReceipt(val messageId: Long? = null)

data class OutgoingMessage(val segments: List<MessageSegment>) {
  companion object {
    fun text(value: String) = OutgoingMessage(listOf(MessageSegment.Text(value)))
  }
}

sealed interface MessageSegment {
  data class Text(val value: String) : MessageSegment
  data class Image(val url: String? = null, val file: String? = null, val data: ByteArray? = null) : MessageSegment
}
