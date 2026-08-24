/**
 * 文件说明：本文件属于 远程服务调用与服务管理。
 * 具体职责：围绕 AnnouncementRemoteService 提供对应的实现、数据结构或测试。
 */
package net.diyigemt.arona.remote.action

import net.diyigemt.arona.Arona
import net.diyigemt.arona.remote.RemoteService
import net.diyigemt.arona.remote.RemoteServiceAction
import kotlin.reflect.KType
import kotlin.reflect.full.createType

// 中文说明：定义 AnnouncementRemoteService 类型，用于封装本模块的数据或处理行为。
class AnnouncementRemoteService : RemoteService<String> {
//  override val kType: KType = List::class.createType(listOf(KTypeProjection.invariant(AnnouncementItem::class.starProjectedType)))
  override val kType: KType = String::class.createType()
  override val type: RemoteServiceAction = RemoteServiceAction.ANNOUNCEMENT

  override fun handleService(data: String, time: String, aid: Long) {
    Arona.sendMessageToAdmin("新公告(${time}):\n${data}")
  }
}
