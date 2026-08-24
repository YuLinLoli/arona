/**
 * 文件说明：本文件属于 数据库表结构、实体和数据访问逻辑。
 * 具体职责：围绕 GachaCharacterTable 提供对应的实现、数据结构或测试。
 */
package net.diyigemt.arona.db.gacha

import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.Column

object GachaCharacterTable: IntIdTable(name = "GachaCharacters") {
  val name: Column<String> = varchar("name", 10)
  val star: Column<Int> = integer("star")
  val limit: Column<Boolean> = bool("limit") // 区分常驻和限定

}

// 中文说明：定义 GachaCharacter 类型，用于封装本模块的数据或处理行为。
class GachaCharacter(id: EntityID<Int>) : IntEntity(id) {
  companion object: IntEntityClass<GachaCharacter>(GachaCharacterTable)

  var name by GachaCharacterTable.name
  var star by GachaCharacterTable.star
  var limit by GachaCharacterTable.limit
}
