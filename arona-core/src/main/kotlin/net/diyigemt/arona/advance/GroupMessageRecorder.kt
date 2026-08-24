/**
 * 文件说明：本文件属于 运行时增强功能与后台推送任务。
 * 具体职责：围绕 GroupMessageRecorder 提供对应的实现、数据结构或测试。
 */
package net.diyigemt.arona.advance

import net.diyigemt.arona.handler.AronaEventHandler
import net.diyigemt.arona.service.AronaGroupService
import net.mamoe.mirai.event.events.GroupMessageEvent

// 中文说明：定义 GroupMessageRecorder 对象，集中提供本文件的共享功能。
object GroupMessageRecorder: AronaEventHandler<GroupMessageEvent>, AronaGroupService {
  override suspend fun handle(event: GroupMessageEvent) {

  }

  override val id: Int = 11
  override val name: String = "岁月史书"
  override var enable: Boolean = true

  override fun init() {
    registerService()
  }
}
