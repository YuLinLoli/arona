/**
 * 文件说明：本文件属于 数据库表结构、实体和数据访问逻辑。
 * 具体职责：围绕 ImageTable 提供对应的实现、数据结构或测试。
 */
package net.diyigemt.arona.db.image

import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.LongEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.Column

object ImageTable: LongIdTable(name = "Image") {
  val name: Column<String> = char("name", 255)
  val path: Column<String> = char("path", 255)
  val hash: Column<String> = char("hash", 255)
  val type: Column<Int> = integer("type")
}

// 中文说明：定义 ImageTableModel 类型，用于封装本模块的数据或处理行为。
class ImageTableModel(id: EntityID<Long>): LongEntity(id) {
  companion object: LongEntityClass<ImageTableModel>(ImageTable)
  var name by ImageTable.name
  var path by ImageTable.path
  var hash by ImageTable.hash
  var type by ImageTable.type
}
