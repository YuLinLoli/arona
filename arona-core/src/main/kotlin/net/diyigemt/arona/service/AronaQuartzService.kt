/**
 * 文件说明：本文件属于 Arona 核心业务服务与服务生命周期。
 * 具体职责：围绕 AronaQuartzService 提供对应的实现、数据结构或测试。
 */
package net.diyigemt.arona.service

import net.diyigemt.arona.quartz.QuartzProvider
import org.quartz.JobKey

// 中文说明：定义 AronaQuartzService 接口，约定相关实现需要提供的能力。
interface AronaQuartzService: AronaService {
  var jobKey: JobKey?

  override fun disableService() {
    if (jobKey != null) {
      QuartzProvider.deleteTask(jobKey!!)
    }
  }
}
