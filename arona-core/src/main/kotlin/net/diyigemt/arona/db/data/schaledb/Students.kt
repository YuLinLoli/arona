/**
 * 文件说明：本文件属于 数据库表结构、实体和数据访问逻辑。
 * 具体职责：围绕 Students 提供对应的实现、数据结构或测试。
 */
package net.diyigemt.arona.db.data.schaledb

import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Table

/**
 *@Author hjn
 *@Create 2022/8/24
 */
object Students : Table("Students") {
  val studentID: Column<Int> = integer("studentID")
  val name: Column<String> = varchar("name", 15)
  val birthday: Column<String> = varchar("birthday", 5)
  // 抽卡等新功能所需字段: 完整落库, toModel 回读时不再丢失(旧库通过 migrateLegacySchema 补列)
  val starGrade: Column<Int> = integer("starGrade").default(0)
  val devName: Column<String> = varchar("devName", 50).default("")
  val pathName: Column<String> = varchar("pathName", 50).default("")
  val isReleased: Column<String> = varchar("isReleased", 50).default("")
  val isLimited: Column<Int> = integer("isLimited").default(0)

  override val primaryKey: PrimaryKey = PrimaryKey(studentID)
}
