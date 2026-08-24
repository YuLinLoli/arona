/**
 * 文件说明：本文件属于 战斗模拟、角色属性和地形规则。
 * 具体职责：围绕 FightManager 提供对应的实现、数据结构或测试。
 */
package net.diyigemt.arona.fighting

import kotlin.math.floor

// 中文说明：定义 FightManager 类型，用于封装本模块的数据或处理行为。
class FightManager(
  private val friends: MutableList<net.diyigemt.arona.fighting.Character>,
  private val enemies: MutableList<net.diyigemt.arona.fighting.Character>,
  private var gameTick: Int = 300
) {
  init {
    friends.forEach {
      it.manager = this
    }
    enemies.forEach {
      it.manager = this
    }
  }
  fun start(): Unit {
    while (friends.isNotEmpty() && enemies.isNotEmpty() && gameTick > 0) {
      friends.forEach {
        it.tick(enemies, friends)
      }
      enemies.forEach {
        it.tick(friends, enemies)
      }
      checkDeath()
      gameTick--
    }
    if (gameTick < 0) {
      println("time out")
    }
    if (friends.isNotEmpty()) {
      println("you win")
    } else {
      println("you lost")
    }
  }
  private fun checkDeath() {
    val death: MutableList<net.diyigemt.arona.fighting.Character> = mutableListOf()
    friends.forEach {
      if (it.health <= 0) {
        logEvent("${it.name} death")
        death.add(it)
      }
    }
    friends.removeAll(death)
    death.clear()
    enemies.forEach {
      if (it.health <= 0) {
        logEvent("${it.name} death")
        death.add(it)
      }
    }
    enemies.removeAll(death)
  }

  fun logEvent(msg: String) {
    println("${calcTime()} $msg")
  }

  fun calcTime(): String {
    val minutes = floor((gameTick / 60).toDouble()).toInt()
    val second = gameTick - minutes * 60
    return "${if (minutes > 10) minutes else "0".plus(minutes)}:${if (second > 10) second else "0".plus(second)}"
  }
}
