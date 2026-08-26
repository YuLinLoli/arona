package net.diyigemt.arona.onebot

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

abstract class AbstractOneBotConnection(
  protected val config: OneBotConnectionConfig,
  private val eventHandler: OneBotEventHandler,
) : OneBotConnection {
  protected val pending = ConcurrentHashMap<String, CompletableFuture<OneBotActionResponse?>>()
  protected val scheduler: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { task ->
    Thread(task, "arona-onebot-heartbeat").apply { isDaemon = true }
  }
  @Volatile
  private var heartbeatScheduled = false

  protected fun receive(payload: String) {
    val parsed = OneBotProtocol.parsePayload(payload) ?: return
    when (parsed) {
      is ParsedPayload.Event -> eventHandler.onEvent(parsed.value, this)
      is ParsedPayload.Action -> handleAction(parsed.value)
      is ParsedPayload.Response -> parsed.value.echo?.let { pending.remove(it)?.complete(parsed.value) }
    }
  }

  private fun handleAction(action: OneBotAction) {
    val business = eventHandler as? OneBotBusinessHandler ?: return
    business.handleAction(action, this).whenComplete { data, error ->
      val response = JsonObject().apply {
        addProperty("status", if (error == null) "ok" else "failed")
        addProperty("retcode", if (error == null) 0 else 1)
        add("data", data ?: com.google.gson.JsonNull.INSTANCE)
        if (error != null) addProperty("message", error.message ?: "action failed")
        addProperty("echo", action.echo)
      }
      sendRaw(response.toString())
    }
  }

  protected fun scheduleHeartbeat() {
    if (config.heartbeatInterval <= 0 || heartbeatScheduled) return
    heartbeatScheduled = true
    scheduler.scheduleAtFixedRate({
      runCatching {
        val params = JsonObject().apply {
          addProperty("status", true)
          addProperty("good", true)
        }
        send(OneBotProtocol.action("get_status", params))
      }
    }, config.heartbeatInterval, config.heartbeatInterval, TimeUnit.MILLISECONDS)
  }

  protected fun parseJson(payload: String): JsonObject? = runCatching {
    JsonParser.parseString(payload).asJsonObject
  }.onFailure { println("[OneBot invalid JSON] ${it.message}: $payload") }.getOrNull()

  override fun broadcast(event: JsonObject) = sendRaw(event.toString())

  protected abstract fun sendRaw(payload: String)

  override fun stop() { scheduler.shutdownNow() }
}
