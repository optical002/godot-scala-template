# Mill Godot Build Module

A reusable Mill build module for Godot Kotlin JVM projects with Scala.

## Installation

Add to your `build.sc`:

```scala
import $file.`mill-godot`.godot
import godot.GodotBuildModule

object yourProject extends ScalaModule with GodotBuildModule {
  def scalaVersion = "3.7.3"
  override def ivyDeps = T { Agg(...) }
}
```

## Configuration

The module automatically configures:
- **Source paths**: Maven layout (`src/main/scala`, `src/main/resources`)
- **Godot dependencies**: Godot Kotlin JVM runtime libraries (compile-only)
- **Repository**: Maven local (`~/.m2/repository`) for Godot artifacts

### Version Configuration

Override these in your module if needed:

```scala
override def godotKotlinVersion = "0.13.0-4.4.1-afac9a3-SNAPSHOT"
override def kotlinVersion = "1.9.0"
override def kotlinCompilerVersion = "2.1.10"
override def classGraphVersion = "4.8.165"
```

## Available Tasks

### Build Tasks

#### `build` / `godotBuild`
Main build task that orchestrates the entire Godot build pipeline.

**What it does:**
1. Generates Entry files from `@RegisterClass` annotations
2. Compiles generated Kotlin Entry files
3. Packages bootstrap JAR (Godot runtime)
4. Packages main JAR (your game code + dependencies)
5. Copies JARs to `jvm/` directory
6. Embeds JRE if not present
7. Generates `.gdignore` files

**Usage:**
```bash
./mill project.build
```

**Output:** `jvm/` directory ready for Godot

---

#### `generateClassGraphEntry`
Scans compiled bytecode for `@RegisterClass` annotations and generates Kotlin Entry files.

**What it does:**
- Uses ClassGraph symbol processor to scan compiled classes
- Generates Kotlin Entry files for Godot class registration
- Creates `.gdj` registration files in `gdj/` directory
- Generates files in Mill's output directory

**Dependencies:** Requires `compile()` to have run first

**Output:** PathRef to generated sources directory

---

#### `compileGeneratedEntry`
Compiles the generated Kotlin Entry files to bytecode.

**What it does:**
- Finds generated Kotlin files from `generateClassGraphEntry()`
- Downloads Kotlin compiler if needed
- Compiles `.kt` files to `.class` files
- Handles compilation errors gracefully (falls back to ScalaEntry)

**Dependencies:** Requires `generateClassGraphEntry()` to have run first

**Output:** PathRef to compiled Entry classes directory

---

### JAR Packaging Tasks

#### `packageBootstrapJar`
Creates the bootstrap JAR containing Godot Kotlin JVM runtime.

**What it does:**
- Resolves all Godot Kotlin JVM dependencies
- Extracts dependency JARs
- Removes signature files (`.SF`, `.DSA`, `.RSA`)
- Packages into single `godot-bootstrap.jar`

**Output:** `godot-bootstrap.jar` (~11MB)

**Contents:**
- Kotlin stdlib
- Godot library (debug)
- Godot API library
- Godot core library
- Godot extension library
- Godot internal library
- Common utilities
- Build properties

---

#### `packageMainJar`
Creates the main JAR containing your game code and dependencies.

**What it does:**
- Includes compiled Scala classes
- Includes compiled Entry classes
- Includes resources from `src/main/resources`
- Includes runtime dependencies (excluding Godot runtime)
- Removes signature files

**Excludes:** Godot runtime dependencies (already in bootstrap JAR)

**Output:** `main.jar` (~16MB with Scala stdlib + Cats)

---

### JRE Management

#### `embedJre`
Downloads and embeds OpenJDK 17 JRE in the project.

**What it does:**
- Detects OS and architecture (Linux/macOS/Windows, x64/ARM)
- Checks if JRE already exists in `jvm/jre-{platform}-{arch}/`
- Downloads OpenJDK 17 from Eclipse Adoptium if missing
- Extracts and moves to correct directory

**Platform support:**
- Linux x64 → `jre-amd64-linux`
- Linux ARM64 → `jre-arm64-linux`
- macOS x64 → `jre-amd64-macos`
- macOS ARM64 → `jre-arm64-macos`
- Windows x64 → `jre-amd64-windows`

**Output:** PathRef to JRE directory

**Note:** JRE is ~45MB compressed, ~180MB extracted

---

#### `cleanJvmDirectory`
Removes the entire `jvm/` directory.

**What it does:**
- Deletes `jvm/` directory and all contents
- Removes JARs and embedded JRE
- Next build will recreate everything

**Usage:**
```bash
./mill project.cleanJvmDirectory
```

---

#### `cleanServer`
Cleans Mill server state to fix connection issues.

**What it does:**
- Removes stale Mill server and worker directories from `.mill/`
- Forces Mill to start fresh on next command
- Fixes "Failed to connect to server" errors

**When to use:**
- After changing Scala version
- When getting "Failed to connect to server" errors
- After major configuration changes
- When builds behave unexpectedly

**Usage:**
```bash
./mill project.cleanServer
# Then rebuild
./mill project.build
```

**Alternative (full clean):**
```bash
./mill clean  # Cleans everything including server
```

---

### Utility Tasks

#### `generateGdIgnoreFiles`
Creates `.gdignore` files in build directories.

**What it does:**
- Creates `.gdignore` in `out/` directory
- Creates `.gdignore` in `.mill/` directory
- Prevents Godot from scanning build artifacts

**Why:** Godot scans all project files; these markers exclude build directories

---

#### `bootstrapDeps`
Returns the Agg of Godot Kotlin JVM runtime dependencies.

**Usage:** Internal task, used by `packageBootstrapJar()`

**Returns:** Dependency list for bootstrap JAR

---

## Build Flow

```
compile()
   ↓
generateClassGraphEntry()  ← Scans @RegisterClass
   ↓
compileGeneratedEntry()    ← Compiles Kotlin Entry
   ↓
├─ packageBootstrapJar()   ← Godot runtime
└─ packageMainJar()        ← Your game code
   ↓
godotBuild()              ← Orchestrates everything
   ├─ Copy JARs to jvm/
   ├─ embedJre()
   └─ generateGdIgnoreFiles()
```

## Output Structure

After running `./mill project.build`:

```
jvm/
├── godot-bootstrap.jar        # Godot runtime (~11MB)
├── main.jar                   # Your game (~16MB)
├── {project-name}.jar         # Symlink to main.jar
├── .gdignore                  # Godot ignore marker
└── jre-amd64-linux/          # Embedded JRE (~180MB)
    ├── bin/
    ├── lib/
    └── ...

gdj/                           # Generated registration files
└── {YourClass}.gdj

out/
└── project/                   # Mill build output
    ├── compile.dest/
    ├── generateClassGraphEntry.dest/
    ├── compileGeneratedEntry.dest/
    ├── packageBootstrapJar.dest/
    └── packageMainJar.dest/
```

## Common Commands

```bash
# Full build
./mill project.build

# Compile only
./mill project.compile

# Clean everything
./mill clean

# Clean JVM directory only
./mill project.cleanJvmDirectory

# Fix Mill server issues
./mill project.cleanServer

# Rebuild from scratch
./mill clean && ./mill project.build

# Show available tasks
./mill resolve project._
```

## Troubleshooting

### Failed to connect to server
Mill server got into a bad state. Fix with:
```bash
./mill project.cleanServer
# Or full clean:
./mill clean
# Then rebuild:
./mill project.build
```

### Build not detecting changes
Run clean first:
```bash
./mill clean && ./mill project.build
```

### ClassGraph errors
Check that:
- Classes have `@RegisterClass` annotation
- Classes extend Godot types (Node, etc.)
- Godot dependencies are in Maven local repo

### JRE download fails
- Check internet connection
- Try manual download from [Adoptium](https://adoptium.net/)
- Place in `jvm/jre-{platform}-{arch}/`

### Kotlin compilation warnings
These are normal:
- "unable to find kotlin-stdlib.jar" → Can be ignored
- "classpath entry points to non-existent location" → Can be ignored

## Customization

### Override source paths
```scala
override def sources = T.sources(millSourcePath / "custom" / "path")
```

### Add custom dependencies to bootstrap
```scala
override def bootstrapDeps = T {
  super.bootstrapDeps() ++ Agg(ivy"your:dependency:1.0")
}
```

### Change JDK version
Edit `embedJre` task and update:
```scala
val jdkVersion = "21.0.1+12"  // Change version here
```

## License

This module is provided as-is for use with Godot Kotlin JVM projects.
