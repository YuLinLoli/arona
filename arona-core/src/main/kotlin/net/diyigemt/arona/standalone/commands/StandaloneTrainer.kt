/**
 * 文件说明：独立模式的「/攻略」命令。
 * 具体职责：提供 GameKee 活动攻略/日程笔记图片抓取, 以及 arona 云端图片搜索。
 */
package net.diyigemt.arona.standalone.commands

import net.diyigemt.arona.entity.ImageResult
import net.diyigemt.arona.gacha.GameKeeGachaPoolSource
import net.diyigemt.arona.gacha.GameKeeGachaPoolSource.GachaServer
import net.diyigemt.arona.runtime.CommandContext
import net.diyigemt.arona.runtime.ForwardMessage
import net.diyigemt.arona.runtime.MessageSegment
import net.diyigemt.arona.runtime.OutgoingMessage
import net.diyigemt.arona.runtime.RuntimeConfig
import net.diyigemt.arona.runtime.RuntimeLog
import net.diyigemt.arona.util.GameKeeUtil
import net.diyigemt.arona.util.GeneralUtils
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap

object StandaloneTrainer {
  /** 等待用户从相近建议中回复数字选择的临时状态 */
  private data class PendingSuggestion(
    val suggestions: List<ImageResult>,
    val expireAt: Long,
  )

  private val pendingSelections = ConcurrentHashMap<String, PendingSuggestion>()
  private const val PENDING_TTL_MILLS = 60_000L

  suspend fun trainer(context: CommandContext, arguments: List<String>): OutgoingMessage {
    val keyword = arguments.joinToString(" ").trim()
    if (keyword.isBlank()) {
      return OutgoingMessage.text("用法: /攻略 日服活动|国际服活动|国服活动|日程笔记|图片关键词|日服卡池|国服卡池|国际服卡池")
    }
    val serverPool = parseServerPoolArg(keyword)
    if (serverPool != null) return gachaPoolForward(serverPool)
    val guideFetcher = when (keyword) {
      "日服活动" -> GameKeeUtil::getJpActivityGuide
      "国际服活动" -> GameKeeUtil::getGlobalActivityGuide
      "国服活动" -> GameKeeUtil::getCnActivityGuide
      "日程笔记" -> GameKeeUtil::getScheduleNoteImages
      else -> null
    }
    if (guideFetcher != null) {
      val imageFiles = runCatching { guideFetcher() }
        .onFailure { RuntimeLog.warning("获取${keyword}攻略失败: ${it.message}") }
        .getOrNull()
      if (imageFiles == null) return OutgoingMessage.text("获取${keyword}攻略失败，请稍后重试")
      // 日程笔记使用合并转发, title 为「日程笔记」
      return if (keyword == "日程笔记") forwardImages(imageFiles, "日程笔记") else sendImages(imageFiles, keyword)
    }
    // 普通图片/别名查询, 复用 arona 云端图片库
    val result = GeneralUtils.loadImageOrUpdate(keyword)
    result.file?.let { return sendImages(listOf(it), keyword) }
    val suggestions = result.list
    if (suggestions.isEmpty()) {
      return OutgoingMessage.text("没有对应信息, 请联系作者添加别名或者在配置文件中指定")
    }
    // 无精确匹配: 列出相近建议, 等待用户回复数字选择, 而不是直接取第一个
    return registerPending(context, keyword, suggestions)
  }

  /** 登记待选建议并返回提示消息, 拆出以便测试 */
  internal fun registerPending(context: CommandContext, keyword: String, suggestions: List<ImageResult>): OutgoingMessage {
    pendingSelections[selectionKey(context)] = PendingSuggestion(
      suggestions = suggestions,
      expireAt = System.currentTimeMillis() + PENDING_TTL_MILLS,
    )
    return OutgoingMessage.text(
      "没有找到与「$keyword」完全匹配的图片, 最接近的有:\n" +
        suggestions.mapIndexed { index, item -> "${index + 1}. ${item.name}" }.joinToString("\n") +
        "\n回复对应数字查看图片"
    )
  }

  /** 处理非命令消息: 用户回复数字选择相近建议 */
  suspend fun resolveNumericReply(context: CommandContext): OutgoingMessage? {
    val key = selectionKey(context)
    val pending = pendingSelections[key] ?: return null
    if (pending.expireAt < System.currentTimeMillis()) {
      pendingSelections.remove(key)
      return null
    }
    val select = context.text.trim().toIntOrNull() ?: return null
    if (select < 1 || select > pending.suggestions.size) return null
    pendingSelections.remove(key)
    val target = pending.suggestions[select - 1]
    val matched = GeneralUtils.loadImageOrUpdate(target.name)
    val file = matched.file
      ?: return OutgoingMessage.text("没有获取到「${target.name}」的图片, 请重新查询")
    return sendImages(listOf(file), target.name)
  }

  /** 私聊按 QQ 号、群聊按 群号+QQ 号 区分, 避免不同会话互相串台 */
  private fun selectionKey(context: CommandContext): String =
    if (context.groupId != null) "g${context.groupId}:u${context.userId}" else "p${context.userId}"

  /** 三服当期卡池: 合并转发, 每个角色一个节点, 内容为 卡池图(image_list) + 角色名/所属/时间 文本 */
  private fun gachaPoolForward(server: GachaServer): OutgoingMessage {
    val characters = runCatching { GameKeeGachaPoolSource.fetchPool(server) }
      .getOrElse { return OutgoingMessage.text("获取${server.displayName}当期卡池失败: ${it.message}") }
    if (characters.isEmpty()) return OutgoingMessage.text("当前没有${server.displayName}的当期卡池")
    val imageDir = GeneralUtils.localImageFile("/gacha-pool/${server.displayName}")
    val uin = RuntimeConfig.botId
    val nodes = characters.map { character ->
      // 上传头图前先按角色 id 查缓存, 未命中才下载卡池图(image_list)
      val imageFile = GameKeeGachaPoolSource.findCachedImage(character.id, imageDir)
        ?: GameKeeGachaPoolSource.downloadCharacterImage(character, imageDir)
      val text = buildString {
        append("角色名：${character.name}\n")
        append("所属：${character.nameAlias}\n")
        append("开始时间：${formatPoolTime(character.startAt)}\n")
        append("结束时间：${formatPoolTime(character.endAt)}")
      }
      val content = mutableListOf<MessageSegment>()
      imageFile?.let { content += MessageSegment.Image(file = it.absolutePath) }
      content += MessageSegment.Text(text)
      ForwardMessage(name = "Arona", uin = uin, content = content)
    }
    return OutgoingMessage.forward("${server.displayName}当期卡池", nodes)
  }

  /** 卡池时间戳(秒)换算为北京时间, 0/负数视为未知 */
  private fun formatPoolTime(epochSeconds: Long): String =
    if (epochSeconds <= 0) "未知"
    else Instant.ofEpochSecond(epochSeconds).atZone(ZoneId.of("Asia/Shanghai"))
      .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))

  /** 解析 /攻略 '日服'卡池 这类参数, 单引号内为服务器名(支持 日服/国服/国际服 与英文别名) */
  private fun parseServerPoolArg(keyword: String): GachaServer? {
    return when (keyword) {
      "日服卡池" -> GachaServer.JP
      "国服卡池" -> GachaServer.CN
      "国际服卡池" -> GachaServer.GLOBAL
      else -> null
    }
  }

  private fun sendImages(files: List<File>, keyword: String): OutgoingMessage {
    if (files.isEmpty()) return OutgoingMessage.text("没有获取到「$keyword」的图片")
    var message = OutgoingMessage.text("「$keyword」共 ${files.size} 张图片")
    files.forEach { message += OutgoingMessage.image(it.absolutePath) }
    return message
  }

  /** 日程笔记: 合并转发, 首节点为标题文本, 后续每张图片一个节点 */
  internal fun forwardImages(files: List<File>, title: String): OutgoingMessage {
    if (files.isEmpty()) return OutgoingMessage.text("没有获取到「$title」的图片")
    val uin = RuntimeConfig.botId
    val nodes = mutableListOf(
      ForwardMessage(name = "Arona", uin = uin, content = listOf(MessageSegment.Text(title))),
    )
    files.forEach { file ->
      nodes.add(ForwardMessage(name = "Arona", uin = uin, content = listOf(MessageSegment.Image(file = file.absolutePath))))
    }
    return OutgoingMessage.forward(title, nodes)
  }
}
