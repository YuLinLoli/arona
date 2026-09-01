/**
 * 文件说明：本文件属于 业务数据实体与接口响应模型。
 * 具体职责：围绕 CommonDAO 提供对应的实现、数据结构或测试。
 */
package net.diyigemt.arona.entity.schaleDB

import net.diyigemt.arona.db.DB
import net.diyigemt.arona.db.DataBaseProvider
import net.diyigemt.arona.db.data.schaledb.CurrentData
import net.diyigemt.arona.db.data.schaledb.Raid
import net.diyigemt.arona.entity.ServerLocale
import net.diyigemt.arona.util.scbaleDB.SchaleDBDataSyncService
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq

/**
 *@Author hjn
 *@Create 2022/8/18
 * 部分无用信息的分支被注释，如需要请取消注释
 */

// 中文说明：定义 CommonDAO 类型，用于封装本模块的数据或处理行为。
data class CommonDAO(
//  val GachaGroup : List<GachaGroup>,
  var regions : List<Regions> = mutableListOf(Regions(null), Regions(null), Regions(null)),
//  val changelog : List<ChangeLog>
) : BaseDAO{
  override fun sendToDataBase(){
    //删表重填
    DataBaseProvider.query(DB.DATA.ordinal) { CurrentData.deleteAll() }
    for ((index, item) in regions.withIndex()){
      // 新版 config.json 的 Regions 带 Name 字段, 据此确定服务器; 旧数据无 Name 时按顺序兜底
      val serverName = when (item.abbreviation?.lowercase()) {
        "jp", "jpn" -> "JPN"
        "gl", "glb", "global" -> "GLB"
        "cn" -> "CN"
        else -> getServer(index == 0)
      }
      val isJPN = serverName == "JPN"
      //characters
      for (chaPool in item.current_gacha){
        for (cha in chaPool.characters){
          DataBaseProvider.query(DB.DATA.ordinal){
            CurrentData.insert {
              it[value] = cha
              it[type] = SchaleDBDataSyncService.DataBaseType.STUDENT.name
              it[server] = serverName
              it[start] = chaPool.start
              it[end] = chaPool.end
            }
          }
        }
      }

      //events
      for(event in item.current_events){
        DataBaseProvider.query(DB.DATA.ordinal) {
          CurrentData.insert {
            it[value] = event.event
            it[type] = SchaleDBDataSyncService.DataBaseType.EVENT.name
              it[server] = serverName
            it[start] = event.start
            it[end] = event.end
          }
        }
      }

      //raid
      for(raid in item.current_raid){
        DataBaseProvider.query(DB.DATA.ordinal) {
          CurrentData.insert {
            it[value] = raid.raid
            it[type] = SchaleDBDataSyncService.DataBaseType.RAID.name
              it[server] = serverName
            it[start] = raid.start
            it[end] = raid.end
          }

          Raid.update ({ Raid.Id eq raid.raid }){
            if (isJPN) it[CurrentJPN] = raid.terrain ?: ""
            else it[CurrentGLB] = raid.terrain ?: ""
          }
        }
      }

    }
  }

  override fun <T : BaseDAO> toModel(dao : T) : T{
    dao as CommonDAO
    for (item in dao.regions){
      item.current_gacha = mutableListOf()
      item.current_events = mutableListOf()
      item.current_raid = mutableListOf()
    }
    var res: T

    //JPN
    res = generateServerData(ServerLocale.JP, dao)

    //GLB
    res = generateServerData(ServerLocale.GLOBAL, res)

    // 国服卡池/活动/总力战同样需要从数据库重建, 否则国服当期 pickup 恒为空
    res = generateServerData(ServerLocale.CN, res)

    return res
  }

  private fun <T : BaseDAO> generateServerData(type : ServerLocale, dao : T) : T{
    val serverType = type.ordinal
    dao as CommonDAO
    //character
    var query = kotlin.runCatching {
      DataBaseProvider.query(DB.DATA.ordinal) {
        CurrentData.select(
          CurrentData.type eq "STUDENT" and (CurrentData.server eq type.dbName)
        ).groupBy(CurrentData.start, CurrentData.end).toList()
      }
    }.getOrNull()?: mutableListOf()

    //筛选时间戳创建卡池
    for (item in query){
      dao.regions[serverType].current_gacha = dao.regions[serverType].current_gacha.plusElement(CurrentGacha(mutableListOf(), 0L, 0L))
      val start = item.getOrNull(CurrentData.start)!!
      val end = item.getOrNull(CurrentData.end)!!
      dao.regions[serverType].current_gacha.last().start = start
      dao.regions[serverType].current_gacha.last().end = end
    }

    //向卡池中放卡
    query = kotlin.runCatching {
      DataBaseProvider.query(DB.DATA.ordinal) {
        CurrentData.select(
          CurrentData.type eq "STUDENT" and (CurrentData.server eq type.dbName)
        ).toList()
      }
    }.getOrNull()?: mutableListOf()

    for (item in query){
      val start = item.getOrNull(CurrentData.start)!!
      val end = item.getOrNull(CurrentData.end)!!
      val value = item.getOrNull(CurrentData.value)!!
      for (pool in dao.regions[serverType].current_gacha){
        if (pool.start == start && pool.end == end){
          pool.characters = pool.characters.plus(value)
        }
      }
    }

    //event
    query = kotlin.runCatching {
      DataBaseProvider.query(DB.DATA.ordinal) {
        CurrentData.select(
          CurrentData.type eq "EVENT" and (CurrentData.server eq type.dbName)
        ).toList()
      }
    }.getOrNull()?: mutableListOf()

    for (item in query){
      val start = item.getOrNull(CurrentData.start)!!
      val end = item.getOrNull(CurrentData.end)!!
      val value = item.getOrNull(CurrentData.value)!!
      dao.regions[serverType].current_events = dao.regions[serverType].current_events.plus(CurrentEvents(value, start, end))
    }

    //raid
    query = kotlin.runCatching {
      DataBaseProvider.query(DB.DATA.ordinal) {
        CurrentData.join(Raid, JoinType.INNER, additionalConstraint = {CurrentData.value eq Raid.Id}).select {
          CurrentData.type eq "RAID" and (CurrentData.server eq type.dbName)
        }.toList()
      }
    }.getOrNull()?: mutableListOf()

    for (item in query){
      val start = item.getOrNull(CurrentData.start)!!
      val end = item.getOrNull(CurrentData.end)!!
      val value = item.getOrNull(CurrentData.value)!!
      val terrain = when(type){
        ServerLocale.GLOBAL -> item.getOrNull(Raid.CurrentGLB)!!
        ServerLocale.JP -> item.getOrNull(Raid.CurrentJPN)!!
        ServerLocale.CN -> item.getOrNull(Raid.CurrentGLB)!!
      }
      dao.regions[serverType].current_raid = dao.regions[serverType].current_raid.plus(CurrentRaid(value, terrain, start, end))
    }

    return dao
  }
}

// 中文说明：定义 GachaGroup 类型，用于封装本模块的数据或处理行为。
data class GachaGroup(
  val Id : Int,
  val Icon : String,
  val NameEn : String,
  val NameJp : String,
  val Rarity : String,
  val ItemList : List<List<Int>>
)

// 中文说明：定义 Regions 类型，用于封装本模块的数据或处理行为。
data class Regions(
  val abbreviation : String?,
  val NameEn : String = "",
  val NameJp : String = "",
  val NameKr : String = "",
  val NameTw : String = "",
  val NameCn : String = "",
  val studentlevel_max : Int = 0,
  val weaponlevel_max : Int = 0,
  val bondlevel_max : Int = 0,
  val gear1_max : Int = 0,
  val gear2_max : Int = 0,
  val gear3_max : Int = 0,
  val campaign_max : Int = 0,
  val events : List<Int> = mutableListOf(),
  val event_max : Int = 0,
  val event_701_max : Int = 0,
  val event_701_challenge_max : Int = 0,
  val commission_max : Int = 0,
  val bounty_max : Int = 0,
  val schooldungeon_max : Int = 0,
  var current_gacha : List<CurrentGacha> = mutableListOf(),
  var current_events : List<CurrentEvents> = mutableListOf(),
  var current_raid : List<CurrentRaid> = mutableListOf()
)

// 中文说明：定义 CurrentGacha 类型，用于封装本模块的数据或处理行为。
data class CurrentGacha(
  var characters : List<Int>,
  var start : Long,
  var end : Long
)

// 中文说明：定义 CurrentEvents 类型，用于封装本模块的数据或处理行为。
data class CurrentEvents(
  val event : Int,
  val start : Long,
  val end : Long
)

// 中文说明：定义 CurrentRaid 类型，用于封装本模块的数据或处理行为。
data class CurrentRaid(
  val raid : Int,
  val terrain : String,
  val start : Long,
  val end : Long
)

// 中文说明：定义 ChangeLog 类型，用于封装本模块的数据或处理行为。
data class ChangeLog(
  val date : String,
  val contents : List<String>
)
