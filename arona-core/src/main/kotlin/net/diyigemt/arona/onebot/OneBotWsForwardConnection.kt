package net.diyigemt.arona.onebot

import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import java.net.URI
import java.util.concurrent.CompletableFuture

class OneBotWsForwardConnection(
  config: OneBotConnectionConfig,
  handler: OneBotEventHandler,
) : AbstractOneBotConnection(config, handler) {
  private var client: WebSocketClient? = null
  private val retry = OneBotRetryPolicy(config.reconnectInterval)
  @Volatile
  private var stopped = false

  override fun start() {
    require(config.endpoint.url.isNotBlank()) { "WebSocket url must not be blank" }
    connect()
  }

  private fun connect() {
    if (stopped) return
    val headers = mutableMapOf<String, String>()
    if (config.token.isNotBlank()) headers["Authorization"] = "Bearer ${config.token}"
    val ws = object : WebSocketClient(URI(config.endpoint.url), headers) {
      override fun onOpen(handshakedata: ServerHandshake) {
        retry.onSuccess()
        println("[OneBot WS] 已连接: ${config.endpoint.url}")
        scheduleHeartbeat()
      }
      override fun onMessage(message: String) = receive(message)
      override fun onClose(code: Int, reason: String, remote: Boolean) {
        println("[OneBot WS] 连接已断开: $code $reason")
        if (!stopped) retry.onFailure(scheduler, "WS 正向", this@OneBotWsForwardConnection::connect)
      }
      override fun onError(ex: Exception) {
        println("[OneBot WS] 连接错误: ${ex.message}")
        if (!isOpen && !stopped) retry.onFailure(scheduler, "WS 正向", this@OneBotWsForwardConnection::connect)
      }
    }
    client = ws
    runCatching { ws.connect() }.onFailure {
      println("[OneBot WS] 连接失败: ${it.message}")
      if (!stopped) retry.onFailure(scheduler, "WS 正向", this@OneBotWsForwardConnection::connect)
    }
  }

  override fun send(action: OneBotAction): CompletableFuture<OneBotActionResponse?> {
    val result = CompletableFuture<OneBotActionResponse?>()
    val current = client
    if (current == null || !current.isOpen) {
      result.completeExceptionally(IllegalStateException("WebSocket is not connected"))
      return result
    }
    trackPending(action.echo, result)
    current.send(OneBotProtocol.serialize(action))
    return result
  }

  override fun sendRaw(payload: String) { client?.takeIf { it.isOpen }?.send(payload) }

  override fun stop() {
    stopped = true
    super.stop()
    runCatching { client?.close() }
  }
}
