/**
 * 文件说明：本文件属于 数据库表结构、实体和数据访问逻辑。
 * 具体职责：围绕 GameNameTable 提供对应的实现、数据结构或测试。
 */
package net.diyigemt.arona.db.name

import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.LongEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.sql.Column

object GameNameTable: IdTable<Long>(name = "GameName") {
  override val id: Column<EntityID<Long>> = long("qq").entityId()
  val name: Column<String> = char("name", 50)

  override val primaryKey: PrimaryKey = PrimaryKey(id)
}

// 中文说明：定义 GameName 类型，用于封装本模块的数据或处理行为。
class GameName(id: EntityID<Long>): LongEntity(id) {
  companion object: LongEntityClass<GameName>(GameNameTable)
  var name by GameNameTable.name
}
