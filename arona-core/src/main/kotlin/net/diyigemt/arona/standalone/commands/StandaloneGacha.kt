package net.diyigemt.arona.standalone.commands

import net.diyigemt.arona.command.cache.GachaCache
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

  private fun poolEmptyMessage(): String? = when {
    GachaCache.star1List.isEmpty() && GachaCache.star2List.isEmpty() && GachaCache.star3List.isEmpty() ->
      "抽卡池为空, 请先使用 /抽卡 update <id> 从远端更新卡池"
    else -> null
  }

  private fun teacherName(context: CommandContext, userId: Long, groupId: Long): String =
    GeneralUtils.queryTeacherNameFromDB(groupId, userId, context.senderName ?: userId.toString())

  suspend fun singleDraw(context: CommandContext): OutgoingMessage {
    val groupId = context.groupId ?: return OutgoingMessage.text("该功能仅限群聊使用")
    poolEmptyMessage()?.let { return OutgoingMessage.text(it) }
    val userId = context.userId
    val teacherName = teacherName(context, userId, groupId)
    val checkTime = GachaUtil.checkTime(userId, groupId)
    if (checkTime <= 0) {
      return OutgoingMessage.at(userId) + OutgoingMessage.text("${teacherName},石头不够了哦,明天再来抽吧")
    }
    val result = GachaUtil.pickup()
    val stars = result.star
    val history = GachaUtil.getHistory(userId, groupId)
    val star3 = if (stars == 3) 1 else 0
    val hit = GachaUtil.hitPickup(result)
    GachaUtil.updateHistory(userId, groupId, addPoints = 1, addCount3 = star3, dog = hit)
    val s = "${GachaUtil.resultData2String(result)}\n${history.points + 1} points"
    val dog = if (hit) "恭喜${teacherName},出货了呢" else ""
    return if (dog.isEmpty()) revokeIfNeeded(OutgoingMessage.text(s))
    else revokeIfNeeded(OutgoingMessage.at(userId) + OutgoingMessage.text("$dog\n$s"))
  }

  suspend fun multiDraw(context: CommandContext): OutgoingMessage {
    val groupId = context.groupId ?: return OutgoingMessage.text("该功能仅限群聊使用")
    poolEmptyMessage()?.let { return OutgoingMessage.text(it) }
    val userId = context.userId
    val teacherName = teacherName(context, userId, groupId)
    val checkTime = GachaUtil.checkTime(userId, groupId)
    if (checkTime <= 0) {
      return OutgoingMessage.at(userId) + OutgoingMessage.text("${teacherName},石头不够了哦,明天再来抽吧")
    }
    val result = Array(checkTime) { GachaUtil.pickup() }
    val starMap = result.map { it.star }
    val stars = starMap.reduce { prv, cur -> prv + cur }
    var stars1 = starMap.count { it == 1 }
    var stars2 = starMap.count { it == 2 }
    val stars3 = starMap.count { it == 3 }
    if (stars <= 10 && checkTime == 10) {
      result[9] = GachaUtil.pickup2()
      stars1--
      stars2++
    }
    val s = result.map { GachaUtil.resultData2String(it) }
      .reduceIndexed { index, prv, cur -> if (index == 4) "$prv $cur\n" else "$prv $cur" }
    val hit = result.any { GachaUtil.hitPickup(it) }
    val history = GachaUtil.getHistory(userId, groupId)
    GachaUtil.updateHistory(userId, groupId, addPoints = checkTime, addCount3 = stars3, dog = hit)
    val dog = if (hit) "恭喜${teacherName},出货了呢" else ""
    val sss = "3星:$stars3 2星:$stars2 1星:$stars1 ${history.points + checkTime} points\n${s}"
    return if (dog.isEmpty()) revokeIfNeeded(OutgoingMessage.text(sss))
    else revokeIfNeeded(OutgoingMessage.at(userId) + OutgoingMessage.text("$dog\n$sss"))
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
