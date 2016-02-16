package woshilaiceshide.wrepl

abstract class Animal {
  def speak(): Unit
}

object DynamicCodeExample extends App {

  import scala.reflect.runtime.currentMirror
  import scala.tools.reflect.ToolBox

  val toolbox = currentMirror.mkToolBox()

  val code = """
import woshilaiceshide.wrepl._
class Cat extends Animal {
  def speak() { println("meow...") }
}
new Cat()
"""

  val tree = toolbox.parse(code)
  val compiled = toolbox.compile(tree)

  val animal = compiled().asInstanceOf[Animal]
  animal.speak()

}