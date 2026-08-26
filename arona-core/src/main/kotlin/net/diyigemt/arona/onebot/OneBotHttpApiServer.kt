package net.diyigemt.arona.onebot

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class OneBotHttpApiServer(
  private val config: OneBotConnectionConfig,
  private val handler: OneBotBusinessHandler,
) {
  private var server: HttpServer? = null
  private val retry = OneBotRetryPolicy(config.reconnectInterval)
  private val scheduler = Executors.newSingleThreadScheduledExecutor { task ->
    Thread(task, "arona-onebot-http-retry").apply { isDaemon = true }
  }
  @Volatile
  private var stopped = false

  private val connection = object : OneBotConnection {
    override fun start() = Unit
    override fun stop() = Unit
    override fun send(action: OneBotAction) = CompletableFuture.completedFuture<OneBotActionResponse?>(null)
    override fun broadcast(event: JsonObject) = Unit
  }

  fun start() {
    require(config.endpoint.port > 0) { "HTTP port must be positive" }
    bind()
  }

  private fun bind() {
    if (stopped) return
    val path = config.endpoint.path.ifBlank { "/" }
    try {
      server = HttpServer.create(InetSocketAddress(config.endpoint.host, config.endpoint.port), 0).apply {
        createContext(path) { exchange -> handle(exchange) }
        executor = Executors.newCachedThreadPool()
        start()
      }
      retry.onSuccess()
      println("[OneBot HTTP] 已启动: ${config.endpoint.host}:${config.endpoint.port}$path")
    } catch (error: Throwable) {
      println("[OneBot HTTP] 启动失败: ${error.message}")
      retry.onFailure(scheduler, "HTTP 正向", ::bind)
    }
  }

  private fun handle(exchange: HttpExchange) {
    exchange.use {
      if (!authorized(exchange)) {
        reply(exchange, 401, failure(1403, "unauthorized"))
        return
      }
      val body = String(exchange.requestBody.readBytes(), StandardCharsets.UTF_8)
      val json = runCatching { if (body.isBlank()) JsonObject() else JsonParser.parseString(body).asJsonObject }.getOrElse {
        reply(exchange, 400, failure(10001, "invalid json"))
        return
      }
      val action = json.get("action")?.asString ?: exchange.requestURI.path.trim('/').ifBlank { "get_status" }
      val params = json.getAsJsonObject("params") ?: JsonObject()
      val echo = json.get("echo")?.asString ?: ""
      val data = handler.handleAction(OneBotAction(action, params, echo), connection).get(30, TimeUnit.SECONDS)
      val response = JsonObject().apply {
        addProperty("status", "ok")
        addProperty("retcode", 0)
        add("data", data ?: com.google.gson.JsonNull.INSTANCE)
        addProperty("echo", echo)
      }
      reply(exchange, 200, response.toString())
    }
  }

  private fun authorized(exchange: HttpExchange): Boolean {
    if (config.token.isBlank()) return true
    return exchange.requestHeaders.getFirst("Authorization") == "Bearer ${config.token}"
  }

  private fun failure(code: Int, message: String) =
    "{\"status\":\"failed\",\"retcode\":$code,\"data\":null,\"message\":\"$message\"}"

  private fun reply(exchange: HttpExchange, status: Int, body: String) {
    val bytes = body.toByteArray(StandardCharsets.UTF_8)
    exchange.responseHeaders.set("Content-Type", "application/json; charset=utf-8")
    exchange.sendResponseHeaders(status, bytes.size.toLong())
    exchange.responseBody.use { it.write(bytes) }
  }

  fun stop() {
    stopped = true
    retry.reset()
    scheduler.shutdownNow()
    server?.stop(0)
  }
}
