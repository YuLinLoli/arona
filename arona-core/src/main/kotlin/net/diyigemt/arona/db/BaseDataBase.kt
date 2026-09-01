/**
 * 文件说明：本文件属于 数据库表结构、实体和数据访问逻辑。
 * 具体职责：围绕 BaseDataBase 提供对应的实现、数据结构或测试。
 */
package net.diyigemt.arona.db

import net.diyigemt.arona.command.cache.GachaCache
import net.diyigemt.arona.db.DataBaseProvider.query
import net.diyigemt.arona.db.activity.ActivityCalendarTable
import net.diyigemt.arona.db.announcement.RemoteActionTable
import net.diyigemt.arona.db.gacha.*
import net.diyigemt.arona.db.image.ImageTable
import net.diyigemt.arona.db.name.GameNameTable
import net.diyigemt.arona.db.name.TeacherNameTable
import net.diyigemt.arona.db.system.SystemTable
import net.diyigemt.arona.db.tarot.TarotRecordTable
import net.diyigemt.arona.db.tarot.TarotTable
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.Transaction

// 中文说明：定义 BaseDataBase 对象，集中提供本文件的共享功能。
object BaseDataBase {

  fun init() {
    query {
      migrateLegacySchema(it)
      SchemaUtils.createMissingTablesAndColumns(
        GachaCharacterTable,
        GachaPoolTable,
        GachaPoolCharacterTable,
        GachaHistoryTable,
        GachaLimitTable,
        GachaUserSettingTable,
        GachaPityTable,
        TarotTable,
        TarotRecordTable,
        TeacherNameTable,
        GameNameTable,
        ImageTable,
        SystemTable,
        RemoteActionTable,
        ActivityCalendarTable
      )
    }
    GachaCache.init()
  }

  /**
   * 旧库升级：Exposed 对已有表缺列生成的 ALTER TABLE ... ADD COLUMN 经由
   * PreparedStatement.executeUpdate 执行时，sqlite-jdbc 会抛 "Query returns results"，
   * 因此这里先用原生 Statement 手动补齐新增列，Exposed 便不会再生成 ALTER。
   */
  internal fun migrateLegacySchema(transaction: Transaction) {
    ensureColumn(transaction, "Tarot", "number", "INTEGER NOT NULL DEFAULT 0")
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
}
