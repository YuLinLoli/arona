package net.diyigemt.arona.standalone.commands

import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import net.diyigemt.arona.advance.RemoteActionItem
import net.diyigemt.arona.command.cache.GachaCache
import net.diyigemt.arona.db.DataBaseProvider
import net.diyigemt.arona.db.gacha.GachaCharacterTable
import net.diyigemt.arona.db.gacha.GachaHistoryTable
import net.diyigemt.arona.db.gacha.GachaPool
import net.diyigemt.arona.db.gacha.GachaPoolCharacter
import net.diyigemt.arona.db.gacha.GachaPoolCharacterTable
import net.diyigemt.arona.db.gacha.GachaPoolTable
import net.diyigemt.arona.db.gacha.GachaLimitTable
import net.diyigemt.arona.remote.RemoteServiceAction
import net.diyigemt.arona.remote.action.GachaPoolUpdateData
import net.diyigemt.arona.runtime.CommandContext
import net.diyigemt.arona.runtime.OutgoingMessage
import net.diyigemt.arona.runtime.RuntimeGachaConfig
import net.diyigemt.arona.util.GachaUtil
import net.diyigemt.arona.util.NetworkUtil
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.select
import net.diyigemt.arona.db.gacha.GachaCharacter as GC

object StandaloneGachaAdmin {
  suspend fun handle(context: CommandContext, arguments: List<String>): OutgoingMessage {
    val sub = arguments.firstOrNull()?.lowercase() ?: return usage()
    return when (sub) {
      "list", "列表" -> list()
      "setpool", "设置池子" -> setPool(arguments.getOrNull(1)?.toIntOrNull())
      "reset", "重置" -> reset(context, arguments.getOrNull(1)?.toIntOrNull())
      "1s" -> setRate(arguments.getOrNull(1)?.toFloatOrNull(), "1星", "1s") { RuntimeGachaConfig.star1Rate = it }
      "2s" -> setRate(arguments.getOrNull(1)?.toFloatOrNull(), "2星", "2s") { RuntimeGachaConfig.star2Rate = it }
      "3s" -> setRate(arguments.getOrNull(1)?.toFloatOrNull(), "3星", "3s") { RuntimeGachaConfig.star3Rate = it }
      "p2s" -> setRate(arguments.getOrNull(1)?.toFloatOrNull(), "2星PickUp", "p2s") { RuntimeGachaConfig.star2PickupRate = it }
      "p3s" -> setRate(arguments.getOrNull(1)?.toFloatOrNull(), "3星PickUp", "p3s") { RuntimeGachaConfig.star3PickupRate = it }
      "time" -> setTime(arguments.getOrNull(1)?.toIntOrNull())
      "limit" -> setLimit(arguments.getOrNull(1)?.toIntOrNull())
      "update", "更新" -> update(context, arguments.getOrNull(1)?.toIntOrNull(), arguments.getOrNull(2))
      else -> usage()
    }
  }

  private fun usage(): OutgoingMessage = OutgoingMessage.text(
    "用法: /抽卡 list|setpool <id>|reset [pool]|1s|2s|3s|p2s|p3s|time|limit|update <id> [新池子名字]"
  )

  private fun list(): OutgoingMessage {
    val poolList = DataBaseProvider.query {
      GachaPool.all().orderBy(GachaPoolTable.id to SortOrder.DESC).limit(2).toList()
    } ?: emptyList()
    if (poolList.isEmpty()) return OutgoingMessage.text("没有任何池子信息")
    val msg = poolList.map { pool ->
      val students = DataBaseProvider.query {
        GachaPoolCharacterTable
          .innerJoin(GachaCharacterTable)
          .slice(GachaCharacterTable.name, GachaCharacterTable.star)
          .select { GachaPoolCharacterTable.poolId eq pool.id }.toList()
      } ?: emptyList()
      if (students.isEmpty()) {
        "${pool.name}(id: ${pool.id}): 没有关联的学生"
      } else {
        "${pool.name}(id: ${pool.id}): ${students.joinToString(", ") { s -> GachaUtil.mapStudentInfo(s[GachaCharacterTable.name], s[GachaCharacterTable.star]) }}"
      }
    }.reversed().joinToString("\n")
    return OutgoingMessage.text(msg)
  }

  private fun setPool(pool: Int?): OutgoingMessage {
    if (pool == null) return OutgoingMessage.text("用法: /抽卡 setpool <id>")
    val target = GachaCache.updatePool(pool)
    return if (target == null) OutgoingMessage.text("没有找到池子")
    else OutgoingMessage.text("池子设置为:${target.name}")
  }

  private fun reset(context: CommandContext, pool: Int?): OutgoingMessage {
    val pool0 = pool ?: RuntimeGachaConfig.activePool
    val groupId = context.groupId ?: return OutgoingMessage.text("该功能仅限群聊使用")
    DataBaseProvider.query {
      GachaHistoryTable.deleteWhere { (GachaHistoryTable.pool eq pool0) and (GachaHistoryTable.group eq groupId) }
    }
    GachaLimitTable.forceUpdate(groupId)
    return OutgoingMessage.text("历史记录重置成功")
  }

  private fun setRate(rate: Float?, label: String, usage: String, setter: (Float) -> Unit): OutgoingMessage {
    if (rate == null) return OutgoingMessage.text("用法: /抽卡 $usage <rate>")
    setter(rate)
    RuntimeGachaConfig.recalcMaxDot()
    return OutgoingMessage.text("${label}出货率设置为${rate}%")
  }

  private fun setTime(time: Int?): OutgoingMessage {
    if (time == null) return OutgoingMessage.text("用法: /抽卡 time <秒>")
    RuntimeGachaConfig.revokeTime = if (time > 0) time else 0
    return if (time > 0) OutgoingMessage.text("撤回时间设置为${time}")
    else OutgoingMessage.text("关闭抽卡结果撤回")
  }

  private fun setLimit(time: Int?): OutgoingMessage {
    if (time == null) return OutgoingMessage.text("用法: /抽卡 limit <次数>")
    RuntimeGachaConfig.limit = if (time > 0) time else 0
    return if (time > 0) OutgoingMessage.text("每日限制次数设置为${time}")
    else OutgoingMessage.text("每日限制次数设置为不限制每日抽卡次数")
  }

  @OptIn(InternalSerializationApi::class)
  private suspend fun update(context: CommandContext, id: Int?, poolName: String?): OutgoingMessage {
    if (id == null) return OutgoingMessage.text("用法: /抽卡 update <id> [新池子名字]")
    val data = runCatching {
      val resp = NetworkUtil.fetchDataFromServer<RemoteActionItem>("/action/one", mapOf("id" to id.toString()))
      if (resp.data.action != RemoteServiceAction.POOL_UPDATE.action) return@runCatching null
      Json.decodeFromString(GachaPoolUpdateData::class.serializer(), resp.data.content)
    }.getOrNull() ?: return OutgoingMessage.text("从远端获取池子信息失败")
    var pool = DataBaseProvider.query {
      GachaPool.find { GachaPoolTable.name eq data.name }.firstOrNull()
    }
    if (pool == null) {
      DataBaseProvider.query {
        pool = GachaPool.new { this.name = data.name }
      }
    } else {
      if (poolName == null) {
        return OutgoingMessage.text(
          "同名池子: ${data.name} 已经存在, 请使用\n/抽卡 update $id 新池子名字\n来更新"
        )
      }
      DataBaseProvider.query {
        pool = GachaPool.new { this.name = poolName }
      }
    }
    val inserted = runCatching { insertCharacter(pool!!, data.character) }
      .onFailure {
        DataBaseProvider.query {
          GachaPoolTable.deleteWhere { GachaPoolTable.id eq pool!!.id }
        }
      }.getOrNull() ?: return OutgoingMessage.text("新池子创建失败, 请查看控制台日志")
    return OutgoingMessage.text(
      "新池子: ${pool!!.name} 已添加, id: ${pool!!.id}\n" +
        "${inserted.joinToString(", ") { GachaUtil.mapStudentInfo(it) }}\n" +
        "使用指令\n/抽卡 setpool ${pool!!.id}\n来切换到这个池子"
    )
  }

  private fun insertCharacter(pool: GachaPool, list: List<net.diyigemt.arona.remote.action.GachaCharacter>): List<GC> {
    return DataBaseProvider.query {
      list.map { item ->
        var character = GC.find { GachaCharacterTable.name eq item.name }.firstOrNull()
        if (character == null) {
          character = GC.new {
            this.name = item.name
            this.star = item.star
            this.limit = item.limit == 1
          }
        }
        GachaPoolCharacter.new {
          this.poolId = pool.id
          this.characterId = character.id
        }
        return@map character
      }
    } ?: emptyList()
  }
}
