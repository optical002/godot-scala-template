import mill._
import mill.scalalib._
import mill.api.PathRef
import coursier.maven.MavenRepository

trait GodotBuildModule extends ScalaModule {
  override def millSourcePath = os.pwd
  override def sources = T.sources(millSourcePath / "src" / "main" / "scala")
  override def resources = T.sources(millSourcePath / "src" / "main" / "resources")
  override def repositoriesTask = T.task {
    super.repositoriesTask() ++ Seq(MavenRepository(s"file://${System.getProperty("user.home")}/.m2/repository"))
  }
  
  def godotKotlinVersion = "0.13.0-4.4.1-afac9a3-SNAPSHOT"
  def kotlinVersion = "1.9.0"
  def kotlinCompilerVersion = "2.1.10"
  def classGraphVersion = "4.8.165"
  
  override def compileIvyDeps = T {
    super.compileIvyDeps() ++ Agg(
      ivy"org.jetbrains.kotlin:kotlin-stdlib:$kotlinVersion",
      ivy"com.utopia-rise:godot-library-debug:$godotKotlinVersion",
      ivy"com.utopia-rise:godot-api-library-debug:$godotKotlinVersion",
      ivy"com.utopia-rise:godot-core-library-debug:$godotKotlinVersion",
      ivy"com.utopia-rise:godot-extension-library-debug:$godotKotlinVersion",
      ivy"com.utopia-rise:godot-internal-library-debug:$godotKotlinVersion",
      ivy"com.utopia-rise:common:$godotKotlinVersion",
      ivy"com.utopia-rise:godot-build-props:$godotKotlinVersion"
    )
  }
  
  def generateClassGraphEntry: T[PathRef] = T {
    val generatedDir = T.dest / "generated" / "classgraph"
    os.makeDir.all(generatedDir)
    val compiledClasses = compile().classes.path
    val classPath = (compileClasspath() ++ Agg(compile().classes)).map(_.path).toSet
    val processorDeps = resolveDeps(T.task {
      Agg(
        ivy"com.utopia-rise:godot-class-graph-symbol-processor:$godotKotlinVersion",
        ivy"io.github.classgraph:classgraph:$classGraphVersion",
        ivy"com.utopia-rise:godot-entry-generator:$godotKotlinVersion",
        ivy"com.squareup:kotlinpoet:1.14.2"
      ).map(bindDependency())
    })().map(_.path)
    val projectName = millSourcePath.last
    val regDir = millSourcePath / "gdj"
    os.makeDir.all(regDir)
    println(s"[ClassGraph] Scanning compiled classes...")
    val wrapperCode = s"""
import godot.annotation.processor.classgraph._
import java.io.File
import scala.jdk.CollectionConverters._
object ClassGraphRunner {
  def main(args: Array[String]): Unit = {
    val projectBaseDir = new File("${millSourcePath.toString.replace("\\", "\\\\")}")
    val genDir = new File("${generatedDir.toString.replace("\\", "\\\\")}")
    val settings = new Settings(null, false, "$projectName", projectBaseDir, "gdj", true, true, genDir)
    val logger = org.slf4j.LoggerFactory.getLogger("ClassGraphProcessor")
    val runtimeClassPath: java.util.Set[File] = ${classPath.map(p => "\"" + p.toString.replace("\\", "\\\\") + "\"").mkString("Set(", ", ", ")")}.map(new File(_)).asJava
    ProcessKt.generateEntryUsingClassGraph(settings, logger, runtimeClassPath)
    println("[ClassGraph] Entry generation complete!")
  }
}
"""
    val wrapperFile = T.dest / "ClassGraphRunner.scala"
    os.write.over(wrapperFile, wrapperCode)
    try {
      val fullClasspath = (processorDeps ++ classPath).mkString(":")
      val scalaCompilerCp = scalaCompilerClasspath().map(_.path).mkString(":")
      os.proc("java", "-cp", scalaCompilerCp, "dotty.tools.dotc.Main", "-classpath", fullClasspath, "-d", T.dest.toString, wrapperFile.toString).call()
      os.proc("java", "-cp", s"${T.dest}:$fullClasspath:$scalaCompilerCp", "ClassGraphRunner").call()
      val generatedGdjDir = generatedDir / "main" / "resources" / "entryFiles" / "gdj"
      if (os.exists(generatedGdjDir)) os.copy.over(generatedGdjDir, regDir, createFolders = true)
    } catch { case e: Exception => println(s"[ClassGraph] Warning: ${e.getMessage}") }
    PathRef(generatedDir)
  }
  
  def bootstrapDeps = T {
    Agg(
      ivy"org.jetbrains.kotlin:kotlin-stdlib:$kotlinVersion",
      ivy"com.utopia-rise:godot-library-debug:$godotKotlinVersion",
      ivy"com.utopia-rise:godot-api-library-debug:$godotKotlinVersion",
      ivy"com.utopia-rise:godot-core-library-debug:$godotKotlinVersion",
      ivy"com.utopia-rise:godot-extension-library-debug:$godotKotlinVersion",
      ivy"com.utopia-rise:godot-internal-library-debug:$godotKotlinVersion",
      ivy"com.utopia-rise:common:$godotKotlinVersion",
      ivy"com.utopia-rise:godot-build-props:$godotKotlinVersion"
    )
  }
  
  def packageBootstrapJar: T[PathRef] = T {
    val dest = T.dest / "godot-bootstrap.jar"
    println(s"[Godot] Packaging bootstrap JAR...")
    val jars = resolveDeps(T.task { bootstrapDeps().map(bindDependency()) })()
    val jarDir = T.dest / "bootstrap-contents"
    os.makeDir.all(jarDir)
    jars.foreach { jar =>
      if (jar.path.ext == "jar") os.proc("jar", "xf", jar.path.toString).call(cwd = jarDir)
    }
    val metaInf = jarDir / "META-INF"
    if (os.exists(metaInf)) {
      os.list(metaInf).filter(f => f.last.endsWith(".SF") || f.last.endsWith(".DSA") || f.last.endsWith(".RSA")).foreach(os.remove)
    }
    os.proc("jar", "cf", dest.toString, "-C", jarDir.toString, ".").call()
    PathRef(dest)
  }
  
  def packageMainJar: T[PathRef] = T {
    val compiledClassesRef = compile().classes
    val compiledClasses = compiledClassesRef.path
    val dest = T.dest / "main.jar"
    println(s"[Godot] Packaging main JAR...")
    val jarDir = T.dest / "jar-contents"
    os.makeDir.all(jarDir)
    if (os.exists(compiledClasses) && os.isDir(compiledClasses)) {
      os.walk(compiledClasses).foreach { file =>
        if (!os.isDir(file)) {
          val rel = file.relativeTo(compiledClasses)
          val target = jarDir / rel
          os.makeDir.all(target / os.up)
          os.copy.over(file, target, createFolders = true)
        }
      }
    }
    val entryClasses = compileGeneratedEntry().path
    if (os.exists(entryClasses) && os.list(entryClasses).nonEmpty) {
      os.walk(entryClasses).foreach { file =>
        if (!os.isDir(file)) {
          val rel = file.relativeTo(entryClasses)
          val target = jarDir / rel
          os.makeDir.all(target / os.up)
          os.copy.over(file, target, createFolders = true)
        }
      }
    }
    val resourcesDir = millSourcePath / "src" / "main" / "resources"
    if (os.exists(resourcesDir) && os.isDir(resourcesDir)) {
      os.walk(resourcesDir).foreach { file =>
        if (!os.isDir(file)) {
          val rel = file.relativeTo(resourcesDir)
          val target = jarDir / rel
          os.makeDir.all(target / os.up)
          os.copy.over(file, target, createFolders = true)
        }
      }
    }
    val excludePatterns = Seq("kotlin-stdlib", "godot-library", "godot-api-library", "godot-core-library", 
                              "godot-extension-library", "godot-internal-library", "godot-build-props", "common")
    runClasspath().map(_.path).filter { p =>
      os.exists(p) && p.ext == "jar" && !excludePatterns.exists(pattern => p.toString.contains(pattern))
    }.foreach { jar =>
      os.proc("jar", "xf", jar.toString).call(cwd = jarDir)
    }
    val metaInf = jarDir / "META-INF"
    if (os.exists(metaInf)) {
      os.list(metaInf).filter(f => f.last.endsWith(".SF") || f.last.endsWith(".DSA") || f.last.endsWith(".RSA")).foreach(os.remove)
    }
    os.proc("jar", "cf", dest.toString, "-C", jarDir.toString, ".").call()
    PathRef(dest)
  }
  
  def compileGeneratedEntry: T[PathRef] = T {
    val generatedDir = generateClassGraphEntry().path
    val entryClassesDir = T.dest / "entry-classes"
    os.makeDir.all(entryClassesDir)
    val kotlinFiles = if (os.exists(generatedDir / "main" / "kotlin")) {
      os.walk(generatedDir / "main" / "kotlin").filter(f => f.ext == "kt" && os.isFile(f)).toSeq
    } else {
      Seq.empty
    }
    if (kotlinFiles.nonEmpty) {
      val kotlinDeps = resolveDeps(T.task {
        Agg(
          ivy"org.jetbrains.kotlin:kotlin-compiler:$kotlinCompilerVersion",
          ivy"org.jetbrains.kotlin:kotlin-stdlib:$kotlinCompilerVersion"
        ).map(bindDependency())
      })()
      val cp = ((compileClasspath() ++ Agg(compile().classes)).map(_.path) ++ kotlinDeps.map(_.path)).mkString(":")
      val kotlincCp = kotlinDeps.map(_.path).mkString(":")
      try {
        val args = Seq("java", "-cp", kotlincCp, "org.jetbrains.kotlin.cli.jvm.K2JVMCompiler",
          "-d", entryClassesDir.toString, "-cp", cp, "-jvm-target", "17") ++ kotlinFiles.map(_.toString)
        os.proc(args).call()
      } catch { case e: Exception => println(s"[Entry] Warning: ${e.getMessage}") }
    }
    PathRef(entryClassesDir)
  }
  
  def embedJre: T[PathRef] = T {
    val jvmDir = millSourcePath / "jvm"
    val osName = System.getProperty("os.name").toLowerCase()
    val osArch = System.getProperty("os.arch").toLowerCase()
    val (platform, arch, ext, jreDirName) = if (osName.contains("linux")) {
      if (osArch.contains("aarch64") || osArch.contains("arm")) ("linux", "aarch64", "tar.gz", "jre-arm64-linux")
      else ("linux", "x64", "tar.gz", "jre-amd64-linux")
    } else if (osName.contains("mac")) {
      if (osArch.contains("aarch64") || osArch.contains("arm")) ("macos", "aarch64", "tar.gz", "jre-arm64-macos")
      else ("macos", "x64", "tar.gz", "jre-amd64-macos")
    } else if (osName.contains("win")) {
      ("windows", "x64", "zip", "jre-amd64-windows")
    } else {
      throw new Exception(s"Unsupported OS: $osName")
    }
    val jreDir = jvmDir / jreDirName
    if (os.exists(jreDir) && os.isDir(jreDir)) {
      PathRef(jreDir)
    } else {
      os.makeDir.all(jvmDir)
      val downloadDir = T.dest / "download"
      os.makeDir.all(downloadDir)
      val jdkVersion = "17.0.13+11"
      val jdkVersionEncoded = jdkVersion.replace("+", "%2B")
      val url = s"https://api.adoptium.net/v3/binary/version/jdk-$jdkVersionEncoded/$platform/$arch/jre/hotspot/normal/eclipse?project=jdk"
      val archiveFile = downloadDir / s"openjdk-17-jre.$ext"
      println(s"[JRE] Downloading OpenJDK 17...")
      os.proc("curl", "-L", "-o", archiveFile.toString, url).call(stdout = os.Inherit, stderr = os.Inherit)
      val extractDir = downloadDir / "extracted"
      os.makeDir.all(extractDir)
      if (ext == "tar.gz") {
        os.proc("tar", "xzf", archiveFile.toString, "-C", extractDir.toString).call(stdout = os.Inherit, stderr = os.Inherit)
      } else {
        os.proc("unzip", "-q", archiveFile.toString, "-d", extractDir.toString).call(stdout = os.Inherit, stderr = os.Inherit)
      }
      val extractedJre = os.list(extractDir).find(p => os.isDir(p) && (p.last.contains("jdk") || p.last.contains("jre")))
        .getOrElse(throw new Exception("Could not find extracted JRE directory"))
      os.move(extractedJre, jreDir)
      PathRef(jreDir)
    }
  }
  
  def generateGdIgnoreFiles: T[Unit] = T {
    Seq(millSourcePath / "out", millSourcePath / ".mill").filter(os.exists).foreach { dir =>
      val gdignore = dir / ".gdignore"
      if (!os.exists(gdignore)) os.write(gdignore, "")
    }
  }
  
  def godotBuild: T[PathRef] = T {
    println("[Godot] Building...")
    val _ = generateClassGraphEntry()
    val _ = compileGeneratedEntry()
    val bootstrapJar = packageBootstrapJar()
    val mainJar = packageMainJar()
    val projectJvmDir = millSourcePath / "jvm"
    if (os.exists(projectJvmDir)) {
      Seq("godot-bootstrap.jar", "main.jar", s"${millSourcePath.last}.jar").foreach { jar =>
        val jarPath = projectJvmDir / jar
        if (os.exists(jarPath)) os.remove(jarPath)
      }
    } else {
      os.makeDir.all(projectJvmDir)
    }
    os.copy(bootstrapJar.path, projectJvmDir / "godot-bootstrap.jar")
    os.copy(mainJar.path, projectJvmDir / "main.jar")
    os.copy(mainJar.path, projectJvmDir / s"${millSourcePath.last}.jar")
    embedJre()
    generateGdIgnoreFiles()
    println("[Godot] Build complete!")
    PathRef(projectJvmDir)
  }
  
  def build = T { godotBuild() }
  
  def cleanJvmDirectory: T[Unit] = T {
    val jvmDir = millSourcePath / "jvm"
    if (os.exists(jvmDir)) {
      os.remove.all(jvmDir)
      println("[JVM] Cleaned jvm directory")
    }
  }
  
  def cleanServer: T[Unit] = T {
    val millDir = millSourcePath / ".mill"
    if (os.exists(millDir)) {
      os.list(millDir).filter(p => p.last.contains("mill-worker") || p.last.contains("mill-server")).foreach { dir =>
        try { os.remove.all(dir) } catch { case _: Exception => }
      }
      println("[Mill] Cleaned server state - restart will use fresh server")
    } else {
      println("[Mill] No server state to clean")
    }
  }
}
