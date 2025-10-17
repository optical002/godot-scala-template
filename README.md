# Godot Scala Template

A Scala 3 project template for Godot Engine using Godot Kotlin JVM.

## Project Structure

### Build Files

- **`build.sc`** - Minimal project configuration (dependencies, Scala version)
- **`mill-godot/godot.sc`** - Reusable Godot build module with all Godot-specific logic
- **`src/main/scala/`** - Your Scala game code
- **`src/main/resources/`** - Project resources
- **`jvm/`** - Output directory for JARs and JRE (used by Godot)

## Quick Start

### Build Commands

```bash
# Full build (first time, or when adding new @RegisterClass)
./mill project.build

# Fast incremental build (code changes only)
./mill project.dev

# Watch mode (auto-rebuild on file save)
./mill -w project.dev

# Compile only
./mill project.compile

# Clean everything
./mill clean
```

**Important:** Use `./mill` (local wrapper), not system `mill`.

## Project Configuration

The `build.sc` file contains only your project-specific settings:

```scala
object project extends ScalaModule with GodotBuildModule {
  def scalaVersion = "3.7.3"
  
  override def ivyDeps = T {
    Agg(
      ivy"org.typelevel::cats-core:2.12.0"
    )
  }
}
```

### Adding Dependencies

Simply add libraries to `ivyDeps`:

```scala
override def ivyDeps = T {
  Agg(
    ivy"org.typelevel::cats-core:2.12.0",
    ivy"io.circe::circe-core:0.14.6",
    ivy"com.softwaremill.sttp.client3::core:3.9.1"
  )
}
```

### Changing Scala Version

Update the `scalaVersion` in `build.sc`:

```scala
def scalaVersion = "3.7.3"  // or "3.5.2", "3.4.3", etc.
```

## Godot Integration

### Registering Classes

Use `@RegisterClass` annotation on classes that extend Godot types:

```scala
import godot.annotation.{RegisterClass, RegisterFunction}
import godot.api.Node
import godot.global.GD

@RegisterClass
class MyGameNode extends Node:
  
  @RegisterFunction
  override def _ready(): Unit =
    GD.INSTANCE.print("Hello from Scala!")
```

### Build Process

When you run `./mill project.build`:

1. **Compiles** your Scala code
2. **Generates** Kotlin Entry files from `@RegisterClass` annotations using ClassGraph
3. **Packages** bootstrap JAR (Godot Kotlin runtime)
4. **Packages** main JAR (your game code + dependencies)
5. **Copies** JARs to `jvm/` directory
6. **Embeds** OpenJDK 17 JRE (downloads if not present)
7. **Creates** `.gdignore` files

### After Building

1. Open your project in Godot Editor
2. Your Scala classes will be available as Godot nodes
3. Attach them to scenes like any other node type

## Advanced: Reusing the Build Module

The `mill-godot/godot.sc` module is reusable in other projects:

```scala
import $file.`path-to`.`mill-godot`.godot
import godot.GodotBuildModule

object myGame extends ScalaModule with GodotBuildModule {
  def scalaVersion = "3.7.3"
}
```

## Configuration Files

### `godot_kotlin_configuration.json`

Runtime configuration for Godot Kotlin JVM:

- **`debug_port`**: Port for remote debugging (default: 5005)
- **`use_debug`**: Enable debug mode
- **`wait_for_debugger`**: Wait for debugger to attach before starting
- **`custom_jvm_args`**: Additional JVM arguments

Example for enabling remote debugging:

```json
{
  "use_debug": true,
  "debug_port": 5005,
  "wait_for_debugger": false,
  "custom_jvm_args": ["-Xmx2G"]
}
```

## File Structure

```
godot-scala-template/
├── build.sc                      # Project configuration
├── mill-godot/
│   └── godot.sc                  # Godot build module (reusable)
├── godot_kotlin_configuration.json  # Runtime config
├── src/
│   └── main/
│       ├── scala/                # Your Scala game code
│       │   └── entry/
│       │       └── HelloFromScala.scala
│       └── resources/            # Resources
│           └── META-INF/
│               └── services/
│                   └── godot.registration.Entry
├── jvm/                          # Generated (Godot reads from here)
│   ├── godot-bootstrap.jar
│   ├── main.jar
│   ├── godot-scala-template.jar
│   └── jre-amd64-linux/         # Embedded JRE
├── gdj/                          # Generated registration files
└── out/                          # Mill build output
```

## Troubleshooting

### Build cache issues

If changes aren't picked up:

```bash
./mill clean
./mill project.build
```

### Source files not found

Make sure your Scala files are in `src/main/scala/` (Maven layout).

### Godot doesn't see my classes

1. Check that `@RegisterClass` annotation is present
2. Rebuild: `./mill project.build`
3. Restart Godot Editor

## Development Workflow

### Initial Setup
```bash
./mill project.build  # First build (~17s)
```

### Development Iteration
```bash
# Option 1: Manual rebuild after changes
./mill project.dev  # ~6-7s (incremental)

# Option 2: Watch mode (recommended)
./mill -w project.dev  # Auto-rebuild on save (~2-3s)
```

### When to Use Which Command

| Command | When to Use | Time |
|---------|-------------|------|
| `./mill project.build` | First build, adding new `@RegisterClass` | ~17s |
| `./mill project.dev` | Code changes only | ~6-7s |
| `./mill -w project.dev` | Continuous development | ~2-3s |

**Note:** Use `build` when adding new Godot classes, `dev` for code changes only.

## License

This template is provided as-is for use with Godot Kotlin JVM projects.
