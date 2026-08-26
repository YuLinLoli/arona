package net.diyigemt.arona.onebot

import com.google.gson.JsonObject
import java.util.concurrent.CopyOnWriteArrayList

class OneBotApplication(
  val config: OneBotConfig,
  private val eventHandler: OneBotBusinessHandler = StandaloneBusinessHandler(config),
) {
  private val connections = CopyOnWriteArrayList<OneBotConnection>()
  private var httpApiServer: OneBotHttpApiServer? = null

  fun start() {
    startWs(config.ws)
    startWs(config.wsReverse)
    if (config.http.enable) {
      if (config.http.type == ConnectionType.FORWARD) {
        httpApiServer = OneBotHttpApiServer(config.http, eventHandler)
        runCatching { httpApiServer?.start() }
          .onFailure { println("[OneBot] failed to start HTTP API: ${it.message}") }
      }
    }
    if (config.httpReverse.enable) {
      val connection = OneBotHttpReverseConnection(config.httpReverse, eventHandler)
      connections += connection
      runCatching { connection.start() }
        .onFailure { connections -= connection; println("[OneBot] failed to start reverse HTTP: ${it.message}") }
    }
  }

  private fun startWs(endpoint: EndpointConfig) {
    if (!endpoint.enable) return
    val connection = if (endpoint.type == ConnectionType.REVERSE) {
      OneBotWsReverseConnection(endpoint, eventHandler)
    } else {
      OneBotWsForwardConnection(endpoint, eventHandler)
    }
    connections += connection
    runCatching { connection.start() }
      .onFailure { connections -= connection; println("[OneBot] failed to start WS: ${it.message}") }
  }

  fun stop() {
    connections.forEach { runCatching { it.stop() } }
    connections.clear()
    httpApiServer?.stop()
    httpApiServer = null
  }

  fun send(action: OneBotAction) = connections.firstOrNull()?.send(action)

  fun broadcast(event: JsonObject) {
    connections.forEach { it.broadcast(event) }
  }
}
