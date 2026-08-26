/**
 * 文件说明：本文件属于 跨模块复用的接口和生命周期约定。
 * 具体职责：围绕 BaseFunctionProvider 提供对应的实现、数据结构或测试。
 */
package net.diyigemt.arona.interfaces

import kotlinx.coroutines.*
import net.diyigemt.arona.Arona
import net.diyigemt.arona.runtime.RuntimeLog
import net.diyigemt.arona.runtime.RuntimeServices
import kotlin.coroutines.CoroutineContext

// 中文说明：定义 BaseFunctionProvider 类型，用于封装本模块的数据或处理行为。
abstract class BaseFunctionProvider(ctx: CoroutineContext? = null): CoroutineScope {

  abstract val tag: String
  final override val coroutineContext: CoroutineContext
    get() = SupervisorJob(if (RuntimeServices.isStandalone) null else Arona.coroutineContext.job)

  init {
    if(ctx != null) {
      coroutineContext.plus(ctx)
    }
  }

  protected abstract suspend fun main()

  @Suppress("NOTHING_TO_INLINE")
  inline fun warning(text: String) {
    if (RuntimeServices.isStandalone) RuntimeLog.warning("$tag: $text") else Arona.warning { "$tag: $text" }
  }
  @Suppress("NOTHING_TO_INLINE")
  inline fun error(text: String) {
    if (RuntimeServices.isStandalone) RuntimeLog.error("$tag: $text") else Arona.error { "$tag: $text" }
  }
  @Suppress("NOTHING_TO_INLINE")
  inline fun info(text: String) {
    if (RuntimeServices.isStandalone) RuntimeLog.info("$tag: $text") else Arona.info { "$tag: $text" }
  }
  @Suppress("NOTHING_TO_INLINE")
  inline fun verbose(text: String) {
    if (RuntimeServices.isStandalone) RuntimeLog.verbose("$tag: $text") else Arona.verbose { "$tag: $text" }
  }

  fun start() : Job = this.launch(context = this.coroutineContext) { main() }

  open fun disable() {}

}
