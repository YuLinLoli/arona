package net.diyigemt.arona.standalone

import net.diyigemt.arona.runtime.RuntimePaths
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 独立模式滚动日志文件输出。
 * 日志写到 arona-standalone/logs/arona-yyyy-MM-dd.log(按天分文件), 单文件超过 10MB 时滚动为 .1/.2 等后缀。
 * 通过 TeePrintStream 拦截 System.out/err, 落盘前会去掉 ANSI 颜色转义码。
 */
object StandaloneLogFile {
  private const val MAX_FILE_BYTES = 10L * 1024 * 1024
  private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
  private val timeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
  private val ansiPattern = Regex("\\u001B\\[[0-9;]*m")
  private val lock = Any()

  @Volatile private var directory: Path? = null
  @Volatile private var currentFile: Path? = null
  @Volatile private var currentSize: Long = 0
  @Volatile private var closed = false

  fun init() {
    synchronized(lock) {
      if (directory != null) return
      val dir = RuntimePaths.prepareStandaloneRoot().resolve("logs")
      runCatching { Files.createDirectories(dir) }
      directory = dir
      currentFile = todayFile(dir)
      currentSize = sizeOf(currentFile)
    }
  }

  /** 写一行日志(自动去掉 ANSI 颜色码并加时间戳), 跨天或超限时自动滚动 */
  fun log(line: String) {
    if (closed) return
    val stripped = ansiPattern.replace(line, "")
    if (stripped.isEmpty()) return
    synchronized(lock) {
      val dir = directory ?: return
      val today = todayFile(dir)
      if (today != currentFile) {
        currentFile = today
        currentSize = sizeOf(today)
      }
      rollIfNeeded()
      val file = currentFile ?: return
      val bytes = ("${LocalDateTime.now().format(timeFormatter)} $stripped${System.lineSeparator()}").toByteArray(Charsets.UTF_8)
      runCatching { Files.write(file, bytes, StandardOpenOption.CREATE, StandardOpenOption.APPEND) }
      currentSize += bytes.size
    }
  }

  fun close() {
    closed = true
  }

  fun logDirectory(): Path? = directory

  private fun rollIfNeeded() {
    val file = currentFile ?: return
    if (currentSize < MAX_FILE_BYTES) return
    val target = (1..9).firstOrNull { seq -> !Files.exists(file.resolveSibling("${file.fileName}.$seq")) }
      ?.let { file.resolveSibling("${file.fileName}.$it") }
      ?: file.resolveSibling("${file.fileName}.${System.currentTimeMillis()}")
    runCatching { Files.move(file, target) }
    currentFile = todayFile(directory!!)
    currentSize = sizeOf(currentFile)
  }

  private fun todayFile(dir: Path): Path = dir.resolve("arona-${LocalDate.now().format(dateFormatter)}.log")

  private fun sizeOf(file: Path?): Long =
    if (file != null && Files.exists(file)) runCatching { Files.size(file) }.getOrDefault(0) else 0
}

/** 把控制台输出同时写入滚动日志文件(落盘前去除 ANSI 颜色码)的 PrintStream 包装 */
class TeePrintStream(private val console: java.io.PrintStream) : java.io.PrintStream(console) {
  override fun print(x: String?) {
    console.print(x)
    if (x != null) StandaloneLogFile.log(x)
  }

  override fun print(x: Any?) {
    print(x?.toString() ?: "null")
  }

  override fun print(x: Boolean) = print(x.toString())
  override fun print(x: Char) = print(x.toString())
  override fun print(x: Int) = print(x.toString())
  override fun print(x: Long) = print(x.toString())
  override fun print(x: Float) = print(x.toString())
  override fun print(x: Double) = print(x.toString())
  override fun print(x: CharArray?) = print(x?.concatToString() ?: "null")

  override fun println(x: String?) {
    console.println(x)
    if (x != null) StandaloneLogFile.log(x)
  }

  override fun println(x: Any?) = println(x?.toString() ?: "null")
  override fun println(x: Boolean) = println(x.toString())
  override fun println(x: Char) = println(x.toString())
  override fun println(x: Int) = println(x.toString())
  override fun println(x: Long) = println(x.toString())
  override fun println(x: Float) = println(x.toString())
  override fun println(x: Double) = println(x.toString())
  override fun println(x: CharArray?) = println(x?.concatToString() ?: "null")

  override fun println() {
    console.println()
    StandaloneLogFile.log("")
  }

  // 字节级输出直通控制台, 避免进入 PrintStream 内部缓冲造成延迟
  override fun write(b: Int) = console.write(b)
  override fun write(buf: ByteArray, off: Int, len: Int) = console.write(buf, off, len)
  override fun write(buf: ByteArray) = console.write(buf)

  override fun flush() {
    console.flush()
  }
}
