package net.diyigemt.arona.onebot

import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import java.util.concurrent.CompletableFuture

interface OneBotBusinessHandler : OneBotEventHandler {
  fun handleAction(action: OneBotAction, connection: OneBotConnection): CompletableFuture<JsonObject?> =
    CompletableFuture.completedFuture(null)
}

class StandaloneBusinessHandler(
  private val config: OneBotConfig,
) : OneBotBusinessHandler {
  override fun onEvent(event: OneBotEvent, connection: OneBotConnection) {
    val text = OneBotProtocol.extractText(event).trim()
    if (text.isEmpty()) return
    println("[Arona standalone message] $text")
    val reply = when (text) {
      "/arona", "/状态", "/arona status" -> "Arona standalone is running."
      "/帮助", "/arona help" -> "Arona commands: /arona status"
      else -> return
    }
    val params = JsonObject().apply {
      when {
        event.groupId != null -> {
          addProperty("group_id", event.groupId)
          add("message", OneBotProtocol.messageToJson(net.diyigemt.arona.runtime.OutgoingMessage.text(reply)))
        }
        event.userId != null -> {
          addProperty("user_id", event.userId)
          add("message", OneBotProtocol.messageToJson(net.diyigemt.arona.runtime.OutgoingMessage.text(reply)))
        }
      }
    }
    val action = if (event.groupId != null) "send_group_msg" else "send_private_msg"
    connection.send(OneBotProtocol.action(action, params))
  }

  override fun handleAction(action: OneBotAction, connection: OneBotConnection): CompletableFuture<JsonObject?> {
    val data = JsonObject()
    when (action.action) {
      "get_status" -> {
        data.addProperty("online", true)
        data.addProperty("good", true)
      }
      "get_version_info" -> {
        data.addProperty("app_name", "arona")
        data.addProperty("app_version", "standalone")
        data.addProperty("protocol_version", "11")
      }
      "get_login_info" -> {
        data.addProperty("user_id", config.selfId)
        data.addProperty("nickname", config.nickname)
      }
      else -> return CompletableFuture.completedFuture(null)
    }
    return CompletableFuture.completedFuture(data)
  }
}
