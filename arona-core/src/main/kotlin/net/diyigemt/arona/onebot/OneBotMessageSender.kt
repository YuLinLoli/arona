package net.diyigemt.arona.onebot

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.diyigemt.arona.runtime.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class OneBotMessageSender(
  private val connectionProvider: () -> OneBotConnection?,
  private val selfId: Long = 0,
) : MessageSender {
  private val revokeScheduler = Executors.newSingleThreadScheduledExecutor { task ->
    Thread(task, "arona-onebot-revoke").apply { isDaemon = true }
  }

  override suspend fun send(target: MessageTarget, message: OutgoingMessage): MessageReceipt {
    OneBotConsole.printOutgoing(selfId, target, message)
    val forward = message.segments.filterIsInstance<MessageSegment.Forward>().firstOrNull()
    val receipt = if (forward != null) sendForward(target, forward)
    else sendNormal(target, message)
    scheduleRevoke(receipt, message.revokeAfterMillis)
    return receipt
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
    val response = awaitResponse(connectionProvider()?.send(OneBotProtocol.action(actionName, params)))
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
    val response = awaitResponse(connection.send(OneBotProtocol.action("send_forward_msg", params)))
    val ok = response?.status == "ok" || (response?.retcode ?: -1) == 0
    if (ok) {
      val messageId = response?.data?.takeIf { it.isJsonObject }?.asJsonObject?.get("message_id")?.asLong
      return MessageReceipt(messageId)
    }
    // 降级: 平铺节点内容作为普通消息发送
    return sendNormal(target, OutgoingMessage(forward.messages.flatMap { it.content }))
  }

  /** 等待 OneBot 动作响应, 15 秒超时, 超时/异常一律视为失败返回 null */
  private suspend fun awaitResponse(future: CompletableFuture<OneBotActionResponse?>?): OneBotActionResponse? {
    if (future == null) return null
    return withContext(Dispatchers.IO) {
      runCatching { future.get(15, TimeUnit.SECONDS) }.getOrNull()
    }
  }

  /** 需要撤回的消息在延迟后通过 OneBot delete_msg 动作撤回 */
  private fun scheduleRevoke(receipt: MessageReceipt, revokeAfterMillis: Long?) {
    val messageId = receipt.messageId ?: return
    val delay = revokeAfterMillis ?: return
    if (delay <= 0) return
    revokeScheduler.schedule({
      runCatching {
        val params = JsonObject().apply { addProperty("message_id", messageId) }
        connectionProvider()?.send(OneBotProtocol.action("delete_msg", params))
      }
    }, delay, TimeUnit.MILLISECONDS)
  }
}
