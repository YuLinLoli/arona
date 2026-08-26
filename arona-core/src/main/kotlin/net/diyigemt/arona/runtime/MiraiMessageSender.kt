package net.diyigemt.arona.runtime

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.mamoe.mirai.Bot
import net.mamoe.mirai.contact.Contact
import net.mamoe.mirai.message.data.At
import net.mamoe.mirai.message.data.MessageChainBuilder
import net.mamoe.mirai.utils.ExternalResource.Companion.toExternalResource
import java.io.File
import java.net.URL

class MiraiMessageSender(
  private val botProvider: () -> Bot?,
) : MessageSender {
  override suspend fun send(target: MessageTarget, message: OutgoingMessage): MessageReceipt {
    val bot = botProvider() ?: error("Mirai bot is not online")
    val contact = target.resolve(bot)
    if (contact == null) {
      // 目标群/好友不可达(被移出群/群解散/删除好友)是常态业务场景,
      // 记录 warning 而非抛异常, 避免中断群广播并刷异常栈
      RuntimeLog.warning("Mirai target is unavailable: ${target.id}")
      return MessageReceipt(null)
    }
    val chain = MessageChainBuilder().apply {
      message.segments.flatMap { segment ->
        // 合并转发段: 展开为普通内容(插件模式使用独立转发逻辑, 这里只是兜底)
        if (segment is MessageSegment.Forward) segment.messages.flatMap { it.content } else listOf(segment)
      }.forEach { segment ->
        when (segment) {
          is MessageSegment.Text -> append(segment.value)
          is MessageSegment.At -> append(At(segment.userId))
          is MessageSegment.Forward -> {}
          is MessageSegment.Image -> {
            val bytes = segment.loadBytes()
            bytes.toExternalResource().use { resource -> append(contact.uploadImage(resource)) }
          }
        }
      }
    }.build()
    val receipt = contact.sendMessage(chain)
    return MessageReceipt(receipt.source.ids.firstOrNull()?.toLong())
  }

  private fun MessageTarget.resolve(bot: Bot): Contact? = when (this) {
    is MessageTarget.Group -> bot.getGroup(id)
    is MessageTarget.Private -> bot.getFriend(id)
  }

  private suspend fun MessageSegment.Image.loadBytes(): ByteArray = withContext(Dispatchers.IO) {
    data ?: file?.let { File(it.removePrefix("file://")).readBytes() }
      ?: url?.let { URL(it).openStream().use { stream -> stream.readBytes() } }
      ?: error("Image segment has no data, file, or URL")
  }
}
