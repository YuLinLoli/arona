package net.diyigemt.arona.standalone.commands

import net.diyigemt.arona.service.AronaService
import net.diyigemt.arona.service.AronaServiceManager

class StandaloneServiceInfo(
  override val id: Int,
  override val name: String,
  val groupOnly: Boolean = false,
  val adminOnly: Boolean = false,
) : AronaService {
  override var enable: Boolean = true
  override fun init() {}
}

object StandaloneServices {
  val GACHA_CONFIG = StandaloneServiceInfo(1, "抽卡配置", adminOnly = true)
  val CONFIG = StandaloneServiceInfo(23, "配置管理", adminOnly = true)
  val ACTIVITY = StandaloneServiceInfo(3, "活动查询")
  val ACTIVITY_NOTIFY = StandaloneServiceInfo(12, "活动推送")
  val DATA_SYNC = StandaloneServiceInfo(19, "数据同步服务")
  val TRAINER = StandaloneServiceInfo(20, "地图与学生攻略")
  val GACHA_SINGLE = StandaloneServiceInfo(4, "抽卡单抽", groupOnly = true)
  val GACHA_MULTI = StandaloneServiceInfo(5, "抽卡十连", groupOnly = true)
  val GACHA_DOG = StandaloneServiceInfo(6, "抽卡狗叫查询", groupOnly = true)
  val GACHA_HISTORY = StandaloneServiceInfo(7, "抽卡历史查询", groupOnly = true)
  val TAROT = StandaloneServiceInfo(16, "塔罗牌")
  val EMERGENCY_STOP = StandaloneServiceInfo(17, "紧急停止")
  val CALL_ME = StandaloneServiceInfo(18, "自定义昵称", groupOnly = true)
  val GAME_NAME = StandaloneServiceInfo(21, "游戏名记录")
  val GAME_NAME_SEARCH = StandaloneServiceInfo(22, "游戏名反查")
  val TASK = StandaloneServiceInfo(24, "定时任务", adminOnly = true)
  val BACKUP = StandaloneServiceInfo(25, "备份恢复", adminOnly = true)

  val all: List<StandaloneServiceInfo> = listOf(
    CONFIG,
    GACHA_CONFIG,
    ACTIVITY,
    ACTIVITY_NOTIFY,
    DATA_SYNC,
    TRAINER,
    GACHA_SINGLE,
    GACHA_MULTI,
    GACHA_DOG,
    GACHA_HISTORY,
    TAROT,
    EMERGENCY_STOP,
    CALL_ME,
    GAME_NAME,
    GAME_NAME_SEARCH,
    TASK,
    BACKUP,
  )

  fun registerAll() {
    all.forEach { AronaServiceManager.register(it) }
  }
}
