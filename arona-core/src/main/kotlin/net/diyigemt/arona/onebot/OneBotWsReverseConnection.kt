package net.diyigemt.arona.onebot

import org.java_websocket.WebSocket
import org.java_websocket.handshake.ClientHandshake
import org.java_websocket.server.WebSocketServer
import java.net.InetSocketAddress
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList

class OneBotWsReverseConnection(
  config: OneBotConnectionConfig,
  handler: OneBotEventHandler,
) : AbstractOneBotConnection(config, handler) {
  private val clients = CopyOnWriteArrayList<WebSocket>()
  private var server: WebSocketServer? = null
  private val retry = OneBotRetryPolicy(config.reconnectInterval)
  @Volatile
  private var stopped = false

  override fun start() {
    startServer()
  }

  private fun startServer() {
    if (stopped) return
    val address = InetSocketAddress(config.endpoint.host, config.endpoint.port)
    val next = object : WebSocketServer(address) {
      override fun onOpen(conn: WebSocket, handshake: ClientHandshake) {
        val authorization = handshake.getFieldValue("Authorization")
        if (config.token.isNotBlank() && authorization != "Bearer ${config.token}") {
          conn.close(1008, "Unauthorized")
          return
        }
        clients += conn
        println("[OneBot reverse WS] 客户端已连接: ${conn.remoteSocketAddress}")
      }
      override fun onMessage(conn: WebSocket, message: String) = receive(message)
      override fun onClose(conn: WebSocket, code: Int, reason: String, remote: Boolean) {
        clients -= conn
        println("[OneBot reverse WS] 客户端断开: $code $reason")
      }
      override fun onError(conn: WebSocket?, ex: Exception) {
        println("[OneBot reverse WS] 错误: ${ex.message}")
        if (conn == null && !stopped) retry.onFailure(scheduler, "WS 反向", ::startServer)
      }
      override fun onStart() {
        retry.onSuccess()
        println("[OneBot reverse WS] 已启动: $address")
        scheduleHeartbeat()
      }
    }
    server = next
    runCatching { next.start() }.onFailure { error ->
      println("[OneBot reverse WS] 启动失败: ${error.message}")
      if (!stopped) retry.onFailure(scheduler, "WS 反向", ::startServer)
    }
  }

  override fun send(action: OneBotAction): CompletableFuture<OneBotActionResponse?> {
    val result = CompletableFuture<OneBotActionResponse?>()
    val opened = clients.filter { it.isOpen }
    if (opened.isEmpty()) {
      result.completeExceptionally(IllegalStateException("Reverse WebSocket has no clients"))
      return result
    }
    pending[action.echo] = result
    val payload = OneBotProtocol.serialize(action)
    opened.forEach { it.send(payload) }
    return result
  }

  override fun sendRaw(payload: String) { clients.filter { it.isOpen }.forEach { it.send(payload) } }

  override fun stop() {
    stopped = true
    super.stop()
    runCatching { server?.stop() }
  }
}
