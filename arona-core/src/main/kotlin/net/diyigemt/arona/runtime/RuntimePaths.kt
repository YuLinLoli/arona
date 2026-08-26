package net.diyigemt.arona.runtime

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

object RuntimePaths {
  fun standaloneRoot(): Path {
    val location = runCatching {
      Paths.get(RuntimePaths::class.java.protectionDomain.codeSource.location.toURI())
    }.getOrElse { Paths.get(".").toAbsolutePath() }
    val base = if (Files.isDirectory(location)) location else location.parent
    return base.resolve("arona-standalone").toAbsolutePath().normalize()
  }

  fun prepareStandaloneRoot(): Path = standaloneRoot().also {
    Files.createDirectories(it.resolve("images"))
    Files.createDirectories(it.resolve("data"))
  }
}
