/**
 * 文件说明：抽卡结果图片渲染器。
 * 具体职责：按参考图布局生成 2340x1080 的 PNG 结果图——
 *           头像在上、星级底板紧贴头像下方、不写角色名、右下角显示抽卡次数(无 OK 按钮)。
 *           头像以角色 id 为键缓存到本地, 未命中时按 avatar/icon 地址下载。
 */
package net.diyigemt.arona.gacha

import net.diyigemt.arona.runtime.RuntimeLog
import net.diyigemt.arona.util.GameKeeUtil
import net.diyigemt.arona.util.GeneralUtils
import net.diyigemt.arona.util.NetworkUtil
import org.jsoup.Jsoup
import java.awt.Color
import java.awt.BasicStroke
import java.awt.Font
import java.awt.GradientPaint
import java.awt.Graphics2D
import java.awt.GraphicsEnvironment
import java.awt.Rectangle
import java.awt.RenderingHints
import java.awt.geom.Path2D
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

object GachaV2ImageRenderer {

  private const val CANVAS_WIDTH = 2340
  private const val CANVAS_HEIGHT = 1080

  // 十连网格: 5 列 x 2 行(参考图实测)
  private const val CELL_WIDTH = 246
  private const val COL_STEP = 282
  private const val FIRST_COL = 468
  private const val FIRST_ROW = 138
  private const val ROW_STEP = 342
  private const val CELL_HEIGHT = 258
  private const val STAR_PLATE_HEIGHT = 66

  // 右下角抽卡次数块(参考图 OK 按钮位置, OK 已移除)
  private const val COUNT_X = 1812
  private const val COUNT_Y = 870
  private const val COUNT_WIDTH = 368
  private const val COUNT_HEIGHT = 115

  /** 头像缓存目录(相对项目图片根目录) */
  private const val AVATAR_FOLDER = "/gacha/avatar"
  /** 渲染结果输出目录 */
  private const val RESULT_FOLDER = "/gacha/result"

  /** 按角色 id 查头像缓存: 优先匹配 <id>.<后缀>, 兼容旧命名 <id>-<角色名>.<后缀> */
  fun findCachedAvatar(characterId: Long, avatarDir: File): File? =
    GameKeeGachaPoolSource.findCachedImage(characterId, avatarDir)

  /** 未命中缓存时按 avatar/icon 地址下载头像并保存为 <角色id>.<后缀>; 失败返回 null */
  fun downloadAvatar(result: GachaV2Service.GachaDrawResult, avatarDir: File): File? {
    val url = result.avatar.trim()
    if (url.isBlank()) return null
    val normalized = if (url.startsWith("//")) "https:$url" else url
    val suffix = normalized.substringBefore("?").substringAfterLast(".", "")
      .takeIf { it.length in 2..5 } ?: "png"
    val file = File(avatarDir, "${result.id}.$suffix")
    runCatching { avatarDir.mkdirs() }
    val ok = runCatching {
      val headers = if (normalized.contains("gamekee.com")) {
        GameKeeUtil.gameKeeHeaders("https://www.gamekee.com/ba/")
      } else {
        mapOf(
          "accept" to "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8",
          "accept-language" to "zh-CN,zh;q=0.9",
          "referer" to "https://ba.kivo.wiki/",
          "user-agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36",
        )
      }
      NetworkUtil.request(Jsoup.connect(normalized))
        .headers(headers)
        .ignoreContentType(true)
        .maxBodySize(15 * 1024 * 1024)
        .timeout(15_000)
        .execute()
        .bodyStream().use { input -> file.outputStream().use { output -> input.copyTo(output) } }
      file.isFile && file.length() > 0
    }.onFailure { RuntimeLog.warning("下载抽卡头像失败 $normalized: ${it.message}") }.getOrDefault(false)
    return file.takeIf { ok }
  }

  /** 加载头像图片: 先查缓存, 未命中则下载; 无头像地址或读取失败时返回 null(渲染占位图) */
  internal fun loadAvatar(result: GachaV2Service.GachaDrawResult, avatarDir: File): BufferedImage? {
    if (result.avatar.isBlank()) return null
    val file = findCachedAvatar(result.id, avatarDir)
      ?: runCatching { downloadAvatar(result, avatarDir) }.getOrNull()
      ?: return null
    return runCatching { ImageIO.read(file) }.getOrNull()
  }

  /** 渲染抽卡结果图, 输出 PNG 文件; 头像目录使用项目默认目录 */
  fun render(results: List<GachaV2Service.GachaDrawResult>, pityCount: Int, output: File): File {
    val avatarDir = GeneralUtils.localImageFile(AVATAR_FOLDER).apply { mkdirs() }
    return render(results, pityCount, avatarDir, output)
  }

  /** 渲染抽卡结果图(测试可直接注入临时头像目录) */
  internal fun render(results: List<GachaV2Service.GachaDrawResult>, pityCount: Int, avatarDir: File, output: File): File {
    require(results.isNotEmpty()) { "抽卡结果为空" }
    require(results.size <= 10) { "最多支持十连(10 个结果), 实际 ${results.size}" }
    val image = BufferedImage(CANVAS_WIDTH, CANVAS_HEIGHT, BufferedImage.TYPE_4BYTE_ABGR)
    val g = image.createGraphics()
    try {
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
      g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
      g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
      g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
      drawBackground(g)
      results.forEachIndexed { index, result -> drawCell(g, index, result, avatarDir) }
      drawCountBlock(g, pityCount)
    } finally {
      g.dispose()
    }
    output.parentFile?.mkdirs()
    ImageIO.write(image, "png", output)
    return output
  }

  /** 生成一个唯一的结果输出文件 */
  fun newResultFile(): File {
    val dir = GeneralUtils.localImageFile(RESULT_FOLDER).apply { mkdirs() }
    return File(dir, "gacha-${System.currentTimeMillis()}-${java.util.UUID.randomUUID().toString().take(8)}.png")
  }

  private fun drawBackground(g: Graphics2D) {
    g.paint = GradientPaint(0f, 0f, Color(160, 213, 246), 0f, CANVAS_HEIGHT.toFloat(), Color(250, 241, 241))
    g.fillRect(0, 0, CANVAS_WIDTH, CANVAS_HEIGHT)
  }

  private fun drawCell(g: Graphics2D, index: Int, result: GachaV2Service.GachaDrawResult, avatarDir: File) {
    val col = index % 5
    val row = index / 5
    val x = FIRST_COL + col * COL_STEP
    val y = FIRST_ROW + row * ROW_STEP
    val cell = RoundRectangle2D.Float(x.toFloat(), y.toFloat(), CELL_WIDTH.toFloat(), CELL_HEIGHT.toFloat(), 24f, 24f)

    // 卡片背景: 白到浅蓝渐变
    g.paint = GradientPaint(x.toFloat(), y.toFloat(), Color(255, 255, 255), x.toFloat(), (y + CELL_HEIGHT).toFloat(), Color(226, 240, 251))
    g.fill(cell)

    // 头像区域: 卡片上部, 星级底板之上
    val avatarArea = Rectangle(x, y, CELL_WIDTH, CELL_HEIGHT - STAR_PLATE_HEIGHT)
    val avatar = loadAvatar(result, avatarDir)
    if (avatar != null) {
      val clip = g.clip
      g.clip = cell
      drawCover(g, avatar, avatarArea)
      g.clip = clip
    } else {
      drawPlaceholder(g, result, avatarArea)
    }

    // 星级底板: 深灰蓝渐变, 紧贴头像下方
    val plateY = y + CELL_HEIGHT - STAR_PLATE_HEIGHT
    val plate = RoundRectangle2D.Float(x.toFloat(), plateY.toFloat(), CELL_WIDTH.toFloat(), STAR_PLATE_HEIGHT.toFloat(), 24f, 24f)
    g.paint = GradientPaint(x.toFloat(), plateY.toFloat(), Color(112, 128, 148), x.toFloat(), (plateY + STAR_PLATE_HEIGHT).toFloat(), Color(76, 92, 112))
    g.fill(plate)
    g.color = Color(150, 165, 185)
    g.fillRect(x + 12, plateY + 3, CELL_WIDTH - 24, 2)

    drawStars(g, x + CELL_WIDTH / 2, plateY + STAR_PLATE_HEIGHT / 2 + 1, result.star)

    // 星级外框: 1星白色 / 2星金色 / 3星粉紫色, 圈住整张卡片(含星级底板)
    g.stroke = BasicStroke(8f)
    g.color = starBorderColor(result.star)
    g.draw(cell)
  }

  /** 星级外框颜色: 1星白色, 2星金色, 3星粉紫色 */
  private fun starBorderColor(star: Int): Color = when (star) {
    3 -> Color(206, 96, 240)
    2 -> Color(242, 178, 46)
    else -> Color(255, 255, 255)
  }

  /** 头像等比缩放铺满区域并居中裁剪 */
  private fun drawCover(g: Graphics2D, src: BufferedImage, area: Rectangle) {
    val scale = max(area.width.toDouble() / src.width, area.height.toDouble() / src.height)
    val w = (src.width * scale).toInt()
    val h = (src.height * scale).toInt()
    g.drawImage(src, area.x + (area.width - w) / 2, area.y + (area.height - h) / 2, w, h, null)
  }

  /** 无头像(如彩蛋角色/下载失败)时的占位图 */
  private fun drawPlaceholder(g: Graphics2D, result: GachaV2Service.GachaDrawResult, area: Rectangle) {
    g.paint = GradientPaint(area.x.toFloat(), area.y.toFloat(), Color(140, 180, 210), area.x.toFloat(), (area.y + area.height).toFloat(), Color(96, 134, 168))
    g.fill(RoundRectangle2D.Float((area.x + 8).toFloat(), (area.y + 8).toFloat(), (area.width - 16).toFloat(), (area.height - 16).toFloat(), 20f, 20f))
    g.font = loadFont(Font.BOLD, 100f)
    g.color = Color(255, 255, 255)
    drawCentered(g, if (result.custom) "A" else "?", area)
  }

  /** 绘制 1-3 颗金色五角星, 居中对齐 */
  private fun drawStars(g: Graphics2D, centerX: Int, centerY: Int, star: Int) {
    if (star <= 0) return
    val outer = 30.0
    val inner = 15.0
    val spacing = 74
    val startX = centerX - spacing * (star - 1) / 2.0
    for (i in 0 until star) {
      val cx = startX + i * spacing
      val path = starPath(cx, centerY.toDouble(), outer, inner)
      g.paint = GradientPaint(cx.toFloat(), (centerY - outer).toFloat(), Color(255, 224, 120), cx.toFloat(), (centerY + outer).toFloat(), Color(238, 168, 46))
      g.fill(path)
      g.color = Color(180, 122, 28)
      g.draw(path)
    }
  }

  private fun starPath(cx: Double, cy: Double, outer: Double, inner: Double): Path2D.Double {
    val path = Path2D.Double()
    for (i in 0 until 10) {
      val radius = if (i % 2 == 0) outer else inner
      val angle = Math.toRadians(-90.0 + i * 36.0)
      val x = cx + radius * cos(angle)
      val y = cy + radius * sin(angle)
      if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.closePath()
    return path
  }

  /** 右下角保底计数块(青色圆角块 + 白色计数文本, 跟随抽卡变动) */
  private fun drawCountBlock(g: Graphics2D, pityCount: Int) {
    val block = RoundRectangle2D.Float(COUNT_X.toFloat(), COUNT_Y.toFloat(), COUNT_WIDTH.toFloat(), COUNT_HEIGHT.toFloat(), 30f, 30f)
    g.paint = GradientPaint(COUNT_X.toFloat(), COUNT_Y.toFloat(), Color(112, 216, 250), COUNT_X.toFloat(), (COUNT_Y + COUNT_HEIGHT).toFloat(), Color(56, 164, 226))
    g.fill(block)
    g.color = Color(255, 255, 255, 56)
    g.fill(RoundRectangle2D.Float(COUNT_X.toFloat(), COUNT_Y.toFloat(), COUNT_WIDTH.toFloat(), (COUNT_HEIGHT / 2).toFloat(), 30f, 30f))
    g.font = loadFont(Font.BOLD, 58f)
    g.color = Color.WHITE
    drawCentered(g, "${pityCount}抽", Rectangle(COUNT_X, COUNT_Y, COUNT_WIDTH, COUNT_HEIGHT))
  }

  private fun drawCentered(g: Graphics2D, text: String, area: Rectangle) {
    val fm = g.fontMetrics
    val x = area.x + (area.width - fm.stringWidth(text)) / 2
    val y = area.y + (area.height - fm.height) / 2 + fm.ascent
    g.drawString(text, x, y)
  }

  /** 优先使用项目注册的中文字体, 未注册时回退到系统字体(Windows 可回退渲染中文) */
  private fun loadFont(style: Int, size: Float): Font {
    val family = GraphicsEnvironment.getLocalGraphicsEnvironment().availableFontFamilyNames.firstOrNull {
      it.contains("SourceHanSans", true) || it.contains("Noto Sans CJK", true) || it.contains("PingFang", true)
    }
    return if (family != null) Font(family, style, size.toInt())
    else Font(Font.SANS_SERIF, style, size.toInt()).deriveFont(size)
  }
}
