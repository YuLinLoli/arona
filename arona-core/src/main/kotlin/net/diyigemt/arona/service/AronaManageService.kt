/**
 * 文件说明：本文件属于 Arona 核心业务服务与服务生命周期。
 * 具体职责：围绕 AronaManageService 提供对应的实现、数据结构或测试。
 */
package net.diyigemt.arona.service

import net.diyigemt.arona.Arona
import net.diyigemt.arona.config.AronaConfig
import net.mamoe.mirai.contact.Contact
import net.mamoe.mirai.contact.User
import net.mamoe.mirai.message.code.MiraiCode

// 中文说明：定义 AronaManageService 接口，约定相关实现需要提供的能力。
interface AronaManageService: AronaService {

  fun checkAdmin(user: User, contact: Contact): Boolean {
    if (!AronaConfig.managerGroup.contains(user.id)) {
      if (AronaConfig.permissionDeniedMessage != "") {
        Arona.runSuspend {
          contact.sendMessage(MiraiCode.deserializeMiraiCode(AronaConfig.permissionDeniedMessage, contact))
        }
      }
      return false
    }
    return true
  }

}
