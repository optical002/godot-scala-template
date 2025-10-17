package entry

import godot.annotation.{RegisterClass, RegisterFunction}
import godot.api.Node
import godot.global.GD
import cats.*
import cats.syntax.semigroup.*

@RegisterClass
class HelloFromScala extends Node:
  def semigroupAdd[A: Semigroup](a1: A, a2: A) = a1 |+| a2
  def semigroupAdd2[A](a1: A, a2: A)(using Semigroup[A]) = a1 |+| a2

  def catsCoreTest() =
    GD.INSTANCE.print(s"Semigroup sum: ${semigroupAdd(1, 2)}")
    GD.INSTANCE.print(s"Semigroup string: ${semigroupAdd2("Hello ", "World!")}")

  @RegisterFunction
  override def _ready(): Unit =
    GD.INSTANCE.print("Hello from scala and mill!")
    catsCoreTest()
