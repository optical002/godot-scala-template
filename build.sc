import mill._
import mill.scalalib._
import mill.api.PathRef
import coursier.maven.MavenRepository

/**
 * Godot Scala Template Project
 * 
 * This project uses Mill to build a Godot game with Scala 3.
 * Uses godot-class-graph-symbol-processor for automatic registration (works with any JVM language).
 */
object godot extends ScalaModule {
  
  // ============================================================================
  // Basic Configuration
  // ============================================================================
  
  def scalaVersion = "3.6.3"
  
  // Fix source path (build.sc is in template dir, so we need to use parent as source root)
  override def millSourcePath = os.pwd
  
  // Override sources to use standard Maven layout (src/main/scala instead of src/)
  override def sources = T.sources(millSourcePath / "src" / "main" / "scala")
  override def resources = T.sources(millSourcePath / "src" / "main" / "resources")
  
  // Add Maven local repository for Godot dependencies
  override def repositoriesTask = T.task {
    super.repositoriesTask() ++ Seq(
      MavenRepository(s"file://${System.getProperty("user.home")}/.m2/repository")
    )
  }
  
  // ============================================================================
  // Dependencies
  // ============================================================================
  
  override def ivyDeps = T {
    Agg(
      ivy"org.typelevel::cats-core:2.12.0",
      ivy"io.github.classgraph:classgraph:4.8.165"  // For runtime class scanning in ScalaEntry
    )
  }
  
  // Godot Kotlin JVM compile-only dependencies
  override def compileIvyDeps = T {
    val version = "0.13.0-4.4.1-afac9a3-SNAPSHOT"
    super.compileIvyDeps() ++ Agg(
      ivy"org.jetbrains.kotlin:kotlin-stdlib:1.9.0",
      ivy"com.utopia-rise:godot-library-debug:$version",
      ivy"com.utopia-rise:godot-api-library-debug:$version",
      ivy"com.utopia-rise:godot-core-library-debug:$version",
      ivy"com.utopia-rise:godot-extension-library-debug:$version",
      ivy"com.utopia-rise:godot-internal-library-debug:$version",
      ivy"com.utopia-rise:common:$version",
      ivy"com.utopia-rise:godot-build-props:$version"
    )
  }
  
  // ============================================================================
  // Godot Build Tasks
  // ============================================================================
  
  /**
   * Runs ClassGraph symbol processor to generate Entry files from compiled bytecode.
   * This scans compiled Scala classes for @RegisterClass annotations and generates
   * Kotlin Entry files + .gdj registration files.
   */
  def generateClassGraphEntry: T[PathRef] = T {
    val generatedDir = T.dest / "generated" / "classgraph"
    os.makeDir.all(generatedDir)
    
    // Get compiled classes + classpath
    val compiledClasses = compile().classes.path
    val classPath = (compileClasspath() ++ Agg(compile().classes)).map(_.path).toSet
    
    // Get classgraph processor
    val processorDeps = resolveDeps(T.task {
      Agg(
        ivy"com.utopia-rise:godot-class-graph-symbol-processor:0.13.0-4.4.1-afac9a3-SNAPSHOT",
        ivy"io.github.classgraph:classgraph:4.8.165",
        ivy"com.utopia-rise:godot-entry-generator:0.13.0-4.4.1-afac9a3-SNAPSHOT",
        ivy"com.squareup:kotlinpoet:1.14.2"
      ).map(bindDependency())
    })().map(_.path)
    
    val projectName = "godot-scala-template"
    val regDir = millSourcePath / "gdj"
    os.makeDir.all(regDir)
    
    println(s"[ClassGraph] Scanning compiled classes for @RegisterClass annotations...")
    
    // Create a simple Scala wrapper to call the Kotlin function
    val wrapperCode = s"""
import godot.annotation.processor.classgraph._
import java.io.File
import scala.jdk.CollectionConverters._

object ClassGraphRunner {
  def main(args: Array[String]): Unit = {
    val projectBaseDir = new File("${millSourcePath.toString.replace("\\", "\\\\")}")
    val genDir = new File("${generatedDir.toString.replace("\\", "\\\\")}")
    
    // Settings constructor: (classPrefix, isFqNameRegistrationEnabled, projectName, projectBaseDir, 
    //                        registrationBaseDirPathRelativeToProjectDir, isRegistrationFileHierarchyEnabled, 
    //                        isRegistrationFileGenerationEnabled, generatedSourceRootDir)
    val settings = new Settings(
      null,              // classPrefix
      false,             // isFqNameRegistrationEnabled
      "$projectName",    // projectName
      projectBaseDir,    // projectBaseDir
      "gdj",             // registrationBaseDirPathRelativeToProjectDir
      true,              // isRegistrationFileHierarchyEnabled
      true,              // isRegistrationFileGenerationEnabled
      genDir             // generatedSourceRootDir
    )
    
    val logger = org.slf4j.LoggerFactory.getLogger("ClassGraphProcessor")
    val runtimeClassPath: java.util.Set[File] = ${classPath.map(p => "\"" + p.toString.replace("\\", "\\\\") + "\"").mkString("Set(", ", ", ")")}
      .map(new File(_))
      .asJava
    
    ProcessKt.generateEntryUsingClassGraph(settings, logger, runtimeClassPath)
    
    println("[ClassGraph] Entry generation complete!")
  }
}
"""
    
    val wrapperFile = T.dest / "ClassGraphRunner.scala"
    os.write.over(wrapperFile, wrapperCode)
    
    // Compile and run the wrapper
    try {
      val fullClasspath = (processorDeps ++ classPath).mkString(":")
      
      // Compile wrapper
      os.proc(
        "scalac",
        "-cp", fullClasspath,
        "-d", T.dest.toString,
        wrapperFile.toString
      ).call()
      
      // Run ClassGraph processor
      os.proc(
        "scala",
        "-cp", s"${T.dest}:$fullClasspath",
        "ClassGraphRunner"
      ).call()
      
      // Copy generated .gdj files to project gdj directory
      val generatedGdjDir = generatedDir / "main" / "resources" / "entryFiles" / "gdj"
      if (os.exists(generatedGdjDir)) {
        os.copy.over(generatedGdjDir, regDir, createFolders = true)
        println(s"[ClassGraph] Copied .gdj registration files to: $regDir")
      }
      
      println(s"[ClassGraph] Generated Entry files in: $generatedDir")
    } catch {
      case e: Exception =>
        println(s"[ClassGraph] Warning: Entry generation failed: ${e.getMessage}")
        println(s"[ClassGraph] Falling back to manual ScalaEntry")
    }
    
    PathRef(generatedDir)
  }
  
  /**
   * Bootstrap dependencies for Godot runtime.
   */
  def bootstrapDeps = T {
    val version = "0.13.0-4.4.1-afac9a3-SNAPSHOT"
    Agg(
      ivy"org.jetbrains.kotlin:kotlin-stdlib:1.9.0",
      ivy"com.utopia-rise:godot-library-debug:$version",
      ivy"com.utopia-rise:godot-api-library-debug:$version",
      ivy"com.utopia-rise:godot-core-library-debug:$version",
      ivy"com.utopia-rise:godot-extension-library-debug:$version",
      ivy"com.utopia-rise:godot-internal-library-debug:$version",
      ivy"com.utopia-rise:common:$version",
      ivy"com.utopia-rise:godot-build-props:$version"
    )
  }
  
  /**
   * Packages the bootstrap JAR with Godot runtime dependencies.
   */
  def packageBootstrapJar: T[PathRef] = T {
    val dest = T.dest / "godot-bootstrap.jar"
    
    println(s"[Godot] Packaging bootstrap JAR...")
    
    // Resolve dependencies
    val jars = resolveDeps(T.task { bootstrapDeps().map(bindDependency()) })()
    val jarDir = T.dest / "bootstrap-contents"
    os.makeDir.all(jarDir)
    
    jars.foreach { jar =>
      if (jar.path.ext == "jar") {
        println(s"[Godot]   Extracting: ${jar.path.last}")
        os.proc("jar", "xf", jar.path.toString).call(cwd = jarDir)
      }
    }
    
    // Remove signature files
    val metaInf = jarDir / "META-INF"
    if (os.exists(metaInf)) {
      os.list(metaInf).filter(f => f.last.endsWith(".SF") || f.last.endsWith(".DSA") || f.last.endsWith(".RSA")).foreach(os.remove)
    }
    
    // Create JAR
    os.proc("jar", "cf", dest.toString, "-C", jarDir.toString, ".").call()
    println(s"[Godot] Bootstrap JAR created: $dest")
    PathRef(dest)
  }
  
  /**
   * Packages the main JAR with game code and dependencies.
   */
  def packageMainJar: T[PathRef] = T {
    // Explicitly depend on compilation to ensure rebuild on source changes
    val compiledClassesRef = compile().classes
    val compiledClasses = compiledClassesRef.path
    
    val dest = T.dest / "main.jar"
    println(s"[Godot] Packaging main JAR...")
    
    // Prepare JAR directory
    val jarDir = T.dest / "jar-contents"
    os.makeDir.all(jarDir)
    // Copy compiled classes (including ScalaEntry fallback)
    if (os.exists(compiledClasses) && os.isDir(compiledClasses)) {
      println(s"[Godot]   Adding compiled classes...")
      os.walk(compiledClasses).foreach { file =>
        if (!os.isDir(file)) {
          val rel = file.relativeTo(compiledClasses)
          val target = jarDir / rel
          os.makeDir.all(target / os.up)
          os.copy.over(file, target, createFolders = true)
        }
      }
    }
    
    // Copy compiled Entry classes (generated from ClassGraph)
    val entryClasses = compileGeneratedEntry().path
    if (os.exists(entryClasses) && os.list(entryClasses).nonEmpty) {
      println(s"[Godot]   Adding generated Entry classes...")
      os.walk(entryClasses).foreach { file =>
        if (!os.isDir(file)) {
          val rel = file.relativeTo(entryClasses)
          val target = jarDir / rel
          os.makeDir.all(target / os.up)
          os.copy.over(file, target, createFolders = true)
        }
      }
    }
    
    // Copy resources (including META-INF/services pointing to ScalaEntry)
    val resourcesDir = millSourcePath / "src" / "main" / "resources"
    if (os.exists(resourcesDir) && os.isDir(resourcesDir)) {
      println(s"[Godot]   Adding resources...")
      os.walk(resourcesDir).foreach { file =>
        if (!os.isDir(file)) {
          val rel = file.relativeTo(resourcesDir)
          val target = jarDir / rel
          os.makeDir.all(target / os.up)
          os.copy.over(file, target, createFolders = true)
        }
      }
    }
    
    // Add runtime dependencies (excluding bootstrap deps)
    val excludePatterns = Seq("kotlin-stdlib", "godot-library", "godot-api-library", "godot-core-library", "godot-extension-library", "godot-internal-library", "godot-build-props", "common")
    runClasspath().map(_.path).filter { p =>
      os.exists(p) && p.ext == "jar" && !excludePatterns.exists(pattern => p.toString.contains(pattern))
    }.foreach { jar =>
      println(s"[Godot]   Extracting: ${jar.last}")
      os.proc("jar", "xf", jar.toString).call(cwd = jarDir)
    }
    
    // Remove signature files
    val metaInf = jarDir / "META-INF"
    if (os.exists(metaInf)) {
      os.list(metaInf).filter(f => f.last.endsWith(".SF") || f.last.endsWith(".DSA") || f.last.endsWith(".RSA")).foreach(os.remove)
    }
    
    // Create JAR
    os.proc("jar", "cf", dest.toString, "-C", jarDir.toString, ".").call()
    println(s"[Godot] Main JAR created: $dest")
    PathRef(dest)
  }
  
  /**
   * Sets up the complete JVM directory with JARs, JRE, and .gdignore file.
   */
  def setupJvmDirectory: T[PathRef] = T {
    // Explicitly depend on JAR tasks to ensure rebuild chain
    val bootstrapJar = packageBootstrapJar()
    val mainJar = packageMainJar()
    
    // Use project root explicitly - os.pwd should give us the actual working directory
    val jvmDir = os.pwd / "jvm"
    
    println(s"[JVM] Setting up complete JVM directory at: $jvmDir")
    
    // Always recreate to ensure fresh files on rebuild
    if (os.exists(jvmDir)) {
      println(s"[JVM] Removing old JVM directory: $jvmDir")
      os.remove.all(jvmDir)
    }
    os.makeDir.all(jvmDir)
    println(s"[JVM] Created fresh JVM directory at: $jvmDir")
    
    // Copy JARs
    println("[JVM] Copying JARs...")
    os.copy.over(bootstrapJar.path, jvmDir / "godot-bootstrap.jar", createFolders = true)
    os.copy.over(mainJar.path, jvmDir / "main.jar", createFolders = true)
    os.copy.over(mainJar.path, jvmDir / "godot-scala-template.jar", createFolders = true)
    println("[JVM]   - godot-bootstrap.jar")
    println("[JVM]   - main.jar")
    println("[JVM]   - godot-scala-template.jar")
    
    // Embed JRE
    println("[JVM] Setting up JRE...")
    embedJre()
    
    // Create .gdignore file in jvm directory
    val gdignoreFile = jvmDir / ".gdignore"
    os.write(gdignoreFile, "")
    println(s"[JVM] Created .gdignore file in: $jvmDir")
    
    println(s"[JVM] JVM directory setup complete at: $jvmDir")
    println(s"[JVM] Contents: JARs, JRE, and .gdignore")
    PathRef(jvmDir)
  }
  
  /**
   * Copies JARs to the jvm/ directory for Godot.
   * @deprecated Use setupJvmDirectory instead
   */
  def copyJarsToProject: T[PathRef] = T {
    val jvmDir = millSourcePath / "jvm"
    os.makeDir.all(jvmDir)
    
    os.copy.over(packageBootstrapJar().path, jvmDir / "godot-bootstrap.jar")
    os.copy.over(packageMainJar().path, jvmDir / "main.jar")
    os.copy.over(packageMainJar().path, jvmDir / "godot-scala-template.jar")
    
    println(s"[Godot] JARs copied to: $jvmDir")
    PathRef(jvmDir)
  }
  
  /**
   * Generates .gdignore files for build directories.
   */
  def generateGdIgnoreFiles: T[Unit] = T {
    Seq(millSourcePath / "out", millSourcePath / ".mill").filter(os.exists).foreach { dir =>
      val gdignore = dir / ".gdignore"
      if (!os.exists(gdignore)) {
        os.write(gdignore, "")
        println(s"[Godot] Created .gdignore in: $dir")
      }
    }
  }
  
  /**
   * Checks if JRE is embedded in jvm directory, and if not, embeds OpenJDK 17.
   */
  def embedJre: T[PathRef] = T {
    val jvmDir = millSourcePath / "jvm"
    
    // Detect OS and architecture first to determine JRE directory name
    val osName = System.getProperty("os.name").toLowerCase()
    val osArch = System.getProperty("os.arch").toLowerCase()
    
    // Godot-Kotlin-JVM expects platform-specific JRE directory names
    val (platform, arch, ext, jreDirName) = if (osName.contains("linux")) {
      if (osArch.contains("aarch64") || osArch.contains("arm")) 
        ("linux", "aarch64", "tar.gz", "jre-arm64-linux")
      else 
        ("linux", "x64", "tar.gz", "jre-amd64-linux")
    } else if (osName.contains("mac")) {
      if (osArch.contains("aarch64") || osArch.contains("arm")) 
        ("macos", "aarch64", "tar.gz", "jre-arm64-macos")
      else 
        ("macos", "x64", "tar.gz", "jre-amd64-macos")
    } else if (osName.contains("win")) {
      ("windows", "x64", "zip", "jre-amd64-windows")
    } else {
      throw new Exception(s"Unsupported OS: $osName")
    }
    
    val jreDir = jvmDir / jreDirName
    
    if (os.exists(jreDir) && os.isDir(jreDir)) {
      println(s"[JRE] JRE already exists at: $jreDir")
      PathRef(jreDir)
    } else {
      println(s"[JRE] No JRE found in jvm directory. Downloading OpenJDK 17 for $platform-$arch...")
      
      os.makeDir.all(jvmDir)
      val downloadDir = T.dest / "download"
      os.makeDir.all(downloadDir)
      
      // Use Eclipse Temurin OpenJDK 17 from Adoptium
      val jdkVersion = "17.0.13+11"
      val jdkVersionEncoded = jdkVersion.replace("+", "%2B")
      val url = s"https://api.adoptium.net/v3/binary/version/jdk-$jdkVersionEncoded/$platform/$arch/jre/hotspot/normal/eclipse?project=jdk"
      
      val archiveFile = downloadDir / s"openjdk-17-jre.$ext"
      
      println(s"[JRE] Downloading from: $url")
      println(s"[JRE] Platform: $platform, Arch: $arch")
      
      // Download JRE
      os.proc("curl", "-L", "-o", archiveFile.toString, url)
        .call(stdout = os.Inherit, stderr = os.Inherit)
      
      println(s"[JRE] Extracting JRE...")
      
      // Extract archive
      val extractDir = downloadDir / "extracted"
      os.makeDir.all(extractDir)
      
      if (ext == "tar.gz") {
        os.proc("tar", "xzf", archiveFile.toString, "-C", extractDir.toString)
          .call(stdout = os.Inherit, stderr = os.Inherit)
      } else {
        os.proc("unzip", "-q", archiveFile.toString, "-d", extractDir.toString)
          .call(stdout = os.Inherit, stderr = os.Inherit)
      }
      
      // Find the JRE directory (it's usually in a versioned subdirectory)
      val extractedJre = os.list(extractDir).find(p => os.isDir(p) && (p.last.contains("jdk") || p.last.contains("jre")))
        .getOrElse(throw new Exception("Could not find extracted JRE directory"))
      
      // Move to final location
      os.move(extractedJre, jreDir)
      
      println(s"[JRE] OpenJDK 17 JRE successfully embedded at: $jreDir")
      println(s"[JRE] Java version: ")
      os.proc(jreDir / "bin" / "java", "-version")
        .call(stdout = os.Inherit, stderr = os.Inherit)
      
      PathRef(jreDir)
    }
  }
  
  /**
   * Removes the entire JVM directory.
   */
  def cleanJvmDirectory: T[Unit] = T {
    val jvmDir = millSourcePath / "jvm"
    if (os.exists(jvmDir)) {
      println(s"[JVM] Removing JVM directory: $jvmDir")
      os.remove.all(jvmDir)
      println("[JVM] JVM directory removed successfully!")
    } else {
      println("[JVM] JVM directory does not exist, nothing to clean.")
    }
  }
  
  /**
   * Compiles generated Kotlin Entry files to bytecode.
   */
  def compileGeneratedEntry: T[PathRef] = T {
    val generatedDir = generateClassGraphEntry().path
    val entryClassesDir = T.dest / "entry-classes"
    os.makeDir.all(entryClassesDir)
    
    // Find generated Kotlin files
    val kotlinFiles = if (os.exists(generatedDir / "main" / "kotlin")) {
      os.walk(generatedDir / "main" / "kotlin")
        .filter(f => f.ext == "kt" && os.isFile(f))
    } else {
      Seq.empty
    }
    
    if (kotlinFiles.nonEmpty) {
      println(s"[Entry] Compiling ${kotlinFiles.length} generated Kotlin Entry files...")
      
      // Get Kotlin compiler + stdlib (must match godot-kotlin-jvm version)
      val kotlinDeps = resolveDeps(T.task {
        Agg(
          ivy"org.jetbrains.kotlin:kotlin-compiler:2.1.10",
          ivy"org.jetbrains.kotlin:kotlin-stdlib:2.1.10"
        ).map(bindDependency())
      })()
      
      val kotlinc = kotlinDeps.find(_.path.last.contains("kotlin-compiler"))
        .getOrElse(throw new Exception("Kotlin compiler not found"))
      
      val cp = ((compileClasspath() ++ Agg(compile().classes)).map(_.path) ++ kotlinDeps.map(_.path)).mkString(":")
      val kotlincCp = kotlinDeps.map(_.path).mkString(":")
      
      try {
        val args = Seq("java", "-cp", kotlincCp,
          "org.jetbrains.kotlin.cli.jvm.K2JVMCompiler",
          "-d", entryClassesDir.toString,
          "-cp", cp,
          "-jvm-target", "17") ++ kotlinFiles.map(_.toString)
        
        os.proc(args).call()
        
        println(s"[Entry] Kotlin Entry compilation successful!")
      } catch {
        case e: Exception =>
          println(s"[Entry] Warning: Entry compilation failed: ${e.getMessage}")
      }
    } else {
      println("[Entry] No generated Entry files to compile (using fallback ScalaEntry)")
    }
    
    PathRef(entryClassesDir)
  }
  
  /**
   * Main Godot build task.
   */
  def build: T[PathRef] = T {
    println("[Godot] Starting Godot build...")
    println("[Godot] Step 1: Generating Entry from @RegisterClass annotations...")
    val _ = generateClassGraphEntry()
    println("[Godot] Step 2: Compiling generated Entry files...")
    val _ = compileGeneratedEntry()
    println("[Godot] Step 3: Packaging JARs...")
    val bootstrapJar = packageBootstrapJar()
    val mainJar = packageMainJar()
    
    // Copy JARs to project root jvm directory (Mill tasks run in T.dest context)
    val projectJvmDir = millSourcePath / "jvm"
    println(s"[Godot] Step 4: Copying JARs to project jvm directory...")
    if (os.exists(projectJvmDir)) {
      // Remove old JARs
      Seq("godot-bootstrap.jar", "main.jar", "godot-scala-template.jar").foreach { jar =>
        val jarPath = projectJvmDir / jar
        if (os.exists(jarPath)) os.remove(jarPath)
      }
    } else {
      os.makeDir.all(projectJvmDir)
    }
    os.copy(bootstrapJar.path, projectJvmDir / "godot-bootstrap.jar")
    os.copy(mainJar.path, projectJvmDir / "main.jar")
    os.copy(mainJar.path, projectJvmDir / "godot-scala-template.jar")
    println("[Godot]   - godot-bootstrap.jar")
    println("[Godot]   - main.jar")
    println("[Godot]   - godot-scala-template.jar")
    
    println("[Godot] Step 5: Ensuring JRE is embedded...")
    embedJre()
    
    println("[Godot] Step 6: Generating .gdignore files...")
    generateGdIgnoreFiles()
    
    println("[Godot] Build completed successfully!")
    println(s"[Godot] JVM directory is ready at: $projectJvmDir")
    println("[Godot] @RegisterClass annotations are auto-registered via ClassGraph")
    println("[Godot] You can now open this project in Godot editor.")
    PathRef(projectJvmDir)
  }
}
