# Godot Scala Template (Linux)

This is a template project for using the [Godot Kotlin JVM module](https://github.com/utopia-rise/godot-kotlin-jvm) with Scala.

Currently, the **Godot Kotlin JVM module** itself does not provide an official release of Scala support.  
However, a working version is available in its `develop` branch.

The **`v1.0.0` release** of this template contains:
- A Godot build with Scala support
- Prebuilt Maven packages with Scala integration

---

## 🚀 How to Use

1. **Download required files:**
    - This repository
    - Godot game engine binary: `godot.linuxbsd.editor.x86_64.jvm.0.13.0`
    - Maven packages archive: `com.utopia-rise.zip`

2. **Install Maven packages**  
   Extract `com.utopia-rise.zip` into your local Maven repository:
   ```bash
   unzip com.utopia-rise.zip -d ~/.m2/repository
   ```
   
3. **Build the Gradle project**
   ```bash
   ./gradlew build
   ```

4. **Open the project in Godot**  
   Launch the Godot editor `godot.linuxbsd.editor.x86_64.jvm.0.13.0` and open this repository as a project.

5. **Run the demo scene**  
   Open `node_2d.tscn` in the editor and press F6 to play the current scene.

6. **Check the output**  
   The log should show:
   ```
   Hello from scala!
   ```

7. **🎉 Enjoy building with Scala + Godot!**
