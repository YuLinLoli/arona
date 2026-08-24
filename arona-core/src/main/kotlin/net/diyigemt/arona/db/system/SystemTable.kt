/**
 * 文件说明：本文件属于 数据库表结构、实体和数据访问逻辑。
 * 具体职责：围绕 SystemTable 提供对应的实现、数据结构或测试。
 */
package net.diyigemt.arona.db.system

import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.LongEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.Column

object SystemTable: LongIdTable(name = "System") {
  val key: Column<String> = char("key", 255)
  val value: Column<String> = char("value", 255)
}

// 中文说明：定义 SystemTableModel 类型，用于封装本模块的数据或处理行为。
class SystemTableModel(id: EntityID<Long>): LongEntity(id) {
  companion object: LongEntityClass<SystemTableModel>(SystemTable)
  var key by SystemTable.key
  var value by SystemTable.value
}
