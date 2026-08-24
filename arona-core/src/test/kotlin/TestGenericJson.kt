/**
 * 文件说明：本文件属于 测试代码，用于验证相关模块的行为和边界情况。
 * 具体职责：围绕 TestGenericJson 提供对应的实现、数据结构或测试。
 */
package org.example.mirai.plugin

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import org.junit.jupiter.api.Test
import kotlin.reflect.full.createType

/**
 * @description
 * @author diyigemt
 * @date 2022/9/27 12:11
 */
// 中文说明：定义 TestGenericJson 类型，用于封装本模块的数据或处理行为。
class TestGenericJson {

  @Serializable
  data class TestData(
    val name: String
  )

  @Test
  fun testJsonParse() {
    val se = serializer(TestData::class.createType())
    val td = Json.decodeFromString(se, "{\"name\":\"hahaha\"}")
    println((td as TestData).name)
  }

}
