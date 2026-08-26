package net.diyigemt.arona.onebot

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import net.diyigemt.arona.runtime.*

class OneBotMessageSender(
  private val connectionProvider: () -> OneBotConnection?,
  private val selfId: Long = 0,
) : MessageSender {
  override suspend fun send(target: MessageTarget, message: OutgoingMessage): MessageReceipt {
    OneBotConsole.printOutgoing(selfId, target, message)
    val forward = message.segments.filterIsInstance<MessageSegment.Forward>().firstOrNull()
    return if (forward != null) sendForward(target, forward)
    else sendNormal(target, message)
  }

  private suspend fun sendNormal(target: MessageTarget, message: OutgoingMessage): MessageReceipt {
    val params = JsonObject().apply {
      when (target) {
        is MessageTarget.Group -> addProperty("group_id", target.id)
        is MessageTarget.Private -> addProperty("user_id", target.id)
      }
      add("message", OneBotProtocol.messageToJson(message))
    }
    val actionName = when (target) {
      is MessageTarget.Group -> "send_group_msg"
      is MessageTarget.Private -> "send_private_msg"
    }
    val response = connectionProvider()?.send(OneBotProtocol.action(actionName, params))?.get()
    val messageId = response?.data?.takeIf { it.isJsonObject }?.asJsonObject?.get("message_id")?.asLong
    return MessageReceipt(messageId)
  }

  /** 合并转发: 走 send_forward_msg; 部分 OneBot 实现不支持时降级为平铺的普通消息 */
  private suspend fun sendForward(target: MessageTarget, forward: MessageSegment.Forward): MessageReceipt {
    val connection = connectionProvider() ?: return MessageReceipt(null)
    val params = JsonObject().apply {
      when (target) {
        is MessageTarget.Group -> addProperty("group_id", target.id)
        is MessageTarget.Private -> addProperty("user_id", target.id)
      }
      val messages = JsonArray()
      forward.messages.forEach { node ->
        messages.add(JsonObject().apply {
          addProperty("type", "node")
          add("data", JsonObject().apply {
            addProperty("name", node.name)
            addProperty("uin", node.uin)
            add("content", OneBotProtocol.messageToJson(OutgoingMessage(node.content)))
          })
        })
      }
      add("messages", messages)
    }
    val response = runCatching { connection.send(OneBotProtocol.action("send_forward_msg", params))?.get() }.getOrNull()
    val ok = response?.status == "ok" || (response?.retcode ?: -1) == 0
    if (ok) {
      val messageId = response?.data?.takeIf { it.isJsonObject }?.asJsonObject?.get("message_id")?.asLong
      return MessageReceipt(messageId)
    }
    // 降级: 平铺节点内容作为普通消息发送
    return sendNormal(target, OutgoingMessage(forward.messages.flatMap { it.content }))
  }
}