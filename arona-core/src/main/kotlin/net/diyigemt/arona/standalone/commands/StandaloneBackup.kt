package net.diyigemt.arona.standalone.commands

import net.diyigemt.arona.db.DataBaseProvider
import net.diyigemt.arona.onebot.OneBotConfigLoader
import net.diyigemt.arona.quartz.QuartzProvider
import net.diyigemt.arona.runtime.AronaConfigLoader
import net.diyigemt.arona.runtime.OutgoingMessage
import net.diyigemt.arona.runtime.RuntimeLog
import net.diyigemt.arona.runtime.RuntimePaths
import net.diyigemt.arona.runtime.RuntimeServices
import net.diyigemt.arona.standalone.StandaloneAronaConfig
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * 配置备份与迁移: /备份 打包 arona.yml + onebot.yml + data 下的 SQLite 数据库;
 * /恢复 <备份文件名> 校验后覆盖恢复并热重载(onebot.yml 连接配置需重启生效)。
 * 备份/恢复期间会暂停定时任务并关闭数据库连接, 保证 SQLite 文件快照一致。
 */
object StandaloneBackup {
  private val backupsDir: Path get() = RuntimePaths.prepareStandaloneRoot().resolve("backups")
  private val stampFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")

  fun backup(arguments: List<String>): OutgoingMessage = when (arguments.firstOrNull()?.lowercase()) {
    "list", "列表" -> listBackups()
    null -> createBackup()
    else -> OutgoingMessage.text("用法: /备份 创建备份; /备份 list 查看已有备份")
  }

  fun restore(name: String?): OutgoingMessage {
    if (name.isNullOrBlank()) return OutgoingMessage.text("用法: /恢复 <备份文件名>，可用 /备份 list 查看")
    val zipFile = backupsDir.resolve(name.trim())
    if (!Files.exists(zipFile)) return OutgoingMessage.text("备份文件不存在: $name")
    val temp = runCatching { Files.createTempDirectory("arona-restore") }
      .getOrElse { return OutgoingMessage.text("创建临时目录失败: ${it.message}") }
    return try {
      val entries = extract(zipFile, temp)
      val restoredArona = temp.resolve("arona.yml")
      if ("arona.yml" !in entries || !Files.exists(restoredArona)) {
        return OutgoingMessage.text("备份中缺少 arona.yml，无法恢复")
      }
      runCatching { AronaConfigLoader.load(restoredArona) }
        .getOrElse { return OutgoingMessage.text("备份的 arona.yml 解析失败: ${it.message}") }
      // 校验通过后开始恢复: 暂停定时任务并关闭数据库/配置监听, 避免文件被占用
      RuntimeLog.info("开始恢复备份: $name")
      runCatching { QuartzProvider.pauseAll() }
      runCatching { DataBaseProvider.close() }
      runCatching { StandaloneAronaConfig.close() }
      // 覆盖配置文件与数据库文件
      val aronaFile = StandaloneAronaConfig.defaultFile()
      Files.copy(restoredArona, aronaFile, StandardCopyOption.REPLACE_EXISTING)
      val restoredOnebot = temp.resolve("onebot.yml")
      if (Files.exists(restoredOnebot)) {
        Files.copy(restoredOnebot, OneBotConfigLoader.defaultFile(), StandardCopyOption.REPLACE_EXISTING)
      }
      restoreDataDir(temp.resolve("data"))
      // 重新加载
      StandaloneAronaConfig.init(aronaFile)
      runCatching { DataBaseProvider.start() }
      runCatching { QuartzProvider.resumeAll() }
      val notice = if (Files.exists(restoredOnebot)) "\n注: onebot.yml 连接配置已恢复, 重启后生效" else ""
      OutgoingMessage.text("恢复完成: $name$notice")
    } catch (error: Exception) {
      runCatching { QuartzProvider.resumeAll() }
      OutgoingMessage.text("恢复失败: ${error.message ?: "未知错误"}")
    } finally {
      runCatching { temp.toFile().deleteRecursively() }
    }
  }

  private fun createBackup(): OutgoingMessage {
    val aronaFile = StandaloneAronaConfig.defaultFile()
    if (!Files.exists(aronaFile)) return OutgoingMessage.text("arona.yml 不存在，无法备份")
    runCatching { Files.createDirectories(backupsDir) }
      .onFailure { return OutgoingMessage.text("创建备份目录失败: ${it.message}") }
    val zipFile = backupsDir.resolve("arona-backup-${LocalDateTime.now().format(stampFormatter)}.zip")
    // 备份前暂停定时任务并关闭数据库, 保证 SQLite 文件快照一致
    runCatching { QuartzProvider.pauseAll() }
    runCatching { DataBaseProvider.close() }
    return try {
      ZipOutputStream(Files.newOutputStream(zipFile)).use { zip ->
        zip.putNextEntry(ZipEntry("arona.yml"))
        Files.newInputStream(aronaFile).use { it.copyTo(zip) }
        zip.closeEntry()
        val onebotFile = OneBotConfigLoader.defaultFile()
        if (Files.exists(onebotFile)) {
          zip.putNextEntry(ZipEntry("onebot.yml"))
          Files.newInputStream(onebotFile).use { it.copyTo(zip) }
          zip.closeEntry()
        }
        val dataDir = RuntimeServices.dataRoot
        if (dataDir != null && Files.isDirectory(dataDir)) {
          Files.walk(dataDir).use { stream ->
            stream.filter { Files.isRegularFile(it) }.forEach { file ->
              val relative = dataDir.relativize(file).toString().replace('\\', '/')
              zip.putNextEntry(ZipEntry("data/$relative"))
              Files.newInputStream(file).use { it.copyTo(zip) }
              zip.closeEntry()
            }
          }
        }
      }
      OutgoingMessage.text("备份完成: ${zipFile.fileName}\n路径: ${zipFile.toAbsolutePath()}")
    } catch (error: Exception) {
      runCatching { Files.deleteIfExists(zipFile) }
      OutgoingMessage.text("备份失败: ${error.message ?: "未知错误"}")
    } finally {
      runCatching { DataBaseProvider.start() }
      runCatching { QuartzProvider.resumeAll() }
    }
  }

  private fun listBackups(): OutgoingMessage {
    if (!Files.isDirectory(backupsDir)) return OutgoingMessage.text("还没有任何备份")
    val files = runCatching {
      Files.list(backupsDir).use { stream ->
        stream.filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".zip") }
          .map { it.fileName.toString() }
          .sorted()
          .toList()
      }
    }.getOrDefault(emptyList())
    if (files.isEmpty()) return OutgoingMessage.text("还没有任何备份")
    return OutgoingMessage.text(files.joinToString("\n", prefix = "已有备份:\n"))
  }

  private fun restoreDataDir(restoredData: Path) {
    val dataDir = RuntimeServices.dataRoot ?: return
    if (!Files.isDirectory(restoredData)) return
    Files.walk(restoredData).use { stream ->
      stream.filter { Files.isRegularFile(it) }.forEach { file ->
        val relative = restoredData.relativize(file).toString()
        val target = dataDir.resolve(relative)
        Files.createDirectories(target.parent)
        Files.copy(file, target, StandardCopyOption.REPLACE_EXISTING)
      }
    }
  }

  private fun extract(zipFile: Path, target: Path): Set<String> {
    val names = mutableSetOf<String>()
    ZipInputStream(Files.newInputStream(zipFile)).use { zip ->
      var entry = zip.nextEntry
      while (entry != null) {
        val name = entry.name
        val dest = target.resolve(name).normalize()
        if (!dest.startsWith(target)) throw IllegalArgumentException("非法的备份条目: $name")
        if (!entry.isDirectory) {
          Files.createDirectories(dest.parent)
          Files.newOutputStream(dest).use { out -> zip.copyTo(out) }
          names += name
        }
        zip.closeEntry()
        entry = zip.nextEntry
      }
    }
    return names
  }
}
