package net.diyigemt.arona.onebot

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class OneBotHttpApiServer(
  private val config: EndpointConfig,
  private val handler: OneBotBusinessHandler,
) {
  private var server: HttpServer? = null
  private val connection = object : OneBotConnection {
    override fun start() = Unit
    override fun stop() = Unit
    override fun send(action: OneBotAction) = java.util.concurrent.CompletableFuture.completedFuture<OneBotActionResponse?>(null)
    override fun broadcast(event: JsonObject) = Unit
  }

  fun start() {
    server = HttpServer.create(InetSocketAddress(config.host, config.port), 0).apply {
      createContext("/") { exchange -> handle(exchange) }
      executor = Executors.newCachedThreadPool()
      start()
    }
    println("[OneBot HTTP] forward API listening: ${config.host}:${config.port}")
  }

  private fun handle(exchange: HttpExchange) {
    exchange.use {
      if (!authorized(exchange)) {
        reply(exchange, 401, failure(1403, "unauthorized"))
        return
      }
      val body = String(exchange.requestBody.readAllBytes(), StandardCharsets.UTF_8)
      println("[OneBot HTTP request] ${exchange.requestMethod} ${exchange.requestURI} $body")
      val json = runCatching { if (body.isBlank()) JsonObject() else JsonParser.parseString(body).asJsonObject }.getOrElse {
        reply(exchange, 400, failure(10001, "invalid json"))
        return
      }
      val action = json.get("action")?.asString ?: exchange.requestURI.path.trim('/').ifBlank { "get_status" }
      val params = json.getAsJsonObject("params") ?: JsonObject()
      val echo = json.get("echo")?.asString ?: ""
      val result = handler.handleAction(OneBotAction(action, params, echo), connection).get(30, TimeUnit.SECONDS)
      val response = JsonObject().apply {
        addProperty("status", "ok")
        addProperty("retcode", 0)
        add("data", result ?: com.google.gson.JsonNull.INSTANCE)
        addProperty("echo", echo)
      }
      reply(exchange, 200, response.toString())
    }
  }

  private fun authorized(exchange: HttpExchange): Boolean {
    if (config.token.isBlank()) return true
    return exchange.requestHeaders.getFirst("Authorization") == "Bearer ${config.token}"
  }

  private fun failure(code: Int, message: String) = "{\"status\":\"failed\",\"retcode\":$code,\"data\":null,\"message\":\"$message\"}"

  private fun reply(exchange: HttpExchange, status: Int, body: String) {
    val bytes = body.toByteArray(StandardCharsets.UTF_8)
    exchange.responseHeaders.set("Content-Type", "application/json; charset=utf-8")
    exchange.sendResponseHeaders(status, bytes.size.toLong())
    exchange.responseBody.use { it.write(bytes) }
  }

  fun stop() { server?.stop(0) }
}
