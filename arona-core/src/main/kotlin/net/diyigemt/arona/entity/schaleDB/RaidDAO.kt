/**
 * 文件说明：本文件属于 业务数据实体与接口响应模型。
 * 具体职责：围绕 RaidDAO 提供对应的实现、数据结构或测试。
 */
package net.diyigemt.arona.entity.schaleDB

import net.diyigemt.arona.db.DB
import net.diyigemt.arona.db.DataBaseProvider
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll

/**
 *@Author hjn
 *@Create 2022/8/20
 */
// 中文说明：定义 RaidDAO 类型，用于封装本模块的数据或处理行为。
data class RaidDAO(
  var Raid: List<Raid> = mutableListOf(),
//    val SeasonRewardGlobal: List<SeasonRewardGlobal>,
//    val SeasonRewardJp: List<SeasonRewardJp>,
//    val TimeAttack: List<TimeAttack>,
//    val TimeAttackRules: List<TimeAttackRule>,
//    val WorldRaid: List<WorldRaid>
) : BaseDAO{
  fun getRaidNameById(raidID : Int) : String?{
    for(item in Raid){
      if(item.Id == raidID) return item.NameCn
    }

    return null
  }

  fun isRaidReleased(raidID: Int) : Boolean{
    for(item in Raid){
      if(item.Id == raidID) return item.IsReleased[0]
    }

    return false
  }

  override fun sendToDataBase() {
    DataBaseProvider.query(DB.DATA.ordinal) { net.diyigemt.arona.db.data.schaledb.Raid.deleteAll() }
    for (item in Raid){
      DataBaseProvider.query(DB.DATA.ordinal) {
        net.diyigemt.arona.db.data.schaledb.Raid.insert {
          it[Id] = item.Id
          it[IsReleased] = item.IsReleased.first()
          it[NameCn] = item.NameCn ?: "peroro-sama"
          it[CurrentJPN] = ""
          it[CurrentGLB] = ""
        }
      }
    }
  }

  override fun <T : BaseDAO> toModel(dao: T): T {
    dao as RaidDAO
    dao.Raid = mutableListOf()

    val query = kotlin.runCatching {
      DataBaseProvider.query(DB.DATA.ordinal) {
        net.diyigemt.arona.db.data.schaledb.Raid.selectAll().toList()
      }
    }.getOrNull()?: mutableListOf()

    for (item in query){
      val id = item.getOrNull(net.diyigemt.arona.db.data.schaledb.Raid.Id)!!
      val isReleased = item.getOrNull(net.diyigemt.arona.db.data.schaledb.Raid.IsReleased)!!
      val nameCN = item.getOrNull(net.diyigemt.arona.db.data.schaledb.Raid.NameCn)?: "peroro-sama"
      dao.Raid = dao.Raid.plus(Raid(Id = id, IsReleased = mutableListOf(isReleased, isReleased), NameCn = nameCN))
    }

    return dao
  }
}

// 中文说明：定义 Raid 类型，用于封装本模块的数据或处理行为。
data class Raid(
  var ArmorType: String = "",
  var BulletType: String = "",
  var BulletTypeInsane: String = "",
  var EnemyList: List<List<Int>> = mutableListOf(),
  var Faction: String = "",
  var Icon: String = "",
  var IconBG: String = "",
  var Id: Int,
  var IsReleased: List<Boolean>,
  var IsReleasedInsane: List<Boolean> = mutableListOf(),
  var NameCn: String,
  var NameEn: String = "",
  var NameJp: String = "",
  var NameKr: String = "",
  var NameTh: String = "",
  var NameTw: String = "",
  var PathName: String = "",
  var ProfileCn: String = "",
  var ProfileEn: String = "",
  var ProfileJp: String = "",
  var ProfileKr: String = "",
  var ProfileTh: String = "",
  var ProfileTw: String = "",
  var RaidSkill: List<RaidSkill> = mutableListOf(),
  var Terrain: List<String> = mutableListOf()
)

// 中文说明：定义 SeasonRewardGlobal 类型，用于封装本模块的数据或处理行为。
data class SeasonRewardGlobal(
    val End: Int,
    val RaidId: Int,
    val Rewards: List<List<Any>>,
    val Season: Int,
    val Start: Int,
    val Terrain: String
)

// 中文说明：定义 SeasonRewardJp 类型，用于封装本模块的数据或处理行为。
data class SeasonRewardJp(
    val End: Int,
    val RaidId: Int,
    val Rewards: List<List<Any>>,
    val Season: Int,
    val Start: Int,
    val Terrain: String
)

// 中文说明：定义 TimeAttack 类型，用于封装本模块的数据或处理行为。
data class TimeAttack(
    val ArmorType: String,
    val BulletType: String,
    val DungeonType: String,
    val EnemyLevel: List<Int>,
    val Formations: List<Formation>,
    val Icon: String,
    val Id: Int,
    val IsReleased: List<Boolean>,
    val MaxDifficulty: Int,
    val Rules: List<List<Long>>,
    val Terrain: String
)

// 中文说明：定义 TimeAttackRule 类型，用于封装本模块的数据或处理行为。
data class TimeAttackRule(
    val DescEn: String,
    val DescJp: String,
    val DescKr: String,
    val Icon: String,
    val Id: Long,
    val NameEn: String,
    val NameJp: String,
    val NameKr: String
)

// 中文说明：定义 WorldRaid 类型，用于封装本模块的数据或处理行为。
data class WorldRaid(
    val ArmorType: String,
    val BulletType: String,
    val EnemyList: List<List<Int>>,
    val IconBG: String,
    val Id: Int,
    val IsReleased: List<Boolean>,
    val Level: List<Int>,
    val NameCn: String,
    val NameEn: String,
    val NameJp: String,
    val NameKr: String,
    val NameTh: String,
    val NameTw: String,
    val PathName: String,
    val RaidSkill: List<RaidSkillX>,
    val Rewards: List<List<List<Double>>>,
    val Terrain: List<String>,
    val WorldBossHP: Long
)

// 中文说明：定义 RaidSkill 类型，用于封装本模块的数据或处理行为。
data class RaidSkill(
    var ATGCost: Int,
    var DescCn: String,
    var DescEn: String,
    var DescJp: String,
    var DescKr: String,
    var DescTh: String,
    var DescTw: String,
    var Icon: String,
    var Id: String,
    var MinDifficulty: Int,
    var NameCn: String,
    var NameEn: String,
    var NameJp: String,
    var NameKr: String,
    var NameTh: String,
    var NameTw: String,
    var ParametersCn: List<List<String>>,
    var ParametersEn: List<List<String>>,
    var ParametersJp: List<List<String>>,
    var ParametersKr: List<List<String>>,
    var ParametersTh: List<List<String>>,
    var ParametersTw: List<List<String>>,
    var SkillType: String
)

// 中文说明：定义 Formation 类型，用于封装本模块的数据或处理行为。
data class Formation(
    val EnemyList: List<Int>,
    val Grade: List<Int>,
    val Id: Int,
    val Level: List<Int>
)

// 中文说明：定义 RaidSkillX 类型，用于封装本模块的数据或处理行为。
data class RaidSkillX(
    val ATGCost: Int,
    val DescCn: Any,
    val DescEn: String,
    val DescJp: String,
    val DescKr: String,
    val DescTh: Any,
    val DescTw: Any,
    val Icon: String,
    val Id: String,
    val MinDifficulty: Int,
    val NameCn: Any,
    val NameEn: String,
    val NameJp: String,
    val NameKr: String,
    val SkillType: String
)
