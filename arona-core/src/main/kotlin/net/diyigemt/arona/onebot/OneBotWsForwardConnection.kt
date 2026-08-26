package net.diyigemt.arona.onebot

import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import java.net.URI
import java.util.concurrent.CompletableFuture

class OneBotWsForwardConnection(
  config: EndpointConfig,
  handler: OneBotEventHandler,
) : AbstractOneBotConnection(config, handler) {
  private var client: WebSocketClient? = null

  override fun start() {
    require(config.url.isNotBlank()) { "WS forward url must not be blank" }
    connect()
  }

  private fun connect() {
    val headers = mutableMapOf<String, String>()
    if (config.token.isNotBlank()) headers["Authorization"] = "Bearer ${config.token}"
    client = object : WebSocketClient(URI(config.url), headers) {
      override fun onOpen(handshakedata: ServerHandshake) {
        println("[OneBot WS] forward connected: ${config.url}")
        scheduleHeartbeat()
      }
      override fun onMessage(message: String) = receive(message)
      override fun onClose(code: Int, reason: String, remote: Boolean) {
        println("[OneBot WS] forward closed: $code $reason")
        if (config.reconnectInterval > 0) scheduler.schedule({ runCatching { connect() } }, config.reconnectInterval, java.util.concurrent.TimeUnit.MILLISECONDS)
      }
      override fun onError(ex: Exception) { println("[OneBot WS] forward error: ${ex.message}") }
    }
    client!!.connect()
  }

  override fun send(action: OneBotAction): CompletableFuture<OneBotActionResponse?> {
    val result = CompletableFuture<OneBotActionResponse?>()
    pending[action.echo] = result
    val current = client
    if (current == null || !current.isOpen) {
      pending.remove(action.echo)
      result.completeExceptionally(IllegalStateException("WS forward is not connected"))
    } else current.send(OneBotProtocol.serialize(action))
    return result
  }

  override fun sendRaw(payload: String) { client?.takeIf { it.isOpen }?.send(payload) }
  override fun stop() { super.stop(); client?.close() }
}
