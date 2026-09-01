/**
 * 文件说明：本文件属于 数据库表结构、实体和数据访问逻辑。
 * 具体职责：围绕 SchaleDataBase 提供对应的实现、数据结构或测试。
 */
package net.diyigemt.arona.db.data.schaledb

import net.diyigemt.arona.db.DB
import net.diyigemt.arona.db.DataBaseProvider
import net.diyigemt.arona.runtime.RuntimeLog
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.Transaction

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
      RuntimeLog.warning(it.toString())
      RuntimeLog.warning("数据库修改操作失败，删除当前库重建")
      deleteDataBase()
      initDataBase()
    }
  }

  private fun initDataBase(){
    DataBaseProvider.query(DB.DATA.ordinal) {
      migrateLegacySchema(it)
      SchemaUtils.createMissingTablesAndColumns(
        MD5,

        Students,
        Localization,
        Raid,
        CurrentData
      )
    }
  }

  /**
   * 旧库升级：sqlite-jdbc 对 Exposed 生成的 ALTER TABLE ... ADD COLUMN 会抛
   * "Query returns results"，因此这里先用原生 Statement 手动补齐 Students 新增列，
   * Exposed 便不会再生成 ALTER，避免触发删除重建逻辑导致数据丢失。
   */
  internal fun migrateLegacySchema(transaction: Transaction) {
    ensureColumn(transaction, "Students", "starGrade", "INTEGER NOT NULL DEFAULT 0")
    ensureColumn(transaction, "Students", "devName", "VARCHAR(50) NOT NULL DEFAULT ''")
    ensureColumn(transaction, "Students", "pathName", "VARCHAR(50) NOT NULL DEFAULT ''")
    ensureColumn(transaction, "Students", "isReleased", "VARCHAR(50) NOT NULL DEFAULT ''")
    ensureColumn(transaction, "Students", "isLimited", "INTEGER NOT NULL DEFAULT 0")
  }

  private fun ensureColumn(transaction: Transaction, table: String, column: String, definition: String) {
    val statement = (transaction.connection.connection as java.sql.Connection).createStatement()
    try {
      // 表不存在时跳过, 由 Exposed 负责建表
      val tableExists = statement.executeQuery(
        "SELECT count(*) FROM sqlite_master WHERE type = 'table' AND name = '$table'"
      ).use { rs -> rs.next() && rs.getInt(1) > 0 }
      if (!tableExists) return
      val existing = mutableListOf<String>()
      statement.executeQuery("PRAGMA table_info($table)").use { rs ->
        while (rs.next()) existing.add(rs.getString("name"))
      }
      if (column !in existing) {
        statement.execute("ALTER TABLE $table ADD COLUMN $column $definition")
      }
    } finally {
      statement.close()
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
