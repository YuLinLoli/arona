package net.diyigemt.arona.onebot

import com.google.gson.GsonBuilder
import com.google.gson.annotations.SerializedName
import net.diyigemt.arona.runtime.RuntimePaths
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

data class OneBotConfig(
  @SerializedName("self_id") val selfId: Long = 0,
  val nickname: String = "Arona",
  val groups: List<Long> = emptyList(),
  val managers: List<Long> = emptyList(),
  val ws: EndpointConfig = EndpointConfig(type = ConnectionType.FORWARD, port = 6700),
  @SerializedName("ws_reverse") val wsReverse: EndpointConfig = EndpointConfig(type = ConnectionType.REVERSE, port = 6701),
  val http: EndpointConfig = EndpointConfig(type = ConnectionType.FORWARD, port = 5700),
  @SerializedName("http_reverse") val httpReverse: EndpointConfig = EndpointConfig(type = ConnectionType.REVERSE, port = 5701),
)

data class EndpointConfig(
  val enable: Boolean = false,
  val type: ConnectionType = ConnectionType.FORWARD,
  val host: String = "127.0.0.1",
  val port: Int = 0,
  val url: String = "",
  val path: String = "/onebot/v11",
  val token: String = "",
  @SerializedName("heartbeat_interval") val heartbeatInterval: Long = 30_000,
  @SerializedName("reconnect_interval") val reconnectInterval: Long = 5_000,
)

enum class ConnectionType {
  @SerializedName("forward") FORWARD,
  @SerializedName("reverse") REVERSE,
}

object OneBotConfigLoader {
  private val gson = GsonBuilder().setPrettyPrinting().create()

  fun load(file: Path = defaultFile()): OneBotConfig {
    Files.createDirectories(file.parent)
    if (Files.notExists(file)) {
      val config = OneBotConfig()
      save(file, config)
      return config
    }
    return Files.newBufferedReader(file, StandardCharsets.UTF_8).use {
      gson.fromJson(it, OneBotConfig::class.java) ?: OneBotConfig()
    }
  }

  fun save(file: Path, config: OneBotConfig) {
    Files.createDirectories(file.parent)
    Files.newBufferedWriter(file, StandardCharsets.UTF_8).use { gson.toJson(config, it) }
  }

  fun defaultFile(): Path = RuntimePaths.prepareStandaloneRoot().resolve("onebot.json")
}
