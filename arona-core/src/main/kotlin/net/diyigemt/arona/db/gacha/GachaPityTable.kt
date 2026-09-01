/**
 * 文件说明：本文件属于 数据库表结构、实体和数据访问逻辑。
 * 具体职责：围绕 GachaPityTable 提供对应的实现、数据结构或测试。
 * 说明：记录每个用户在各服务器「距上次 pickup 的抽数」，用于 200 抽保底计数。
 */
package net.diyigemt.arona.db.gacha

import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.LongEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.sql.Column

object GachaPityTable : IdTable<Long>(name = "GachaPity") {
  override val id: Column<EntityID<Long>> = long("qq").entityId()
  /** 服务器名: 日服/国服/国际服 */
  val server: Column<String> = char("server", 10)
  /** 距上次 pickup 的抽数, 满 200 保底出 pickup 后清零 */
  val count: Column<Int> = integer("count").default(0)

  override val primaryKey: PrimaryKey = PrimaryKey(id, server)
}

class GachaPity(id: EntityID<Long>) : LongEntity(id) {
  companion object : LongEntityClass<GachaPity>(GachaPityTable)
  var server by GachaPityTable.server
  var count by GachaPityTable.count
}