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
    //7. class C{def x[T](t: T) = println(t); }; (new C).x[java.lang.String]("123")
    //8. def x(){class X(i: Int){}; val obj = new X(123); obj.hashCode()}
    class TypeCheckerPhase(prev: Phase) extends StdPhase(prev) {

      import scala.reflect.internal.Symbols

      override def name = TypeChecker.this.name

      class Traverse extends global.Traverser {

        private final def extract_qualified_name(tree: Tree, tail: List[String] = Nil): Either[Tree, List[String]] = {
          tree match {
            case Select(qualifier @ Ident(ident), name) => {
              qualifier.symbol match {
                case null => Right(ident.toString() :: name.toString :: tail)
                case x: TermSymbol /*Symbol???*/ if x.owner.isInstanceOf[MethodSymbol] => Right(Nil)
                case _ => Right(ident.toString() :: name.toString :: tail)
              }
            }
            case Select(qualifier @ Select(_, _), name) => {
              extract_qualified_name(qualifier, name.toString :: tail)
            }
            case Select(qualifier: New, name) => {
              //qualifier is an Ident or a Select
              Left(qualifier)
            }
            case Select(qualifier: Super, name) => {
              Left(qualifier)
            }
            case Select(qualifier: This, name) => {
              Right(qualifier.qual.toString() :: name.toString :: tail)
            }
            case Select(qualifier, name) => Left(qualifier)
            case _ => Left(tree)
          }
        }

        override def traverse(tree: Tree) {
          tree match {
            case x @ TypeApply(fun, args) => {
              //TODO may an Ident in args?
              println(s"a tree(${clzName(tree)}): ${tree}")
              super.traverse(x)
            }
            case x: TypeTree if (null != x.original) => {
              //traverse using my code first
              extract_qualified_name(x.original) match {
                case Left(please_traverse) => super.traverse(please_traverse)
                case Right(qualified_name) => println(s"qualified_name: ${qualified_name.mkString(".")}")
              }
            }
            case x @ Select(qualifier, name) => {

              println(s"a tree(${clzName(x)}): ${clzName(qualifier)} -> ${clzName(name)}: ${qualifier} -> ${name}")

              extract_qualified_name(x) match {
                case Left(please_traverse) => super.traverse(please_traverse)
                case Right(qualified_name) => println(s"qualified_name: ${qualified_name.mkString(".")}")
              }
            }
            case tree @ Apply(Select(rcvr, nme.DIV), List(Literal(Constant(0)))) if rcvr.tpe <:< definitions.IntClass.tpe => {
              global.reporter.error(tree.pos, "definitely division by zero")
            }
            case x => super.traverse(x)
          }
        }

        def clzName(x: Any) = {
          if (null != x) {
            val raw = x.getClass.getName
            val i = raw.indexOf('$')
            raw.substring(i + 1)
          } else {
            "NULL"
          }
        }
      }

      def apply(unit: CompilationUnit) {

        //println(s"source: ${unit.source}")
        println(s"body: ${unit.body}")

        println(s"----------> (prev: ${prev})${unit}")
        (new Traverse).traverse(unit.body)
      }
    }
  }
}
