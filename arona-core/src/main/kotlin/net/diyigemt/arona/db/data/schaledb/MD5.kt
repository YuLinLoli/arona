/**
 * 文件说明：本文件属于 数据库表结构、实体和数据访问逻辑。
 * 具体职责：围绕 MD5 提供对应的实现、数据结构或测试。
 */
package net.diyigemt.arona.db.data.schaledb

import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Table

/**
 *@Author hjn
 *@Create 2022/8/28
 */
object MD5 : Table("MD5"){
  val name : Column<String> = varchar("name", 20)
  val remote : Column<String> = varchar("source", 10)
  val md5 : Column<String> = varchar("md5", 32)
}
