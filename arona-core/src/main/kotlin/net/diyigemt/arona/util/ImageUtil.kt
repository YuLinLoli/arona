/**
 * 文件说明：本文件属于 通用工具、网络、图片和业务辅助函数。
 * 具体职责：围绕 ImageUtil 提供对应的实现、数据结构或测试。
 */
package net.diyigemt.arona.util
import net.diyigemt.arona.Arona
import net.diyigemt.arona.interfaces.InitializedFunction
import net.diyigemt.arona.runtime.RuntimeLog
import net.diyigemt.arona.runtime.RuntimeServices
import net.diyigemt.arona.util.ActivityUtil.DEFAULT_CALENDAR_FONT_SIZE
import net.diyigemt.arona.util.ActivityUtil.DEFAULT_CALENDAR_LINE_MARGIN
import java.awt.*
import java.awt.image.BufferedImage
import java.io.File
import kotlin.math.max

// 中文说明：定义 ImageUtil 对象，集中提供本文件的共享功能。
object ImageUtil : InitializedFunction() {

  private const val DEFAULT_PADDING: Int = 10
  private const val FONT_NAME = "SourceHanSansCN-Normal.otf"
  private const val FontFolder: String = "/font"
  private var font: Font? = null

  fun createCalendarImage(eventLength: Int, contentMaxLength: Int, titleLength: Int = 20, fontSize: Float = DEFAULT_CALENDAR_FONT_SIZE.toFloat()): Pair<BufferedImage, Graphics2D> {
    val width = fontSize * (max(contentMaxLength, titleLength) + 20) * 0.8
    val height = (eventLength + 4) * (fontSize + DEFAULT_CALENDAR_LINE_MARGIN) + 2 * DEFAULT_CALENDAR_LINE_MARGIN
    val img = BufferedImage(width.toInt(), height.toInt(), BufferedImage.TYPE_4BYTE_ABGR)
    val g = img.createGraphics()
    g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS,
      RenderingHints.VALUE_FRACTIONALMETRICS_ON)
    g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
      RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
    g.font = g.font.deriveFont(Font.PLAIN).deriveFont(fontSize)
    font = font?.deriveFont(Font.PLAIN)?.deriveFont(fontSize)
    return img to g
  }

  fun init(group: Pair<BufferedImage, Graphics2D>, color: Color) {
    val g = group.second
    val img = group.first
    g.color = color
    g.fillRect(0, 0, img.width, img.height)
  }

  fun drawRoundRect(group: Pair<BufferedImage, Graphics2D>, x: Int, y: Int, w: Int, h: Int, r: Int, color: Color) {
    val g = group.second
    g.color = color
    g.fillRoundRect(x, y, w, h, r, r)
  }

  fun drawLine(
    group: Pair<BufferedImage, Graphics2D>,
    x: Int,
    y: Int,
    w: Int,
    h: Int,
    type: LineType = LineType.HORIZON,
    color: Color = Color.BLACK
  ) {
    val g = group.second
    g.color = color
    when(type) {
      LineType.HORIZON -> g.fillRect(x, y, w, h)
      LineType.VERTICAL -> g.fillRect(x, y, h, w)
    }
  }

  fun drawTextAlign(
    group: Pair<BufferedImage, Graphics2D>,
    str: String,
    x: Int,
    y: Int,
    align: TextAlign = TextAlign.LEFT,
    color: Color = Color.BLACK,
    fontWeight: Int = Font.PLAIN,
    fontSize: Float? = null
  ) {
    val g = group.second
    val img = group.first
    g.font = font ?: g.font
    g.color = color
    g.font = g.font.deriveFont(fontWeight)
    if (fontSize != null) {
      g.font = g.font.deriveFont(fontSize)
    }
    val width = g.fontMetrics.stringWidth(str)
    when (align) {
      TextAlign.LEFT -> g.drawString(str, x + DEFAULT_PADDING, y)
      TextAlign.RIGHT -> g.drawString(str, img.width - width - DEFAULT_PADDING, y)
      TextAlign.CENTER -> g.drawString(str, (img.width - x - width) / 2 + x, y)
    }
  }

  fun drawText(
    group: Pair<BufferedImage, Graphics2D>,
    str: String,
    y: Int,
    align: TextAlign = TextAlign.LEFT,
    color: Color = Color.BLACK,
    fontWeight: Int = Font.PLAIN,
  ) {
    drawTextAlign(group, str, 0, y, align, color, fontWeight)
  }

  fun BufferedImage.scale(x: Float, y: Float, color: Color = Color.WHITE): BufferedImage {
    val width = (this.width * x).toInt()
    val height = (this.height * y).toInt()
    val scale = this.getScaledInstance(width, height, Image.SCALE_SMOOTH)
    val after = BufferedImage(width, height, BufferedImage.TYPE_4BYTE_ABGR)
    after.graphics.drawImage(scale, 0, 0, color, null)
    return after
  }

  fun BufferedImage.scale(scale: Float, color: Color = Color.WHITE) = this.scale(scale, scale, color)

  enum class TextAlign {
    LEFT, RIGHT, CENTER
  }

  enum class LineType {
    HORIZON, VERTICAL
  }

  override fun init() {
    // 下载中文字体, 独立模式与插件模式共用
    val block: () -> Unit = {
      kotlin.runCatching {
        val folder = if (RuntimeServices.isStandalone) {
          GeneralUtils.localImageFile(FontFolder).also { it.mkdirs() }
        } else {
          File(Arona.dataFolderPath(FontFolder)).also { it.mkdirs() }
        }
        val path = "$FontFolder/$FONT_NAME"
        val fontFile = if (RuntimeServices.isStandalone) {
          GeneralUtils.localImageFile(path)
        } else {
          Arona.dataFolderFile(path)
        }
        var downloaded = false
        var attempt = 0
        while (!downloaded && attempt < 3) {
          attempt++
          runCatching {
            if (!fontFile.exists() || fontFile.length() < 1024L * 100L) {
              NetworkUtil.downloadFileFile(path, fontFile)
            }
          }.onSuccess {
            if (fontFile.exists() && fontFile.length() >= 1024L * 100L) downloaded = true
          }
          if (!downloaded) Thread.sleep(2000L)
        }
        if (!downloaded) throw IllegalStateException("字体文件下载失败")
        val f = Font.createFont(Font.TRUETYPE_FONT, fontFile)
        GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(f)
        font = f
        RuntimeLog.info("中文字体初始化成功")
      }.onFailure {
        RuntimeLog.warning("字体注册失败, 使用默认字体, 可能会导致中文乱码")
      }
    }
    if (RuntimeServices.isStandalone) {
      Thread(block).apply {
        isDaemon = true
        name = "arona-font-init"
        start()
      }
    } else {
      Arona.runSuspend { block() }
    }
  }
}
