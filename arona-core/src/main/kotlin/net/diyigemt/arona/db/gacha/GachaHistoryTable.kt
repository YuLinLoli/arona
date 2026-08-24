/**
 * 文件说明：本文件属于 数据库表结构、实体和数据访问逻辑。
 * 具体职责：围绕 GachaHistoryTable 提供对应的实现、数据结构或测试。
 */
package net.diyigemt.arona.db.gacha

import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.LongEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.sql.Column

object GachaHistoryTable: IdTable<Long>(name = "GachaHistory") {
  override val id: Column<EntityID<Long>> = long("qq").entityId()
  val group: Column<Long> = long("group")
  val pool: Column<Int> = integer("pool")
  val points: Column<Int> = integer("points")
  val count3: Column<Int> = integer("count3")
  val dog: Column<Int> = integer("dog")

  override val primaryKey: PrimaryKey = PrimaryKey(id, group, pool)
}

// 中文说明：定义 GachaHistory 类型，用于封装本模块的数据或处理行为。
class GachaHistory(id: EntityID<Long>): LongEntity(id) {
  companion object: LongEntityClass<GachaHistory>(GachaHistoryTable)
  var group by GachaHistoryTable.group
  var pool by GachaHistoryTable.pool
  var points by GachaHistoryTable.points
  var count3 by GachaHistoryTable.count3
  var dog by GachaHistoryTable.dog
}
