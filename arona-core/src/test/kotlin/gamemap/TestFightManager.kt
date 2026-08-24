/**
 * 文件说明：本文件属于 测试代码，用于验证相关模块的行为和边界情况。
 * 具体职责：围绕 TestFightManager 提供对应的实现、数据结构或测试。
 */
package org.example.mirai.plugin.gamemap

import net.diyigemt.arona.fighting.*
import org.junit.jupiter.api.Test

// 中文说明：定义 TestFightManager 类型，用于封装本模块的数据或处理行为。
class TestFightManager {
  @Test
  fun testBase(): Unit {
    val weaponA: Weapon = Weapon(
      1, 1,
      15, 3,
      0, 50,
      3, 0,
      5
    )
    val weaponB: Weapon = Weapon(
      1, 1,
      5, 1,
      0, 50,
      5, 0,
      10
    )
    val weaponC: Weapon = Weapon(
      1, 1,
      5, 1,
      0, 50,
      5, 0,
      10
    )
    val siroko = Character(
      "siroko",
      10, 600, 10,
      10, 10, 10,
      10, 10, 10,
      10, 0, 10,
      10, 10,
      listOf<TerrainSatisfyEnum>(TerrainSatisfyEnum.B, TerrainSatisfyEnum.B, TerrainSatisfyEnum.B),
      weaponA, Position(10, 10)
    )
    val normalEnemyA = Character(
      "normalEnemyA",
      10, 300, 10,
      10, 10, 10,
      10, 10, 10,
      10, 0, 10,
      10, 10,
      listOf<TerrainSatisfyEnum>(TerrainSatisfyEnum.B, TerrainSatisfyEnum.B, TerrainSatisfyEnum.B),
      weaponB, Position(10, 10)
    )
    val normalEnemyB = Character(
      "normalEnemyB",
      10, 250, 10,
      10, 10, 10,
      10, 10, 10,
      10, 0, 10,
      10, 10,
      listOf<TerrainSatisfyEnum>(TerrainSatisfyEnum.B, TerrainSatisfyEnum.B, TerrainSatisfyEnum.B),
      weaponC, Position(10, 10)
    )
    val friends: MutableList<net.diyigemt.arona.fighting.Character> = mutableListOf(
      siroko
    )
    val enemies: MutableList<net.diyigemt.arona.fighting.Character> = mutableListOf(
      normalEnemyA, normalEnemyB
    )
    val manage: FightManager = FightManager(friends, enemies)
    manage.start()
  }
}
