package net.diyigemt.arona.onebot

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import net.diyigemt.arona.runtime.CommandContext
import net.diyigemt.arona.runtime.CommandDispatcher
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

interface OneBotBusinessHandler : OneBotEventHandler {
  fun handleAction(action: OneBotAction, connection: OneBotConnection): CompletableFuture<JsonObject?> =
    CompletableFuture.completedFuture(null)
}

class StandaloneBusinessHandler(
  private val config: OneBotConfig,
  private val commandDispatcher: CommandDispatcher,
  private val broadcast: (OneBotEvent, OneBotConnection) -> Unit = { _, _ -> },
) : OneBotBusinessHandler, AutoCloseable {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private val groupNames = ConcurrentHashMap<Long, String>()
  private val groupNameLoading = ConcurrentHashMap<Long, Boolean>()
  private val pendingGroupMessages = ConcurrentHashMap<Long, CopyOnWriteArrayList<OneBotEvent>>()

  override fun onEvent(event: OneBotEvent, connection: OneBotConnection) {
    broadcast(event, connection)
    if (event.postType != "message") return
    if (event.messageType == "group" && event.groupId != null) {
      printGroupMessage(event, connection)
    } else {
      OneBotConsole.printMessage(config.selfId, event, null)
    }
    val text = OneBotProtocol.extractText(event).trim()
    val userId = event.userId ?: return
    if (text.isEmpty()) return
    val isAdmin = userId in net.diyigemt.arona.runtime.RuntimeConfig.managers
    // 非服务群只放行管理员，方便管理员用 /config groups add 添加服务群
    if (!isAdmin && !isAllowedGroup(event.groupId)) return
    val senderName = event.sender?.get("card")?.asString?.takeIf(String::isNotBlank)
      ?: event.sender?.get("nickname")?.asString
    val context = CommandContext(
      userId = userId,
      groupId = event.groupId,
      text = text,
      senderName = senderName,
      isAdmin = isAdmin,
      messageSender = OneBotMessageSender({ connection }, config.selfId),
    )
    scope.launch {
      runCatching { commandDispatcher.dispatch(context) }
        .onFailure { error ->
          println("[Arona command error] ${error.message}")
          runCatching { context.reply("命令执行失败: ${error.message ?: "未知错误"}") }
        }
    }
  }

  private fun printGroupMessage(event: OneBotEvent, connection: OneBotConnection) {
    val groupId = event.groupId!!
    groupNames[groupId]?.let { name ->
      OneBotConsole.printMessage(config.selfId, event, name)
      return
    }
    event.raw.get("group_name")?.takeUnless(JsonElement::isJsonNull)?.asString?.takeIf(String::isNotBlank)?.let { name ->
      groupNames[groupId] = name
      OneBotConsole.printMessage(config.selfId, event, name)
      return
    }
    // 群名尚未缓存：先排队，获取到群名后再按统一格式打印
    pendingGroupMessages.computeIfAbsent(groupId) { CopyOnWriteArrayList() }.add(event)
    if (groupNameLoading.putIfAbsent(groupId, true) == null) {
      scope.launch { loadGroupName(groupId, connection) }
    }
  }

  private suspend fun loadGroupName(groupId: Long, connection: OneBotConnection) {
    val name = try {
      val params = JsonObject().apply { addProperty("group_id", groupId) }
      val response = connection.send(OneBotProtocol.action("get_group_info", params))
        .get(5, TimeUnit.SECONDS)
      response?.data?.takeIf { it.isJsonObject }?.asJsonObject?.get("group_name")?.asString
    } catch (error: Exception) {
      null
    } finally {
      groupNameLoading.remove(groupId)
    }
    groupNames[groupId] = name ?: groupId.toString()
    val pending = pendingGroupMessages.remove(groupId) ?: return
    pending.forEach { event ->
      OneBotConsole.printMessage(config.selfId, event, name)
    }
  }

  private fun isAllowedGroup(groupId: Long?): Boolean =
    groupId == null || net.diyigemt.arona.runtime.RuntimeConfig.groups.isEmpty() || groupId in net.diyigemt.arona.runtime.RuntimeConfig.groups

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

  override fun close() {
    scope.cancel()
  }
}
