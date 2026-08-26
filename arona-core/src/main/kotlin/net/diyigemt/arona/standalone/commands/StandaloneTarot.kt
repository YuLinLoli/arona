package net.diyigemt.arona.standalone.commands

import net.diyigemt.arona.db.DataBaseProvider
import net.diyigemt.arona.db.tarot.Tarot
import net.diyigemt.arona.db.tarot.TarotRecord
import net.diyigemt.arona.db.tarot.TarotRecordTable
import net.diyigemt.arona.runtime.CommandContext
import net.diyigemt.arona.runtime.OutgoingMessage
import net.diyigemt.arona.runtime.RuntimeTarotConfig
import net.diyigemt.arona.util.GeneralUtils
import net.diyigemt.arona.util.TarotDataUtil
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.update
import java.util.Calendar

object StandaloneTarot {
  private const val TarotCount = 22
  private const val TarotImageFolder = "/tarot"

  suspend fun tarot(context: CommandContext): OutgoingMessage {
    val userId = context.userId
    val group0 = context.groupId ?: userId
    val today = Calendar.getInstance().get(Calendar.DAY_OF_MONTH)
    val record = DataBaseProvider.query {
      TarotRecord.find { (TarotRecordTable.id eq userId) and (TarotRecordTable.group eq group0) }.toList()
    } ?: emptyList()
    if (RuntimeTarotConfig.dayOne && record.isNotEmpty() && record[0].day == today) {
      val tarotRecord = record[0]
      val tarot = DataBaseProvider.query { Tarot.findById(tarotRecord.tarot) }
      if (tarot != null) return send(context, tarot, tarotRecord.positive)
    }
    val tarotNumber = GeneralUtils.randomInt(TarotCount)
    val tarot = TarotDataUtil.findTarotByNumber(tarotNumber)
      ?: return OutgoingMessage.text("塔罗牌数据未初始化, 请联系管理员")
    Thread.sleep((1..10).random().toLong())
    val positive = GeneralUtils.randomBoolean()
    if (RuntimeTarotConfig.dayOne) {
      if (record.isNotEmpty()) {
        DataBaseProvider.query {
          TarotRecordTable.update({ (TarotRecordTable.id eq userId) and (TarotRecordTable.group eq group0) }) {
            it[day] = today
            it[TarotRecordTable.tarot] = tarotNumber + 1
            it[TarotRecordTable.positive] = positive
          }
        }
      } else {
        DataBaseProvider.query {
          TarotRecord.new(userId) {
            this.group = group0
            this.day = today
            this.tarot = tarotNumber + 1
            this.positive = positive
          }
        }
      }
    }
    return send(context, tarot, positive)
  }

  private suspend fun send(context: CommandContext, tarot: Tarot, positive: Boolean): OutgoingMessage {
    val result = if (positive) tarot.positive else tarot.negative
    val resultName = if (positive) "正位" else "逆位"
    val teacherName = GeneralUtils.queryTeacherNameFromDB(
      context.groupId ?: context.userId,
      context.userId,
      context.senderName ?: context.userId.toString(),
    )
    val path = TarotDataUtil.imagePath(tarot.number, positive)
    val text = "看看${teacherName}抽到了什么:\n${tarot.name}(${resultName})\n${result}"
    if (!RuntimeTarotConfig.image) return OutgoingMessage.text(text)
    return runCatching {
      val imageFile = GeneralUtils.localImageFile(path)
      if (!imageFile.exists()) {
        GeneralUtils.imageRequest(path, imageFile)
      }
      imageFile
    }.fold(
      onSuccess = { imageFile -> OutgoingMessage.text(text) + OutgoingMessage.image(imageFile.absolutePath) },
      onFailure = { OutgoingMessage.text(text) },
    )
  }

  /** 启动时预下载全部塔罗牌图片到 image/tarot */
  fun downloadAllImages() {
    val folder = GeneralUtils.localImageFile(TarotImageFolder)
    if (!folder.exists()) folder.mkdirs()
    var downloaded = 0
    var failed = 0
    (0 until TarotCount).forEach { number ->
      listOf(true, false).forEach { positive ->
        val relative = TarotDataUtil.imagePath(number, positive)
        val file = GeneralUtils.localImageFile(relative)
        if (!file.exists() || file.length() == 0L) {
          runCatching { GeneralUtils.imageRequest(relative, file) }
            .onSuccess { downloaded++ }
            .onFailure { failed++ }
        }
      }
    }
    if (downloaded > 0 || failed > 0) {
      net.diyigemt.arona.runtime.RuntimeLog.info("塔罗牌图片预下载完成: 成功 $downloaded 张, 失败 $failed 张")
    }
  }
}
