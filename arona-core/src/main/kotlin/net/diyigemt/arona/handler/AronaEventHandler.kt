/**
 * 文件说明：本文件属于 Mirai 事件监听与消息处理器。
 * 具体职责：围绕 AronaEventHandler 提供对应的实现、数据结构或测试。
 */
package net.diyigemt.arona.handler

import net.diyigemt.arona.service.AronaService
import net.diyigemt.arona.service.AronaServiceManager
import net.mamoe.mirai.event.events.MessageEvent

// 中文说明：定义 AronaEventHandler 接口，约定相关实现需要提供的能力。
interface AronaEventHandler<in T: MessageEvent>: AronaService {

  suspend fun preHandle(event: T) {
    if (AronaServiceManager.checkService(this, event.sender, event.subject) == null) {
      handle(event)
    }
  }
  suspend fun handle(event: T)
}
