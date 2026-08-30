package net.diyigemt.arona.runtime

interface MessageSender {
  suspend fun send(target: MessageTarget, message: OutgoingMessage): MessageReceipt
}

data class MessageReceipt(val messageId: Long? = null)

data class OutgoingMessage(
  val segments: List<MessageSegment>,
  /** 发送成功后延迟自动撤回的毫秒数(独立模式通过 OneBot delete_msg 实现), null 表示不撤回 */
  val revokeAfterMillis: Long? = null,
) {
  companion object {
    fun text(value: String) = OutgoingMessage(listOf(MessageSegment.Text(value)))
    fun at(userId: Long) = OutgoingMessage(listOf(MessageSegment.At(userId)))
    fun image(file: String) = OutgoingMessage(listOf(MessageSegment.Image(file = file)))
    fun image(data: ByteArray) = OutgoingMessage(listOf(MessageSegment.Image(data = data)))
    fun forward(title: String, messages: List<ForwardMessage>) = OutgoingMessage(listOf(MessageSegment.Forward(title, messages)))
  }

  operator fun plus(other: OutgoingMessage) = OutgoingMessage(
    segments + other.segments,
    revokeAfterMillis = revokeAfterMillis ?: other.revokeAfterMillis,
  )
}

sealed interface MessageSegment {
  data class Text(val value: String) : MessageSegment
  data class At(val userId: Long) : MessageSegment
  data class Image(val url: String? = null, val file: String? = null, val data: ByteArray? = null) : MessageSegment
  /** 合并转发消息 */
  data class Forward(val title: String, val messages: List<ForwardMessage>) : MessageSegment
}

/** 合并转发中的一条节点消息 */
data class ForwardMessage(
  val name: String,
  val uin: Long,
  val content: List<MessageSegment>,
)
