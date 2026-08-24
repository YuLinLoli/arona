/**
 * 文件说明：本文件属于 数据库表结构、实体和数据访问逻辑。
 * 具体职责：围绕 SchaleDataBase 提供对应的实现、数据结构或测试。
 */
package net.diyigemt.arona.db.data.schaledb

import net.diyigemt.arona.Arona
import net.diyigemt.arona.db.DB
import net.diyigemt.arona.db.DataBaseProvider
import org.jetbrains.exposed.sql.SchemaUtils

/**
 *@Author hjn
 *@Create 2022/8/26
 */
// 中文说明：定义 SchaleDataBase 对象，集中提供本文件的共享功能。
object SchaleDataBase {
  fun init(){
    kotlin.runCatching {
      initDataBase()
    }.onFailure {
      Arona.warning(it.toString())
      Arona.warning("数据库修改操作失败，删除当前库重建")
      deleteDataBase()
      initDataBase()
    }
  }

  private fun initDataBase(){
    DataBaseProvider.query(DB.DATA.ordinal) {
      SchemaUtils.createMissingTablesAndColumns(
        MD5,

        Students,
        Localization,
        Raid,
        CurrentData
      )
    }
  }

  private fun deleteDataBase(){
    DataBaseProvider.query(DB.DATA.ordinal) {
      SchemaUtils.drop(
        MD5,

        Students,
        Localization,
        Raid,
        CurrentData)
    }
  }
}
