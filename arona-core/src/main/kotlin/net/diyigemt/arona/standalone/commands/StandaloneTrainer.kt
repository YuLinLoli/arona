/**
 * 文件说明：独立模式的「/攻略」命令。
 * 具体职责：提供 GameKee 活动攻略/日程笔记图片抓取, 以及 arona 云端图片搜索。
 */
package net.diyigemt.arona.standalone.commands

import net.diyigemt.arona.entity.ImageResult
import net.diyigemt.arona.runtime.CommandContext
import net.diyigemt.arona.runtime.ForwardMessage
import net.diyigemt.arona.runtime.MessageSegment
import net.diyigemt.arona.runtime.OutgoingMessage
import net.diyigemt.arona.runtime.RuntimeConfig
import net.diyigemt.arona.runtime.RuntimeLog
import net.diyigemt.arona.util.GameKeeUtil
import net.diyigemt.arona.util.GeneralUtils
import java.io.File
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
      return OutgoingMessage.text("用法: /攻略 日服活动|国际服活动|国服活动|日程笔记|图片关键词")
    }
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
