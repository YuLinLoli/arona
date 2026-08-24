/**
 * 文件说明：本文件属于 通用工具、网络、图片和业务辅助函数。
 * 具体职责：围绕 GameKeeUtil 提供对应的实现、数据结构或测试。
 */
package net.diyigemt.arona.util

import com.google.gson.Gson
import net.diyigemt.arona.entity.Activity
import net.diyigemt.arona.entity.ActivityType
import net.diyigemt.arona.entity.GameKeeDAO
import net.diyigemt.arona.entity.GameKeeEntryResponse
import net.diyigemt.arona.entity.GameKeeContentResponse
import net.diyigemt.arona.entity.ServerLocale
import org.jsoup.Jsoup
import java.io.File
import java.util.*

/**
 *@Author hjn
 *@Create 2022/7/21
 */
// 中文说明：定义 GameKeeUtil 对象，集中提供本文件的共享功能。
object GameKeeUtil {
  private const val url = "https://www.gamekee.com/v1/activity/query"
  private const val entryTreeUrl = "https://www.gamekee.com/v1/entry/treesByPidV1?pid=137392"
  private const val entryTreeUrl1 = "https://www.gamekee.com/v1/content/detail/"

  fun getJpActivityGuide(): File {
    val treeResponse = NetworkUtil.request(Jsoup.connect(entryTreeUrl))
      .headers(gameKeeHeaders("https://www.gamekee.com/ba/second/137392"))
      .get()
      .text()

    val root = Gson().fromJson(treeResponse, GameKeeEntryResponse::class.java)
    val contentId = root.data?.child.orEmpty()
      .firstOrNull { it.name == "当期活动 | 当期卡池" }
      ?.child.orEmpty()
      .firstOrNull { it.name == "日服活动攻略" }
      ?.content_id
      ?: throw IllegalStateException("GameKee JP activity guide data not found")

    val referer = "https://www.gamekee.com/ba/${contentId}.html"
    val detailResponse = NetworkUtil.request(Jsoup.connect("$entryTreeUrl1$contentId"))
      .headers(gameKeeHeaders(referer))
      .get()
      .text()
    val thumb = Gson().fromJson(detailResponse, GameKeeContentResponse::class.java)
      .data?.thumb
      ?.split(",")
      ?.getOrNull(1)
      ?.trim()
      ?.takeIf { it.isNotEmpty() }
      ?: throw IllegalStateException("GameKee JP activity guide data not found")

    val imageUrl = if (thumb.startsWith("//")) "https:$thumb" else thumb
    val imageFile = File.createTempFile("arona-jp-activity-guide-$contentId-", ".png").apply { deleteOnExit() }
    try {
      val imageResponse = NetworkUtil.request(Jsoup.connect(imageUrl))
        .header("accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8")
        .header("accept-encoding", "gzip, deflate, br, zstd")
        .header("accept-language", "zh-CN,zh;q=0.9,zh-Hans;q=0.8,und;q=0.7,zh-Hant;q=0.6,ja;q=0.5")
        .header("referer", referer)
        .ignoreContentType(true)
        .maxBodySize(15 * 1024 * 1024)
        .execute()
      imageResponse.bodyStream().use { input ->
        imageFile.outputStream().use { output -> input.copyTo(output) }
      }
      return imageFile
    } catch (throwable: Throwable) {
      imageFile.delete()
      throw throwable
    }
  }

  private fun gameKeeHeaders(referer: String): Map<String, String> = mapOf(
    "accept" to "application/json, text/plain, */*",
    "accept-encoding" to "gzip, deflate, br, zstd",
    "accept-language" to "zh-CN,zh;q=0.9,zh-Hans;q=0.8,und;q=0.7,zh-Hant;q=0.6,ja;q=0.5",
    "access-token" to "",
    "connection" to "keep-alive",
//    "cookie" to "wk_uuid=fa2aff77-c180-482e-85a4-968cf9f3478d; ba_server_id=15; _c_WBKFRo=vHoSvizOQFyLXXDeSsQWQZkfe6RcxoLq2l19Ilju; wikiTheme=light; viewport_height=1275; __qc_wId=724; viewport_width=1271",
    "device-num" to "1",
    "dnt" to "1",
    "game-alias" to "ba",
    "lang" to "zh-cn",
    "referer" to referer,
    "sec-ch-ua" to "\"Not;A=Brand\";v=\"8\", \"Chromium\";v=\"150\", \"Google Chrome\";v=\"150\"",
    "sec-ch-ua-mobile" to "?0",
    "sec-ch-ua-platform" to "\"Windows\"",
    "sec-fetch-dest" to "empty",
    "sec-fetch-mode" to "cors",
    "sec-fetch-site" to "same-origin",
    "user-agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36",
    "x-requested-with" to "XMLHttpRequest"
  )
  fun getEventData(server: ServerLocale) : Pair<MutableList<Activity>, MutableList<Activity>>{
    val res = NetworkUtil.request(Jsoup.connect(url))
      .header("game-alias", "ba")
      .data("active_at", ((Calendar.getInstance().timeInMillis) / 1000).toString())
      .get()
      .text()
    return analyze(Gson().fromJson(res, GameKeeDAO::class.java), server)
  }

  private fun analyze(json : GameKeeDAO, server: ServerLocale) : Pair<MutableList<Activity>, MutableList<Activity>>{
    val active : MutableList<Activity> = mutableListOf()
    val pending : MutableList<Activity> = mutableListOf()
    val method = when(server){
      ServerLocale.GLOBAL -> ActivityUtil::insertEnActivity
      ServerLocale.JP -> ActivityUtil::insertJpActivity
      ServerLocale.CN -> ActivityUtil::insertCnActivity
    }
    val source = when(server){
      ServerLocale.GLOBAL -> ActivityUtil.ActivityENSource.GAME_KEE
      ServerLocale.JP -> ActivityUtil.ActivityJPSource.GAME_KEE
      ServerLocale.CN -> ActivityUtil.ActivityCNSource.GAME_KEE
    }

    for(i in json.data){
      if(i.pub_area == server.serverName){
        method.call(
          Calendar.getInstance().time,
          Date(i.begin_at * 1000),
          Date(i.end_at * 1000),
          active,
          pending,
          i.title,
          source,
          ActivityType.NULL
        )
      }
    }

    return active to pending
  }
}
