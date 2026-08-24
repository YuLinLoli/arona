/**
 * 文件说明：本文件属于 数据库表结构、实体和数据访问逻辑。
 * 具体职责：围绕 TarotRecordTable 提供对应的实现、数据结构或测试。
 */
package net.diyigemt.arona.db.tarot

import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.LongEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.sql.Column

object TarotRecordTable: IdTable<Long>(name = "TarotRecord") {
  override val id: Column<EntityID<Long>> = long("qq").entityId()
  val group: Column<Long> = long("group")
  val day: Column<Int> = integer("day")
  val tarot: Column<Int> = integer("tarot")
  val positive: Column<Boolean> = bool("positive")

  override val primaryKey: PrimaryKey = PrimaryKey(id, group)
}

// 中文说明：定义 TarotRecord 类型，用于封装本模块的数据或处理行为。
class TarotRecord(id: EntityID<Long>): LongEntity(id) {
  companion object: LongEntityClass<TarotRecord>(TarotRecordTable)
  var group by TarotRecordTable.group
  var day by TarotRecordTable.day
  var tarot by TarotRecordTable.tarot
  var positive by TarotRecordTable.positive
}
