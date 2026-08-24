/**
 * 文件说明：本文件属于 数据库表结构、实体和数据访问逻辑。
 * 具体职责：围绕 TeacherNameTable 提供对应的实现、数据结构或测试。
 */
package net.diyigemt.arona.db.name

import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.LongEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.sql.Column

object TeacherNameTable: IdTable<Long>(name = "TeacherName") {
  override val id: Column<EntityID<Long>> = long("qq").entityId()
  val group: Column<Long> = long("group")
  val name: Column<String> = char("name", 20)

  override val primaryKey: PrimaryKey = PrimaryKey(id, group)
}

// 中文说明：定义 TeacherName 类型，用于封装本模块的数据或处理行为。
class TeacherName(id: EntityID<Long>): LongEntity(id) {
  companion object: LongEntityClass<TeacherName>(TeacherNameTable)
  var group by TeacherNameTable.group
  var name by TeacherNameTable.name
}
