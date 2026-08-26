/**
 * 文件说明：本文件属于 数据库表结构、实体和数据访问逻辑。
 * 具体职责：围绕 TarotTable 提供对应的实现、数据结构或测试。
 */
package net.diyigemt.arona.db.tarot

import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.Column

object TarotTable: IntIdTable(name = "Tarot") {
  val name: Column<String> = varchar("name", 30)
  val positive: Column<String> = text("positive")
  val negative: Column<String> = text("negative")
  val number: Column<Int> = integer("number")

}

// 中文说明：定义 Tarot 类型，用于封装本模块的数据或处理行为。
class Tarot(id: EntityID<Int>) : IntEntity(id) {
  companion object: IntEntityClass<Tarot>(TarotTable)

  val name by TarotTable.name
  val positive by TarotTable.positive
  val negative by TarotTable.negative
  val number by TarotTable.number
}
