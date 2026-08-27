package net.diyigemt.arona.onebot

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.diyigemt.arona.runtime.RuntimePaths
import net.mamoe.yamlkt.Yaml
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

@Serializable
data class OneBotConfig(
  @SerialName("self_id") val selfId: Long = 0,
  val nickname: String = "Arona",
  val connections: Map<String, ConnectionConfig> = defaultConnections(),
)


@Serializable
data class ConnectionConfig(
  val enable: Boolean = false,
  val host: String = "0.0.0.0",
  val port: Int = 0,
  val url: String = "",
  val path: String = "/",
  val token: String = "",
  @SerialName("heartbeat_interval") val heartbeatInterval: Long = 30_000,
  @SerialName("reconnect_interval") val reconnectInterval: Long = 5_000,
)

/**
 * 连接类型。配置文件里通过连接名称（键名）区分，不需要单独的 type 字段。
 */
enum class ConnectionType(val key: String, val displayName: String) {
  WEBSOCKET("ws-forward", "WebSocket 正向"),
  WEBSOCKET_REVERSE("ws-reverse", "WebSocket 反向"),
  HTTP("http", "HTTP 正向"),
  HTTP_REVERSE("http-reverse", "HTTP 反向");

  companion object {
    fun fromName(name: String): ConnectionType =
      values().firstOrNull { it.key == name }
        ?: throw IllegalArgumentException(
          "未知的连接类型: $name（可选: ${values().joinToString("、") { it.key }}）"
        )
  }
}

data class OneBotConnectionConfig(
  val name: String,
  val type: ConnectionType,
  val endpoint: ConnectionConfig,
) {
  val token: String get() = endpoint.token
  val heartbeatInterval: Long get() = endpoint.heartbeatInterval
  val reconnectInterval: Long get() = endpoint.reconnectInterval
}

private fun defaultConnections(): Map<String, ConnectionConfig> = linkedMapOf(
  "ws-forward" to ConnectionConfig(host = "127.0.0.1", url = "ws://127.0.0.1:6700"),
  "ws-reverse" to ConnectionConfig(port = 6701, path = "/onebot/v11"),
  "http" to ConnectionConfig(port = 5700, path = "/"),
  "http-reverse" to ConnectionConfig(url = "http://127.0.0.1:5701/onebot"),
)

object OneBotConfigLoader {
  fun load(file: Path = defaultFile()): OneBotConfig {
    Files.createDirectories(file.parent)
    if (Files.notExists(file)) {
      // 旧后缀 onebot.yaml 已存在则迁移
      val oldFile = file.parent.resolve("onebot.yaml")
      if (Files.exists(oldFile)) {
        runCatching { parse(oldFile) }.getOrNull()?.let { config ->
          save(file, config)
          println("[OneBot] 检测到旧版 onebot.yaml，已迁移到 ${file.fileName}")
          return config
        }
      }
      val config = OneBotConfig()
      save(file, config)
      return config
    }

    return parse(file)
  }

  private fun parse(file: Path): OneBotConfig {
    val text = String(Files.readAllBytes(file), StandardCharsets.UTF_8).removePrefix("\uFEFF")
    val parsed = runCatching {
      // 旧版 onebot.yml 可能残留 groups/managers/notify 等业务字段, 先转成动态 Map 剔除后再反序列化
      val map = Yaml.Default.decodeMapFromString(text).toMutableMap()
      map.remove("groups")
      map.remove("managers")
      map.remove("notify")
      Yaml.Default.decodeFromString(OneBotConfig.serializer(), Yaml.Default.encodeToString(map))
    }.getOrElse { error ->
      throw IllegalArgumentException("onebot.yml 解析失败，请检查格式（参考同目录说明）: ${error.message}", error)
    }
    val legacyRemoved = Yaml.Default.decodeMapFromString(text).keys.any { it in setOf("groups", "managers", "notify") }
    if (legacyRemoved) {
      // 业务字段统一由 arona.yml 管理，检测到残留则重写为标准模板，避免用户困惑
      save(file, parsed)
      println("[OneBot] 检测到 onebot.yml 中的 groups/managers/notify 残留，已移除，请统一在 arona.yml 中配置")
    }
    parsed.connections.keys.forEach { ConnectionType.fromName(it) }
    return parsed
  }

  fun save(file: Path, config: OneBotConfig) {
    Files.createDirectories(file.parent)
    Files.newBufferedWriter(file, StandardCharsets.UTF_8).use { it.write(template(config)) }
  }

  private fun template(config: OneBotConfig) = buildString {
    appendLine("# ==================== Arona OneBot 配置文件 ====================")
    appendLine("# 独立运行模式（java -jar arona-standalone-xxx-all.jar）使用本文件。")
    appendLine("# 文件位置：与 jar 同级的 arona-standalone/onebot.yml，修改后重启生效。")
    appendLine("# 作为 Mirai Console 插件运行时本文件不生效，插件仍使用 Mirai 自己的配置目录。")
    appendLine()
    appendLine("# 机器人 QQ 号，用于 get_login_info 等 API 的返回")
    appendLine("self_id: ${config.selfId}")
    appendLine("# 机器人昵称")
    appendLine("nickname: ${yamlString(config.nickname)}")
    appendLine()
    appendLine("# ==================== 连接配置 ====================")
    appendLine("# 每个连接的键名同时决定了连接类型，因此不需要单独的 type 字段：")
    appendLine("#   ws-forward    正向 WebSocket：Arona 主动连接 OneBot 服务端，填 url")
    appendLine("#   ws-reverse    反向 WebSocket：Arona 监听端口，等待 OneBot 服务端连入，填 host/port")
    appendLine("#   http          正向 HTTP API：Arona 监听端口对外提供 OneBot API，填 host/port/path")
    appendLine("#   http-reverse  反向 HTTP：Arona 向 url POST 上报事件，填 url")
    appendLine("# token（鉴权，留空关闭）、heartbeat_interval（心跳间隔毫秒，0 关闭）、")
    appendLine("# reconnect_interval（正向连接失败重试间隔毫秒）都写在各自连接内。")
    appendLine("# 正向连接每 reconnect_interval 重试一次，连续 5 次失败后休息 30 秒再重试。")
    appendLine("connections:")
    config.connections.forEach { (name, connection) ->
      val type = runCatching { ConnectionType.fromName(name) }.getOrNull()
      appendLine("  $name:")
      appendLine("    # 是否启用该连接")
      appendLine("    enable: ${connection.enable}")
      when (type) {
        ConnectionType.WEBSOCKET, ConnectionType.HTTP_REVERSE -> {
          appendLine("    # 目标地址（${type.displayName} 使用）")
          appendLine("    url: ${yamlString(connection.url)}")
        }
        ConnectionType.WEBSOCKET_REVERSE, ConnectionType.HTTP -> {
          appendLine("    # 监听地址（${type.displayName} 使用）")
          appendLine("    host: ${yamlString(connection.host)}")
          appendLine("    port: ${connection.port}")
          appendLine("    path: ${yamlString(connection.path)}")
        }
        null -> {
          appendLine("    host: ${yamlString(connection.host)}")
          appendLine("    port: ${connection.port}")
          appendLine("    url: ${yamlString(connection.url)}")
          appendLine("    path: ${yamlString(connection.path)}")
        }
      }
      appendLine("    # 鉴权 token，留空关闭鉴权；非空时使用 Authorization: Bearer <token>")
      appendLine("    token: ${yamlString(connection.token)}")
      appendLine("    # 心跳间隔（毫秒），0 表示关闭心跳")
      appendLine("    heartbeat_interval: ${connection.heartbeatInterval}")
      appendLine("    # 正向连接失败重试间隔（毫秒），连续 5 次失败后休息 30 秒")
      appendLine("    reconnect_interval: ${connection.reconnectInterval}")
    }
  }

  private fun yamlString(value: String): String = "\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\""

  fun defaultFile(): Path = RuntimePaths.prepareStandaloneRoot().resolve("onebot.yml")
}
