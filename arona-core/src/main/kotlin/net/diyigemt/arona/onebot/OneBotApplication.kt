package net.diyigemt.arona.onebot

import com.google.gson.JsonObject
import java.util.concurrent.CopyOnWriteArrayList

class OneBotApplication(
  val config: OneBotConfig,
  private val eventHandler: OneBotBusinessHandler,
) {
  private val connections = CopyOnWriteArrayList<OneBotConnection>()
  private val httpApiServers = CopyOnWriteArrayList<OneBotHttpApiServer>()

  private fun runtimeConfig(name: String, endpoint: ConnectionConfig) = OneBotConnectionConfig(
    name = name,
    type = ConnectionType.fromName(name),
    endpoint = endpoint,
  )

  fun start() {
    config.connections.filterValues { it.enable }.forEach { (name, endpoint) ->
      val runtime = runtimeConfig(name, endpoint)
      println("[OneBot] 启动连接 ${runtime.type.displayName}: ${address(runtime)}")
      runCatching {
        when (runtime.type) {
          ConnectionType.WEBSOCKET -> startConnection(OneBotWsForwardConnection(runtime, eventHandler))
          ConnectionType.WEBSOCKET_REVERSE -> startConnection(OneBotWsReverseConnection(runtime, eventHandler))
          ConnectionType.HTTP -> {
            val server = OneBotHttpApiServer(runtime, eventHandler)
            server.start()
            httpApiServers += server
          }
          ConnectionType.HTTP_REVERSE -> startConnection(OneBotHttpReverseConnection(runtime, eventHandler))
        }
      }.onFailure { println("[OneBot] ${runtime.type.displayName} 启动失败: ${it.message}") }
    }
  }

  private fun address(runtime: OneBotConnectionConfig): String = when (runtime.type) {
    ConnectionType.WEBSOCKET, ConnectionType.HTTP_REVERSE -> runtime.endpoint.url
    ConnectionType.WEBSOCKET_REVERSE, ConnectionType.HTTP ->
      "${runtime.endpoint.host}:${runtime.endpoint.port}${runtime.endpoint.path}"
  }

  private fun startConnection(connection: OneBotConnection) {
    connections += connection
    runCatching { connection.start() }
      .onFailure { connections -= connection; throw it }
  }

  fun stop() {
    connections.forEach { runCatching { it.stop() } }
    connections.clear()
    httpApiServers.forEach { it.stop() }
    httpApiServers.clear()
    (eventHandler as? AutoCloseable)?.close()
  }

  fun firstConnection(): OneBotConnection? = connections.firstOrNull()

  fun send(action: OneBotAction) = firstConnection()?.send(action)

  fun broadcast(event: JsonObject) {
    connections.forEach { it.broadcast(event) }
  }

  fun broadcastExcept(event: JsonObject, except: OneBotConnection?) {
    connections.forEach { if (it !== except) it.broadcast(event) }
  }
}
