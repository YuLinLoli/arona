package net.diyigemt.arona.standalone.commands

import net.diyigemt.arona.gacha.GachaV2ImageRenderer
import net.diyigemt.arona.gacha.GachaV2Service
import net.diyigemt.arona.runtime.CommandContext
import net.diyigemt.arona.runtime.OutgoingMessage
import net.diyigemt.arona.runtime.RuntimeGachaConfig
import net.diyigemt.arona.util.GachaUtil
import net.diyigemt.arona.util.GeneralUtils

object StandaloneGacha {
  /** 抽卡结果按 revokeTime 配置延迟撤回(通过 OneBot delete_msg 实现) */
  private fun revokeIfNeeded(message: OutgoingMessage): OutgoingMessage {
    val millis = RuntimeGachaConfig.revokeTime * 1000L
    return if (millis > 0) OutgoingMessage(message.segments, revokeAfterMillis = millis) else message
  }

  private fun teacherName(context: CommandContext, userId: Long, groupId: Long): String =
    GeneralUtils.queryTeacherNameFromDB(groupId, userId, context.senderName ?: userId.toString())

  suspend fun singleDraw(context: CommandContext, arguments: List<String>): OutgoingMessage {
    val groupId = context.groupId ?: return OutgoingMessage.text("该功能仅限群聊使用")
    val userId = context.userId
    val teacherName = teacherName(context, userId, groupId)
    val server = GachaV2Service.resolveDrawServer(userId, arguments.firstOrNull())
      ?: return OutgoingMessage.text("未知服务器, 可用: 日服/国服/国际服")
    val report = runCatching { GachaV2Service.performDraw(userId, groupId, 1, server) }
      .getOrElse { return OutgoingMessage.text(it.message ?: "抽卡失败, 请稍后再试") }
    if (report == null) {
      return OutgoingMessage.at(userId) + OutgoingMessage.text("${teacherName},石头不够了哦,明天再来抽吧")
    }
    val imageFile = runCatching {
      GachaV2ImageRenderer.render(report.results, report.pityCount, GachaV2ImageRenderer.newResultFile())
    }.getOrElse {
      return OutgoingMessage.text(it.message ?: "抽卡图片生成失败, 请稍后再试")
    }
    return revokeIfNeeded(OutgoingMessage.image(imageFile.absolutePath))
  }

  suspend fun multiDraw(context: CommandContext, arguments: List<String>): OutgoingMessage {
    val groupId = context.groupId ?: return OutgoingMessage.text("该功能仅限群聊使用")
    val userId = context.userId
    val teacherName = teacherName(context, userId, groupId)
    val server = GachaV2Service.resolveDrawServer(userId, arguments.firstOrNull())
      ?: return OutgoingMessage.text("未知服务器, 可用: 日服/国服/国际服")
    val report = runCatching { GachaV2Service.performDraw(userId, groupId, 10, server) }
      .getOrElse { return OutgoingMessage.text(it.message ?: "抽卡失败, 请稍后再试") }
    if (report == null) {
      return OutgoingMessage.at(userId) + OutgoingMessage.text("${teacherName},石头不够了哦,明天再来抽吧")
    }
    val imageFile = runCatching {
      GachaV2ImageRenderer.render(report.results, report.pityCount, GachaV2ImageRenderer.newResultFile())
    }.getOrElse {
      return OutgoingMessage.text(it.message ?: "抽卡图片生成失败, 请稍后再试")
    }
    return revokeIfNeeded(OutgoingMessage.image(imageFile.absolutePath))
  }

  /** /抽卡服务器 <日服|国服|国际服>: 设置该用户的默认抽卡服务器 */
  suspend fun setServer(context: CommandContext, arguments: List<String>): OutgoingMessage {
    val raw = arguments.firstOrNull()
    if (raw.isNullOrBlank()) {
      return OutgoingMessage.text(
        "当前默认抽卡服务器: ${GachaV2Service.getUserServer(context.userId).serverName}\n" +
          "用法: /抽卡服务器 日服|国服|国际服"
      )
    }
    val server = GachaV2Service.resolveServer(raw)
      ?: return OutgoingMessage.text("未知服务器: $raw, 可用: 日服/国服/国际服")
    GachaV2Service.setUserServer(context.userId, server)
    return OutgoingMessage.text("抽卡服务器已设置为 ${server.serverName}")
  }

  suspend fun dogRanking(context: CommandContext): OutgoingMessage {
    val groupId = context.groupId ?: return OutgoingMessage.text("该功能仅限群聊使用")
    val dogCall = GachaUtil.getDogCall(groupId).filter { it.dog != 0 }
    if (dogCall.isEmpty()) return OutgoingMessage.text("还没有老师抽出来哦")
    var ss = "狗叫排行:\n"
    dogCall.map { record ->
      val teacherName = GeneralUtils.queryTeacherNameFromDB(groupId, record.id.value, record.id.value.toString())
      "${teacherName}(${record.id.value}): ${record.dog}抽"
    }.forEachIndexed { index, line -> ss += "${index + 1}. $line\n" }
    return OutgoingMessage.text(ss.substring(0, ss.length - 1))
  }

  suspend fun historyRanking(context: CommandContext): OutgoingMessage {
    val groupId = context.groupId ?: return OutgoingMessage.text("该功能仅限群聊使用")
    val history = GachaUtil.getHistoryAll(groupId)
    if (history.isEmpty()) return OutgoingMessage.text("还没有记录哦")
    var ss = "历史排行:\n"
    history.map { record ->
      val teacherName = GeneralUtils.queryTeacherNameFromDB(groupId, record.id.value, record.id.value.toString())
      val rate = if (record.count3 == 0) 0 else record.points / record.count3
      "${teacherName}(${record.id.value}): ${record.points}抽/${record.count3}个3星 = $rate"
    }.subList(0, minOf(6, history.size)).forEachIndexed { index, line -> ss += "${index + 1}. $line\n" }
    return OutgoingMessage.text(ss.substring(0, ss.length - 1))
  }
}
