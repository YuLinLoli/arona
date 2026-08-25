/**
 * 文件说明：本文件属于 通用工具、网络、图片和业务辅助函数。
 * 具体职责：围绕 GameKeeUtil 提供对应的实现、数据结构或测试。
 */
package net.diyigemt.arona.util

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonParser
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
  private const val contentCdnUrl = "https://api-cdn.gamekee.com/wiki2.0/pro/829/content"

  fun getScheduleNoteImages(): List<File> {
    val treeResponse = NetworkUtil.request(Jsoup.connect(entryTreeUrl))
      .headers(gameKeeHeaders("https://www.gamekee.com/ba/second/137392"))
      .get()
      .text()
    val root = Gson().fromJson(treeResponse, GameKeeEntryResponse::class.java)
    val contentId = root.data?.child.orEmpty()
      .firstOrNull { it.name == "当期玩法" }
      ?.child.orEmpty()
      .firstOrNull { it.name.contains("日程笔记") }
      ?.content_id
      ?: throw IllegalStateException("GameKee schedule note entry not found")

    val referer = "https://www.gamekee.com/ba/${contentId}.html"
    val detail = Gson().fromJson(
      NetworkUtil.request(Jsoup.connect("$entryTreeUrl1$contentId"))
        .headers(gameKeeHeaders(referer))
        .get()
        .text(),
      GameKeeContentResponse::class.java
    )
    val version = detail.data?.version?.takeIf { it.isNotBlank() }
      ?: throw IllegalStateException("GameKee schedule note version not found")
    val contentUrl = "$contentCdnUrl/$contentId.json?v=$version"
    val contentJson = NetworkUtil.request(Jsoup.connect(contentUrl))
      .header("accept", "application/json, text/plain, */*")
      .header("accept-encoding", "identity")
      .header("accept-language", "zh-CN,zh;q=0.9,zh-Hans;q=0.8,und;q=0.7,zh-Hant;q=0.6,ja;q=0.5")
      .header("connection", "keep-alive")
      .header("dnt", "1")
      .header("origin", "https://www.gamekee.com")
      .header("referer", "https://www.gamekee.com/")
      .header("sec-ch-ua", "\"Not;A=Brand\";v=\"8\", \"Chromium\";v=\"150\", \"Google Chrome\";v=\"150\"")
      .header("sec-ch-ua-mobile", "?0")
      .header("sec-ch-ua-platform", "\"Windows\"")
      .header("sec-fetch-dest", "empty")
      .header("sec-fetch-mode", "cors")
      .header("sec-fetch-site", "same-site")
      .header("user-agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36")
      .ignoreContentType(true)
      .maxBodySize(15 * 1024 * 1024)
      .get()
      .text()
    val content = JsonParser.parseString(contentJson).asJsonObject.get("content")?.asString
      ?: throw IllegalStateException("GameKee schedule note content not found")
    val nodes = JsonParser.parseString(content)
    val imageUrls = mutableListOf<String>()
    var markerFound = false
    fun collect(node: JsonElement) {
      if (!node.isJsonObject) return
      val obj = node.asJsonObject
      if (!markerFound && obj.get("text")?.asString?.contains("日程笔记再见") == true) {
        markerFound = true
      }
      if (markerFound && obj.get("type")?.asString == "image") {
        obj.get("src")?.asString?.let { imageUrls.add(if (it.startsWith("//")) "https:$it" else it) }
      }
      obj.get("children")?.takeIf { it.isJsonArray }?.asJsonArray?.forEach(::collect)
    }
    nodes.asJsonArray.forEach(::collect)
    if (imageUrls.isEmpty()) {
      throw IllegalStateException("GameKee schedule note images not found")
    }
    return downloadImages(imageUrls, contentId, referer)
  }

  private fun downloadImages(imageUrls: List<String>, contentId: Int, referer: String): List<File> {
    val files = mutableListOf<File>()
    try {
      imageUrls.forEachIndexed { index, imageUrl ->
        val suffix = imageUrl.substringBefore("?").substringAfterLast(".", "png")
          .takeIf { it.length in 2..5 }?.let { ".${it}" } ?: ".png"
        val file = File.createTempFile("arona-activity-guide-$contentId-$index-", suffix).apply { deleteOnExit() }
        files.add(file)
        NetworkUtil.request(Jsoup.connect(imageUrl))
          .header("accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8")
          .header("accept-encoding", "identity")
          .header("accept-language", "zh-CN,zh;q=0.9,zh-Hans;q=0.8,und;q=0.7,zh-Hant;q=0.6,ja;q=0.5")
          .header("referer", referer)
          .ignoreContentType(true)
          .maxBodySize(15 * 1024 * 1024)
          .execute().bodyStream().use { input -> file.outputStream().use { output -> input.copyTo(output) } }
      }
      return files
    } catch (throwable: Throwable) {
      files.forEach(File::delete)
      throw throwable
    }
  }
  fun getJpActivityGuide(): List<File> = getActivityGuide("日服活动攻略")

  fun getGlobalActivityGuide(): List<File> = getActivityGuide("国际服活动攻略")

  fun getCnActivityGuide(): List<File> = getActivityGuide("国服活动攻略")

  private fun getActivityGuide(guideName: String): List<File> {
    val treeResponse = NetworkUtil.request(Jsoup.connect(entryTreeUrl))
      .headers(gameKeeHeaders("https://www.gamekee.com/ba/second/137392"))
      .get()
      .text()

    val root = Gson().fromJson(treeResponse, GameKeeEntryResponse::class.java)
    val contentId = root.data?.child.orEmpty()
      .firstOrNull { it.name == "当期活动 | 当期卡池" }
      ?.child.orEmpty()
      .firstOrNull { it.name == guideName }
      ?.content_id
      ?: throw IllegalStateException("GameKee activity guide not found: $guideName")

    val referer = "https://www.gamekee.com/ba/${contentId}.html"
    val detailResponse = NetworkUtil.request(Jsoup.connect("$entryTreeUrl1$contentId"))
      .headers(gameKeeHeaders(referer))
      .get()
      .text()
    val imageUrls = Gson().fromJson(detailResponse, GameKeeContentResponse::class.java)
      .data?.thumb_list.orEmpty()
      .map { if (it.startsWith("//")) "https:$it" else it }
      .filter { it.contains("/pro/") }
    if (imageUrls.isEmpty()) {
      throw IllegalStateException("GameKee activity guide images not found: $guideName")
    }

    val imageFiles = mutableListOf<File>()
    try {
      imageUrls.forEachIndexed { index, imageUrl ->
        val suffix = imageUrl.substringBefore("?")
          .substringAfterLast(".", "png")
          .takeIf { it.length in 2..5 }
          ?.let { ".$it" }
          ?: ".png"
        val imageFile = File.createTempFile("arona-activity-guide-$contentId-$index-", suffix).apply {
          deleteOnExit()
        }
        imageFiles.add(imageFile)
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
      }
      return imageFiles
    } catch (throwable: Throwable) {
      imageFiles.forEach { it.delete() }
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
