/**
 * 文件说明：本文件属于 通用工具、网络、图片和业务辅助函数。
 * 具体职责：围绕 MessageUtil 提供对应的实现、数据结构或测试。
 */
package net.diyigemt.arona.util

import net.diyigemt.arona.Arona
import net.mamoe.mirai.contact.Contact
import net.mamoe.mirai.contact.User
import net.mamoe.mirai.message.MessageReceipt
import net.mamoe.mirai.message.code.MiraiCode
import net.mamoe.mirai.message.data.At
import net.mamoe.mirai.message.data.MessageChain
import net.mamoe.mirai.message.data.MessageChainBuilder

// 中文说明：定义 MessageUtil 对象，集中提供本文件的共享功能。
object MessageUtil {

  fun at(user: User, msg: MessageChain): MessageChain {
    return At(user).plus(msg)
  }

  fun at(user: User, msg: String): MessageChain {
    return At(user).plus(msg)
  }

  fun atAndCTRL(user: User, msg: MessageChain): MessageChain {
    return At(user).plus("\n").plus(msg)
  }

  fun atAndCTRL(user: User, msg: String): MessageChain {
    return At(user).plus("\n").plus(msg)
  }

  fun atMessageAndCTRL(user: User, inline: String, msg: String): MessageChain {
    return At(user).plus("$inline\n").plus(msg)
  }

  fun recall(target: MessageReceipt<Contact>, delay: Long = 1000 * 10) {
    target.recallIn(delay)
  }

  fun deserializeMiraiCodeAndAddString(source: String, extra: String, contact: Contact? = null): MessageChain = deserializeMiraiCodeAndBuild(source, contact) {
    it.add(extra)
    it.build()
  }

  fun deserializeMiraiCodeAndBuild(source: String, contact: Contact? = null, block: (builder: MessageChainBuilder) -> MessageChain): MessageChain {
    val builder = MessageChainBuilder()
    builder.add(MiraiCode.deserializeMiraiCode(source, contact))
    return block(builder)
  }

}
