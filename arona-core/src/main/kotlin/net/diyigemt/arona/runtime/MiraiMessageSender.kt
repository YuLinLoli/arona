package net.diyigemt.arona.runtime

import net.mamoe.mirai.Bot

class MiraiMessageSender(
  private val botProvider: () -> Bot?,
) : MessageSender {
  override suspend fun send(target: MessageTarget, message: OutgoingMessage): MessageReceipt {
    val bot = botProvider() ?: error("Mirai bot is not online")
    val text = message.segments.joinToString("") { segment ->
      when (segment) {
        is MessageSegment.Text -> segment.value
        is MessageSegment.Image -> segment.url ?: segment.file ?: ""
      }
    }
    val receipt = when (target) {
      is MessageTarget.Group -> bot.getGroup(target.id)?.sendMessage(text)
      is MessageTarget.Private -> bot.getFriend(target.id)?.sendMessage(text)
    }
    return MessageReceipt(receipt?.source?.ids?.firstOrNull()?.toLong())
  }
}
