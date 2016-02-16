package woshilaiceshide.wrepl.repl

import scala.tools.nsc.Global
import scala.tools.nsc.Phase
import scala.tools.nsc.plugins.Plugin
import scala.tools.nsc.plugins.PluginComponent

class TypeChecker(val global: Global) extends Plugin {
  import global._

  val name = "typechecker"
  val description = "type checker"
  val components = List[PluginComponent](Component)

  private object Component extends PluginComponent {
    val global: TypeChecker.this.global.type = TypeChecker.this.global
    val runsAfter = List("refchecks")
    // Using the Scala Compiler 2.8.x the runsAfter should be written as below
    // val runsAfter = List[String]("refchecks");
    val phaseName = TypeChecker.this.name
    override def description = TypeChecker.this.description
    def newPhase(_prev: Phase) = new TypeCheckerPhase(_prev)

    override val requires = List("typer", "refchecks")

    class TypeCheckerPhase(prev: Phase) extends StdPhase(prev) {

      override def name = TypeChecker.this.name
      def apply(unit: CompilationUnit) {
        println(s"source: ${unit.source}")
        println(s"body: ${unit.body}")
      }
    }
  }
}
