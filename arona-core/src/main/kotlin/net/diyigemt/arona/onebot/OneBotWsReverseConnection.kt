package net.diyigemt.arona.onebot

import org.java_websocket.WebSocket
import org.java_websocket.handshake.ClientHandshake
import org.java_websocket.server.WebSocketServer
import java.net.InetSocketAddress
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList

class OneBotWsReverseConnection(
  config: EndpointConfig,
  handler: OneBotEventHandler,
) : AbstractOneBotConnection(config, handler) {
  private val clients = CopyOnWriteArrayList<WebSocket>()
  private var server: WebSocketServer? = null

  override fun start() {
    val address = InetSocketAddress(config.host, config.port)
    server = object : WebSocketServer(address) {
      override fun onOpen(conn: WebSocket, handshake: ClientHandshake) {
        val authorization = handshake.getFieldValue("Authorization")
        if (config.token.isNotBlank() && authorization != "Bearer ${config.token}") {
          conn.close(1008, "Unauthorized")
          return
        }
        clients += conn
        println("[OneBot WS] reverse connected: ${conn.remoteSocketAddress}")
      }
      override fun onMessage(conn: WebSocket, message: String) = receive(message)
      override fun onClose(conn: WebSocket, code: Int, reason: String, remote: Boolean) {
        clients -= conn
        println("[OneBot WS] reverse closed: $code $reason")
      }
      override fun onError(conn: WebSocket?, ex: Exception) { println("[OneBot WS] reverse error: ${ex.message}") }
      override fun onStart() { println("[OneBot WS] reverse listening: $address") }
    }
    server!!.start()
  }

  override fun send(action: OneBotAction): CompletableFuture<OneBotActionResponse?> {
    val result = CompletableFuture<OneBotActionResponse?>()
    pending[action.echo] = result
    val payload = OneBotProtocol.serialize(action)
    clients.filter { it.isOpen }.forEach { it.send(payload) }
    if (clients.none { it.isOpen }) {
      pending.remove(action.echo)
      result.completeExceptionally(IllegalStateException("WS reverse has no clients"))
    }
    return result
  }

  override fun sendRaw(payload: String) { clients.filter { it.isOpen }.forEach { it.send(payload) } }
  override fun stop() { super.stop(); server?.stop() }
}
