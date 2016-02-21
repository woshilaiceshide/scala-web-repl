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

    val phaseName = TypeChecker.this.name
    override def description = TypeChecker.this.description

    def newPhase(_prev: Phase) = new TypeCheckerPhase(_prev)

    override val requires = List("typer", "refchecks")

    //some codes to test 
    //1. {class X{def xxx = 3; }; new X}.xxx
    //2. val x = new Object{def xxx = 3; }; x.xxx
    //3. { class X { def xxxxxxx() {val x = new Object; x.hashCode.toString.length} }; new X }.xxxxxxx() 
    //4. def x(){object t{def length = 3}; def y(){ t.length.hashCode; println("abc")}}
    //5. def x(){val t = "123"; def y(){ t.length.hashCode; println("abc")}}
    //6. class C{def x[T](t: T) = println(t); }; (new C).x[String]("123")
    class TypeCheckerPhase(prev: Phase) extends StdPhase(prev) {

      import scala.reflect.internal.Symbols

      override def name = TypeChecker.this.name
      def apply(unit: CompilationUnit) {

        //println(s"source: ${unit.source}")
        //println(s"body: ${unit.body}")

        @inline def check(tree: Tree): Boolean = {
          tree match {
            case Select(qualifier @ Ident(ident), name) =>
            case Select(qualifier, name) => check(qualifier)
            case x: global.TypeTree => {
              if (null == x.original) {
                true
              } else {
                check(x.original) //Select
              }
            }
            case null => true
          }
          true
        }

        for (
          tree @ Apply(Select(rcvr, nme.DIV), List(Literal(Constant(0)))) <- unit.body; if rcvr.tpe <:< definitions.IntClass.tpe
        ) {
          println(tree)
          global.reporter.error(tree.pos, "definitely division by zero")
        }
        def clzName(x: Any) = {
          if (null == x) {
            "Null"
          } else {
            val raw = x.getClass.getName
            val i = raw.indexOf('$')
            raw.substring(i + 1)
          }
        }
        println(s"----------> (prev: ${prev})${unit}")
        for (tree <- unit.body) {
          tree match {
            case Select(x, y) => {
              if (x.isInstanceOf[global.Ident]) {
                x.asInstanceOf[global.Ident].symbol match {
                  case ts: global.TermSymbol => ts.owner.isInstanceOf[global.MethodSymbol]
                  //case ts: global.Symbol => ts.owner.isInstanceOf[global.MethodSymbol]
                }
                println(x)
                //??? check its initOwner? check its path?
              } else if (x.isInstanceOf[global.New]) {
                println(x)
                //??? check its initOwner? check its path?
              }
              println(s"a tree(${clzName(tree)}): ${clzName(x)} -> ${clzName(y)}: ${x} -> ${y}")
            }
            case x: global.TypeTree => {
              println(s"orignial(${clzName(x.original)}): ${x.original}")
              println(s"a tree(${clzName(tree)}): ${tree}")
            }
            case x if x.isInstanceOf[global.TypeApply] => {
              val t = x.asInstanceOf[global.TypeApply]
              println(s"a tree(${clzName(tree)}): ${tree}")
            }
            case x => //println(s"a tree(${clzName(tree)}): ${tree}")
          }

        }
      }
    }
  }
}
