# Mill Godot Build Module

A reusable Mill build module for Godot Kotlin JVM projects with Scala.

## Installation

```scala
import $file.`mill-godot`.godot
import godot.GodotBuildModule

object yourProject extends ScalaModule with GodotBuildModule {
  def scalaVersion = "3.7.3"
  override def ivyDeps = T { Agg(...) }
}
```

## Available Tasks

### Primary Tasks

#### `build`
Full build pipeline. Use when:
- First time build
- Adding new `@RegisterClass` classes  
- Changing dependencies

**Time:** ~17s

```bash
./mill project.build
```

#### `dev`
Fast incremental build. Use when:
- Changing existing code only
- No new `@RegisterClass` added

**Time:** ~6-7s (warm), ~2-3s (watch mode)

```bash
# Manual rebuild
./mill project.dev

# Watch mode (auto-rebuild on save)
./mill -w project.dev
```

**What it does:**
- Compiles changed Scala files
- Updates classes directly in existing JAR (no repackaging)
- Skips ClassGraph Entry generation
- Skips Kotlin compilation

**Warning:** Will NOT detect new `@RegisterClass` - use `build` instead.

---

### Supporting Tasks

#### `generateClassGraphEntry`
Scans `@RegisterClass` annotations and generates Kotlin Entry files.

#### `compileGeneratedEntry`
Compiles generated Kotlin Entry files to bytecode.

#### `packageBootstrapJar`
Creates `godot-bootstrap.jar` (~11MB) with Godot Kotlin runtime.

#### `packageMainJar`
Creates `main.jar` with your game code and dependencies.

#### `embedJre`
Downloads and embeds OpenJDK 17 JRE for your platform.

#### `cleanJvmDirectory`
Removes `jvm/` directory completely.

```bash
./mill project.cleanJvmDirectory
```

#### `cleanServer`
Cleans Mill server state. Use when getting connection errors.

```bash
./mill project.cleanServer
```

---

## Build Flow

```
Full Build (./mill project.build):
  compile() → generateClassGraphEntry() → compileGeneratedEntry() 
  → packageBootstrapJar() + packageMainJar() → embedJre()
  
Fast Build (./mill project.dev):
  compile() → update JAR in-place (no Entry generation)
```

## Output Structure

```
jvm/
├── godot-bootstrap.jar        # Godot runtime (~11MB)
├── main.jar                   # Your game (~16MB)
├── {project-name}.jar         # Copy of main.jar
└── jre-{platform}-{arch}/     # Embedded JRE (~180MB)

gdj/
└── {YourClass}.gdj            # Generated registration files

out/project/                   # Mill build output
```

## Configuration

The module automatically configures:
- **Source paths**: `src/main/scala`, `src/main/resources`
- **Godot dependencies**: Compile-only (not in main JAR)
- **Maven local repo**: `~/.m2/repository`

### Performance Tuning

Configured for 16GB RAM builds:
- Heap: 8GB-16GB
- G1 Garbage Collector
- 16 parallel compilation threads

Edit `forkArgs` in `godot.sc` to adjust.

## Common Commands

```bash
# Development workflow
./mill project.build           # First time
./mill -w project.dev          # Continuous development

# Maintenance
./mill clean                   # Clean everything
./mill project.cleanJvmDirectory  # Clean JARs only
./mill project.cleanServer     # Fix Mill server issues
```

## Troubleshooting

### "Failed to connect to server"
```bash
./mill project.cleanServer
./mill project.build
```

### Changes not detected
```bash
./mill clean
./mill project.build
```

### New classes not showing in Godot
- Did you use `@RegisterClass`?
- Run `./mill project.build` (not `dev`)
- Restart Godot Editor

## License

Provided as-is for Godot Kotlin JVM projects.
