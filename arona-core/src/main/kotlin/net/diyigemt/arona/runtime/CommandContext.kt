package net.diyigemt.arona.runtime

data class CommandContext(
  val userId: Long,
  val groupId: Long?,
  val text: String,
  val senderName: String?,
  val isAdmin: Boolean,
  val messageSender: MessageSender,
) {
  val target: MessageTarget
    get() = groupId?.let(MessageTarget::Group) ?: MessageTarget.Private(userId)

  suspend fun reply(message: OutgoingMessage): MessageReceipt = messageSender.send(target, message)

  suspend fun reply(text: String): MessageReceipt = reply(OutgoingMessage.text(text))
}
