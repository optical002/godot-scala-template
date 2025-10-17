import mill._
import mill.scalalib._
import $file.`mill-godot`.godot
import godot.GodotBuildModule

object project extends ScalaModule with GodotBuildModule {
  def scalaVersion = "3.7.3"
  
  override def ivyDeps = T {
    Agg(
      ivy"org.typelevel::cats-core:2.12.0"
    )
  }
}
