package net.diyigemt.arona.onebot

import com.google.gson.JsonElement
import com.google.gson.JsonObject

data class OneBotEvent(
  val time: Long,
  val selfId: Long,
  val postType: String,
  /** post_type=notice 时的通知类型: group_increase/group_decrease/group_ban 等 */
  val noticeType: String? = null,
  val messageType: String?,
  val subType: String?,
  val messageId: Long?,
  val userId: Long?,
  /** 通知事件中的操作者 QQ(如移出群的管理员) */
  val operatorId: Long? = null,
  val groupId: Long?,
  val rawMessage: String?,
  val message: JsonElement?,
  val sender: JsonObject?,
  val raw: JsonObject,
)

data class OneBotAction(
  val action: String,
  val params: JsonObject = JsonObject(),
  val echo: String,
)

data class OneBotActionResponse(
  val status: String,
  val retcode: Int,
  val data: JsonElement?,
  val message: String?,
  val wording: String?,
  val echo: String?,
  val raw: JsonObject,
) {
  val success: Boolean get() = status == "ok" && retcode == 0
}
