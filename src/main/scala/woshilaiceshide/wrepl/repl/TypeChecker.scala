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
    override val runsRightAfter: Option[String] = Some("refchecks")
    // Using the Scala Compiler 2.8.x the runsAfter should be written as below
    //val runsAfter = List[String]("refchecks");
    val phaseName = TypeChecker.this.name
    override def description = TypeChecker.this.description
    def newPhase(_prev: Phase) = new TypeCheckerPhase(_prev)

    override val requires = List("typer", "refchecks")

    class TypeCheckerPhase(prev: Phase) extends StdPhase(prev) {

      def show(x: Any) {
        println(x)
      }

      override def name = TypeChecker.this.name
      def apply(unit: CompilationUnit) {
        //println(s"source: ${unit.source}")
        //println(s"body: ${unit.body}")
        for (
          tree @ Apply(Select(rcvr, nme.DIV), List(Literal(Constant(0)))) <- unit.body; if rcvr.tpe <:< definitions.IntClass.tpe
        ) {
          println(tree)
          global.reporter.error(tree.pos, "definitely division by zero")
        }
        def clzName(x: Any) = {
          val raw = x.getClass.getName
          val i = raw.indexOf('$')
          raw.substring(i + 1)
        }
        println(s"----------${unit}")
        for (tree <- unit.body) {
          tree match {
            case Select(x, y) => {
              if (x.isInstanceOf[global.Ident] && x.toString == "t") {
                show(x)
              }

              println(s"a tree(${clzName(tree)}): ${clzName(x)} -> ${clzName(y)}: ${x} -> ${y}")
            }
            case x => //println(s"a tree(${tree.getClass}): ${tree}")
          }

        }
      }
    }
  }
}
