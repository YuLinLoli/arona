/**
 * 文件说明：本文件属于 数据库表结构、实体和数据访问逻辑。
 * 具体职责：围绕 DBConstant 提供对应的实现、数据结构或测试。
 */
package net.diyigemt.arona.db

// 中文说明：定义 DBConstant 对象，集中提供本文件的共享功能。
object DBConstant {

  private const val GLOBAL_DB_NAME: String = "arona.db"
  private const val SCHALE_DB_NAME: String = "schale.db"

  val dbNameList = mutableListOf(GLOBAL_DB_NAME, SCHALE_DB_NAME)
}

// 中文说明：定义 DB 类型，用于封装本模块的数据或处理行为。
enum class DB{
  DEFAULT,
  DATA
}
