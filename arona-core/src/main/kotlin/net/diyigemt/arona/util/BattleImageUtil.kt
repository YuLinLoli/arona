/**
 * 文件说明：本文件属于 通用工具、网络、图片和业务辅助函数。
 * 具体职责：围绕 BattleImageUtil 提供对应的实现、数据结构或测试。
 */
package net.diyigemt.arona.util

import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * 本类用作总力战与大决战的图片生成
 */
// 中文说明：定义 BattleImageUtil 对象，集中提供本文件的共享功能。
object BattleImageUtil {
  private val bossMap = mapOf(
    "비나" to "大蛇",
    "고즈" to "GOZ",
    "게부라" to "盖布拉",
    "그레고리오" to "格里高利",
    "호버크래프트" to "气垫船",
    "쿠로카게" to "猫鬼",
    "예로니무스" to "主教",
    "KAITEN FX Mk.0" to "寿司",
    "시로&쿠로" to "黑白",
    "페로로지라" to "鸡斯拉",
    "호드" to "HOD",
    "헤세드" to "赫塞德(球)",
  )
  private val bossSurroundings = mapOf(
    "시가지전" to "市街战",
    "실내전" to "室内战",
    "야외전" to "野外战",
  )

  /**
 * 获取JP大决战信息
 *
 * 该函数通过网络请求获取arona.ai网站上的大决战信息，包括当前期数、boss名称和环境信息
 *
 * @return Map<String, String>? 包含大决战信息的映射表，包含以下键值对：
 *         - "number": 当前期数
 *         - "boss": boss名称
 *         - "surroundings": 环境信息
 *         如果请求失败或解析失败则返回null
 */
fun getJPGrandBattleInfo(): Map<String, String>? {
    val client = OkHttpClient()
    val request = Request.Builder()
      .url("https://arona.ai/egraph")
      .addHeader("Accept","text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7")
      .addHeader("Cache-control","max-age=0")
      .addHeader("Content-Type", "application/json")
      .get()
      .build()

    // 发送网络请求并解析响应数据
    try {
      val response = client.newCall(request).execute()
      val string = response.body?.string()
      if (string == null)return null
      //当前期数
      val str = string.split("<!-- -->. <!-- -->")[0].substringAfterLast(">")
      //boss名称
      val boss = bossMap[string.split("<!-- -->. <!-- -->")[1].substringBefore("<").substringBefore("·")]  ?: ""
      //环境信息
      val surroundings = bossSurroundings[string.split("<!-- -->. <!-- -->")[1].substringBefore("<").substringAfter("·")] ?: ""
      return mapOf(
        "number" to str,
        "boss" to boss,
        "surroundings" to surroundings,
      )
    }catch (e: Exception){
      e.printStackTrace()
    }finally {
      client.dispatcher.executorService.shutdown()
    }

    return null
  }

  fun createBattleImage(){

  }
}
