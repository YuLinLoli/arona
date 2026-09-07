plugins {
  val kotlinVersion = "1.7.10"
  val miraiVersion = "2.16.0"
  kotlin("jvm") version kotlinVersion
  kotlin("plugin.serialization") version kotlinVersion
  id("com.github.gmazzo.buildconfig") version "4.1.1"
  id("net.mamoe.mirai-console") version miraiVersion
}

group = "net.diyigemt"
version = "1.2.8-onebotAndMirai"
val exposedVersion = "0.38.2"
val sqliteVersion = "3.36.0.3"
val quartzVersion = "2.3.2"
val okhttpVersion = "4.10.0"

repositories {
  mavenCentral()
  maven("https://jitpack.io")
  maven("https://maven.aliyun.com/repository/public") // 阿里云国内代理仓库
  maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
  //Overflow依赖仓库
  maven("https://s01.oss.sonatype.org/content/repositories/snapshots")
}
//Overflow测试需移除mirai-core
mirai {
  noTestCore = true
  setupConsoleTestRuntime {
    // 移除 mirai-core 依赖
    classpath = classpath.filter {
      !it.nameWithoutExtension.startsWith("mirai-core-jvm")
    }
  }
}
buildConfig {
  className("BuildConfig")
  packageName("net.diyigemt.arona.cg")
  buildConfigField("String", "version", "\"${version}\"")
  buildConfigField("String", "name", "\"blue-archive-arona\"")
  buildConfigField("String", "id", "\"net.diyigemt.arona\"")
}
dependencies {
//    implementation("com.github.kittinunf.fuel:fuel:2.3.1")
  implementation("org.jsoup:jsoup:1.15.1")
  testImplementation("org.junit.jupiter:junit-jupiter:5.6.0")
  testImplementation("io.kotest:kotest-runner-junit5:5.3.0")
  testImplementation("io.kotest:kotest-assertions-core:5.3.0")
  implementation("org.jetbrains.exposed:exposed-core:$exposedVersion")
  implementation("org.jetbrains.exposed:exposed-dao:$exposedVersion")
  implementation("org.jetbrains.exposed:exposed-jdbc:$exposedVersion")
  implementation("org.xerial:sqlite-jdbc:$sqliteVersion")
  implementation("org.quartz-scheduler:quartz:$quartzVersion")
  // https://mvnrepository.com/artifact/com.squareup.okhttp3/okhttp
  implementation("com.squareup.okhttp3:okhttp:$okhttpVersion")
  implementation("org.apache.logging.log4j:log4j-core:2.18.0")
  implementation("org.slf4j:slf4j-api:1.7.36")
  implementation("com.google.code.gson:gson:2.9.0")
  implementation("org.jetbrains.kotlinx:kotlinx-serialization-json-jvm:1.5.0")
  implementation("org.java-websocket:Java-WebSocket:1.5.6")
  // https://mvnrepository.com/artifact/me.xdrop/fuzzywuzzy
  implementation("me.xdrop:fuzzywuzzy:1.4.0")
  // https://mvnrepository.com/artifact/com.github.taptap/pinyin-plus
  implementation("com.github.taptap:pinyin-plus:1.0")
  implementation("net.mamoe.yamlkt:yamlkt-jvm:0.10.2")
  //Overflow运行时依赖
  testConsoleRuntime("top.mrxiaom.mirai:overflow-core:1.0.4.589-05fbc9f-SNAPSHOT")
}

tasks.test {
  useJUnitPlatform()
}


tasks.register<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("standaloneFatJar") {
  archiveFileName.set("arona-standalone-${project.version}-all.jar")
  duplicatesStrategy = DuplicatesStrategy.EXCLUDE
  from(sourceSets.main.get().output)
  configurations = listOf(project.configurations.runtimeClasspath.get())
  manifest { attributes["Main-Class"] = "net.diyigemt.arona.standalone.AronaStandalone" }
}

// 一个 jar 既可作为 Mirai Console 插件加载，也可直接 java -jar 独立运行，
// 因此禁用 Mirai 官方 buildPlugin，不再产出 .mirai2.jar。
// buildPlugin 由 mirai-console 插件在 afterEvaluate 中注册，因此这里也在 afterEvaluate 中配置。
project.afterEvaluate {
  tasks.named("buildPlugin") { enabled = false }
  tasks.register<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("buildOnebotAndMiraiPlugin") {
    group = "mirai"
    archiveFileName.set("arona-${project.version}-all.jar")
    destinationDirectory.set(layout.buildDirectory.dir("mirai"))
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    // 编译产物与资源（含 META-INF/services 插件注册）
    from(sourceSets.main.get().output)
    // 全部运行时依赖，保证 java -jar 独立运行不缺类
    configurations = listOf(project.configurations.runtimeClasspath.get())
    exclude { file -> file.name.endsWith(".SF", ignoreCase = true) }
    manifest { attributes["Main-Class"] = "net.diyigemt.arona.standalone.AronaStandalone" }
  }
}
