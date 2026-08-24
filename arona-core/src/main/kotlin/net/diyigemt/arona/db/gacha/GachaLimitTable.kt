/**
 * 文件说明：本文件属于 数据库表结构、实体和数据访问逻辑。
 * 具体职责：围绕 GachaLimitTable 提供对应的实现、数据结构或测试。
 */
package net.diyigemt.arona.db.gacha

import net.diyigemt.arona.config.AronaGachaConfig
import net.diyigemt.arona.db.DataBaseProvider
import net.diyigemt.arona.util.TimeUtil
import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.LongEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update

object GachaLimitTable: IdTable<Long>(name = "GachaLimit") {
  override val id: Column<EntityID<Long>> = long("qq").entityId()
  val group: Column<Long> = long("group")
  val count: Column<Int> = integer("count")

  override val primaryKey: PrimaryKey = PrimaryKey(id, group)

  fun update() {
    val today = TimeUtil.today()
    if (AronaGachaConfig.day == today) return
    AronaGachaConfig.day = today
    forceUpdate()
  }

  fun forceUpdate(group0: Long? = null) {
    DataBaseProvider.query {
      if (group0 == null) {
        GachaLimitTable.update() {
          it[count] = 0
        }
      } else {
        GachaLimitTable.update({ group eq group0 }) {
          it[count] = 0
        }
      }
    }
  }
}

// 中文说明：定义 GachaLimit 类型，用于封装本模块的数据或处理行为。
class GachaLimit(id: EntityID<Long>): LongEntity(id) {
  companion object: LongEntityClass<GachaLimit>(GachaLimitTable)
  var group by GachaLimitTable.group
  var count by GachaLimitTable.count
}
