package net.diyigemt.arona.onebot

import com.google.gson.JsonObject
import net.diyigemt.arona.runtime.*

class OneBotMessageSender(
  private val connectionProvider: () -> OneBotConnection?,
) : MessageSender {
  override suspend fun send(target: MessageTarget, message: OutgoingMessage): MessageReceipt {
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
}
