package net.diyigemt.arona.standalone.commands

import net.diyigemt.arona.db.DataBaseProvider
import net.diyigemt.arona.db.name.GameName
import net.diyigemt.arona.db.name.GameNameTable
import net.diyigemt.arona.db.name.TeacherName
import net.diyigemt.arona.db.name.TeacherNameTable
import net.diyigemt.arona.runtime.CommandContext
import net.diyigemt.arona.runtime.OutgoingMessage
import net.diyigemt.arona.util.GeneralUtils
import org.jetbrains.exposed.sql.and

object StandaloneName {
  suspend fun gameName(context: CommandContext, name: String?): OutgoingMessage {
    val userId = context.userId
    if (name.isNullOrBlank()) {
      val list = DataBaseProvider.query {
        GameName.find { GameNameTable.id eq userId }.toList()
      } ?: emptyList()
      return if (list.isEmpty()) OutgoingMessage.text("游戏名未设置")
      else OutgoingMessage.text("当前游戏名为: ${list[0].name}")
    }
    if (name.length > 50) return OutgoingMessage.at(userId) + OutgoingMessage.text("太长了, 爬")
    updateGameNameToDB(userId, name)
    return OutgoingMessage.text("游戏名已记录: $name")
  }

  suspend fun search(context: CommandContext, myName: String): OutgoingMessage {
    if (myName.isBlank()) return OutgoingMessage.text("用法: /谁是 <游戏名>")
    val result = DataBaseProvider.query {
      GameName.find { GameNameTable.name like "%$myName%" }.toList()
    } ?: emptyList()
    if (result.isEmpty()) return OutgoingMessage.text("没有游戏名叫 '$myName' 的群友")
    val lines = result.map { "${it.name}(${it.id.value})" }
    return OutgoingMessage.text("查询结果:\n" + lines.joinToString("\n"))
  }

  suspend fun callMe(context: CommandContext, name: String?): OutgoingMessage {
    val userId = context.userId
    val groupId = context.groupId ?: return OutgoingMessage.text("该功能仅限群聊使用")
    if (name.isNullOrBlank()) {
      val teacherName = GeneralUtils.queryTeacherNameFromDB(
        groupId,
        userId,
        context.senderName ?: userId.toString(),
      )
      return OutgoingMessage.at(userId) + OutgoingMessage.text("怎么了, $teacherName")
    }
    var teacherName = name
    if (teacherName.length > 20) return OutgoingMessage.at(userId) + OutgoingMessage.text("太长了, 爬")
    updateTeacherNameToDB(groupId, userId, teacherName)
    if (!teacherName.endsWith("老师")) teacherName = "${teacherName}老师"
    return OutgoingMessage.text("好的, $teacherName")
  }

  private fun updateGameNameToDB(userId: Long, name: String) {
    DataBaseProvider.query {
      val list = GameName.find { GameNameTable.id eq userId }.toList()
      if (list.isEmpty()) {
        GameName.new(userId) { this.name = name }
      } else {
        list[0].name = name
      }
    }
  }

  private fun updateTeacherNameToDB(groupId: Long, userId: Long, newName: String) {
    DataBaseProvider.query {
      val list = TeacherName.find { (TeacherNameTable.group eq groupId) and (TeacherNameTable.id eq userId) }.toList()
      if (list.isEmpty()) {
        TeacherName.new(userId) {
          group = groupId
          name = newName
        }
      } else {
        list[0].name = newName
      }
    }
  }
}
