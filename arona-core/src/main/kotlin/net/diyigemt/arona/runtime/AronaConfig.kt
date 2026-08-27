package net.diyigemt.arona.runtime

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.mamoe.yamlkt.Yaml
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

/**
 * 独立模式的 Arona 业务配置（非 OneBot 协议配置）。
 * OneBot 连接配置放在 onebot.yml，本配置放在 jar 同级 arona-standalone/arona.yml。
 */
@Serializable
data class AronaConfig(
  /** 允许响应的群号列表，留空表示响应所有群 */
  val groups: List<Long> = emptyList(),
  /** 管理员 QQ 号列表，可执行管理命令 */
  val managers: List<Long> = emptyList(),
  /** 每日活动推送配置 */
  val notify: NotifyConfig = NotifyConfig(),
)

@Serializable
data class NotifyConfig(
  /** 是否启用每日活动防侠推送 */
  val enable: Boolean = true,
  /** 每日推送的小时(0-23) */
  @SerialName("every_day_hour") val everyDayHour: Int = 8,
  /** 是否推送日服活动 */
  val jp: Boolean = true,
  /** 是否推送国际服活动 */
  val global: Boolean = true,
  /** 是否推送国服活动 */
  val cn: Boolean = true,
  /** 不推送的群号列表（黑名单），推送时会排除这些群 */
  @SerialName("black_groups") val blackGroups: List<Long> = emptyList(),
  /** 推送消息开头文字 */
  @SerialName("notify_text") val notifyText: String = "碧蓝档案预警",
)

object AronaConfigLoader {
  fun load(file: Path = defaultFile()): AronaConfig {
    Files.createDirectories(file.parent)
    if (Files.notExists(file)) {
      // 1) 旧后缀 arona.yaml 已存在则迁移
      val oldArona = file.parent.resolve("arona.yaml")
      if (Files.exists(oldArona)) {
        runCatching { parse(oldArona) }.getOrNull()?.let { config ->
          save(file, config)
          println("[Arona] 检测到旧版 arona.yaml，已迁移到 ${file.fileName}")
          return config
        }
      }
      // 2) 最早版本从旧的 onebot.yaml 迁移 groups/managers/notify, 避免用户配置丢失
      val legacy = readLegacyFromOneBot(onebotFile(file))
      val config = AronaConfig(
        groups = legacy?.groups ?: emptyList(),
        managers = legacy?.managers ?: emptyList(),
        notify = legacy?.notify ?: NotifyConfig(),
      )
      save(file, config)
      return config
    }
    return parse(file)
  }

  private fun parse(file: Path): AronaConfig {
    val text = String(Files.readAllBytes(file), StandardCharsets.UTF_8).removePrefix("\uFEFF")
    return runCatching {
      Yaml.Default.decodeFromString(AronaConfig.serializer(), text)
    }.getOrElse { error ->
      throw IllegalArgumentException("arona.yml 解析失败，请检查格式（参考同目录说明）: ${error.message}", error)
    }
  }

  fun save(file: Path, config: AronaConfig) {
    Files.createDirectories(file.parent)
    Files.newBufferedWriter(file, StandardCharsets.UTF_8).use { it.write(template(config)) }
  }

  fun defaultFile(): Path = RuntimePaths.prepareStandaloneRoot().resolve("arona.yml")

  /** 旧版 onebot.yml 中可能存在的业务字段 */
  @Serializable
  private data class LegacyOneBotExtra(
    val groups: List<Long> = emptyList(),
    val managers: List<Long> = emptyList(),
    val notify: NotifyConfig = NotifyConfig(),
  )

  private fun readLegacyFromOneBot(onebot: Path): LegacyOneBotExtra? {
    if (!Files.exists(onebot)) return null
    return runCatching {
      val text = String(Files.readAllBytes(onebot), StandardCharsets.UTF_8).removePrefix("\uFEFF")
      val map = Yaml.Default.decodeMapFromString(text)
      val extra = linkedMapOf<String, Any?>()
      map["groups"]?.let { extra["groups"] = it }
      map["managers"]?.let { extra["managers"] = it }
      map["notify"]?.let { notifyRaw ->
        // 旧版 notify.groups 是"推送目标"语义，与现在的黑名单 black_groups 相反，无法安全迁移，直接丢弃
        val notifyMap = if (notifyRaw is Map<*, *>) notifyRaw.toMutableMap() else notifyRaw
        if (notifyMap is MutableMap<*, *>) notifyMap.remove("groups")
        extra["notify"] = notifyMap
      }
      if (extra.isEmpty()) {
        null
      } else {
        val cleaned = Yaml.Default.encodeToString(extra)
        Yaml.Default.decodeFromString(LegacyOneBotExtra.serializer(), cleaned)
      }
    }.getOrNull()
  }

  private fun onebotFile(arona: Path): Path {
    // 优先读新后缀，兼容旧版 onebot.yaml
    val newFile = arona.parent.resolve("onebot.yml")
    if (Files.exists(newFile)) return newFile
    return arona.parent.resolve("onebot.yaml")
  }

  private fun template(config: AronaConfig) = buildString {
    appendLine("# ==================== Arona 业务配置 ====================")
    appendLine("# 独立运行模式（java -jar arona-standalone-xxx-all.jar）使用本文件。")
    appendLine("# 文件位置：与 jar 同级的 arona-standalone/arona.yml，修改后保存即自动热重载。")
    appendLine("# OneBot 协议连接配置见同目录 onebot.yml；本文件只放非 OneBot 的业务配置。")
    appendLine("# 作为 Mirai Console 插件运行时本文件不生效，插件仍使用 Mirai 自己的配置目录。")
    appendLine()
    appendLine("# 允许响应的群号列表，留空表示响应所有群")
    appendLine("groups: ${config.groups}")
    appendLine("# 管理员 QQ 号列表，可执行管理命令")
    appendLine("managers: ${config.managers}")
    appendLine()
    appendLine("# ==================== 每日活动推送 ====================")
    appendLine("# 每天 every_day_hour 点向目标群推送国服/国际服/日服活动日历图片")
    appendLine("notify:")
    appendLine("  # 是否启用每日活动防侠推送")
    appendLine("  enable: ${config.notify.enable}")
    appendLine("  # 每日推送的小时(0-23)")
    appendLine("  every_day_hour: ${config.notify.everyDayHour}")
    appendLine("  # 是否推送日服/国际服/国服活动")
    appendLine("  jp: ${config.notify.jp}")
    appendLine("  global: ${config.notify.global}")
    appendLine("  cn: ${config.notify.cn}")
    appendLine("  # 不推送的群号列表（黑名单），留空表示推送到全部允许的群")
    appendLine("  black_groups: ${config.notify.blackGroups}")
    appendLine("  # 推送消息开头文字")
    appendLine("  notify_text: ${yamlString(config.notify.notifyText)}")
  }

  private fun yamlString(value: String): String = "\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\""
}