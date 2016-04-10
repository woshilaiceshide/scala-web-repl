package woshilaiceshide.wrepl.repl

import scala.tools.nsc.Global
import scala.tools.nsc.Phase
import scala.tools.nsc.plugins.Plugin
import scala.tools.nsc.plugins.PluginComponent

object TypeGuardian {

  //TODO only support types whose kind is 1
  sealed trait TypeRule {
    def should_forbidden(qualified_type_name: String): Boolean
  }
  //TODO
  def parse_type_rule(s: String): Option[TypeRule] = ???

}

import TypeGuardian._
class TypeGuardian(val global: Global, type_rules: Seq[TypeRule]) extends Plugin {
  import global._

  val name = "typeguardian"
  val description = "type guardian"
  val components = List[PluginComponent](Component)

  private object Component extends PluginComponent {

    val global: TypeGuardian.this.global.type = TypeGuardian.this.global

    val runsAfter = List("refchecks")
    override val runsRightAfter: Option[String] = Some("refchecks")

    val phaseName = TypeGuardian.this.name
    override def description = TypeGuardian.this.description

    def newPhase(_prev: Phase) = new TypeGuardianPhase(_prev)

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

    //9. {class X{def xxx: java.util.Date = null; }; new X}.xxx 
    //10. {class X{def xxx = new String("123"); }; new X}.xxx 
    //11. {class X{def xxx = 3; }; new X}.xxx 
    //12. {class X{def xxx: Int = 3; }; new X}.xxx
    //13. {class X{def xxx = new ==(); }; new X}.xxx
    //14. {class X{def xxx = (new ==()).hashCode; }; new X}.xxx

    class TypeGuardianPhase(prev: Phase) extends StdPhase(prev) {

      import scala.reflect.internal.Symbols

      override def name = TypeGuardian.this.name

      class Traverse extends global.Traverser {

        //@scala.annotation.tailrec
        private final def extract_qualified_name_from_type(tpe: Type, tail: List[String] = Nil): List[String] = {

          //see "scala.reflect.internal.Symbols.Symbol.toString"

          val pre = tpe.prefix
          val sym = tpe.typeSymbol

          var _me: Option[String] = null
          //def get_me = if (sym.hasMeaninglessName) sym.owner.decodedName else sym.decodedName
          //the related type will show somewhere else when it has no meaning.   
          def me = {
            if (null == _me)
              _me = if (sym.hasMeaninglessName) None else Some(sym.decodedName)
            _me
          }

          if ((NoPrefix == pre || NoType == pre) && NoSymbol == tpe.typeSymbol) {
            tail
          } else if (NoPrefix == pre || NoType == pre) {
            me match {
              case Some(x) => {
                if (x == "<root>") tail
                else x :: tail
              }
              case None => Nil
            }
          } else {
            tpe match {
              case x: UniqueThisType => {
                me match {
                  case Some(x) => {
                    if (x == "<root>") tail
                    else extract_qualified_name_from_type(pre, x :: tail)
                  }
                  case None => Nil
                }
              }
              case x => {
                me match {
                  case Some(x) => {
                    extract_qualified_name_from_type(pre, x :: tail)
                  }
                  case None => Nil
                }
              }
            }
          }
        }
        private final def extract_qualified_name_from_tree(tree: Tree, tail: List[String] = Nil): Either[Tree, List[String]] = {
          tree match {
            case Select(qualifier @ Ident(ident), name) => {
              qualifier.symbol match {
                case null => Right(ident.toString() :: name.toString :: tail)
                case x: Symbol /*TermSymbol ???*/ if x.owner.isInstanceOf[MethodSymbol] => Right(Nil)
                case _ => Right(ident.toString() :: name.toString :: tail)
              }
            }
            case Select(qualifier @ Select(_, _), name) => {
              extract_qualified_name_from_tree(qualifier, name.toString :: tail)
            }
            case Select(qualifier: New, name) => {
              //qualifier is an Ident or a Select
              qualifier.tpt match {
                case x @ Ident(ident) => {
                  x.symbol match {
                    case null => Right(ident.toString() :: name.toString :: tail)
                    case x: Symbol /*ClassSymbol???*/ if x.owner.isInstanceOf[TermSymbol] => Right(Nil)
                    case _ => Right(ident.toString() :: name.toString :: tail)
                  }
                }
                case x => Left(x)
              }
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

          println(s"a tree(${clzName(tree)}): ${tree}")

          tree match {
            case x @ TypeApply(fun, args) => {
              //TODO may be an Ident in args?
              //println(s"a tree(${clzName(tree)}): ${tree}")
              super.traverse(x)
            }
            case x: TypeTree if (null != x.original) => {
              //traverse using my code first

              val tpe = x.tpe
              val args = tpe.typeArgs

              extract_qualified_name_from_tree(x.original) match {
                case Left(please_traverse) => super.traverse(please_traverse)
                case Right(qualified_name) => println(s"qualified_name: ${qualified_name.mkString(".")}")
              }
            }
            case x: TypeTree => {
              val tpe = x.tpe
              val args = tpe.typeArgs

              println(s"a type: ${extract_qualified_name_from_type(tpe).mkString(".")}")
              args.map { arg =>
                println(s"a type: ${extract_qualified_name_from_type(arg).mkString(".")}")
              }
            }
            case x @ Select(qualifier, name) => {

              //println(s"a tree(${clzName(x)}): ${clzName(qualifier)} -> ${clzName(name)}: ${qualifier} -> ${name}")

              extract_qualified_name_from_tree(x) match {
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
        //println(s"body: ${unit.body}")

        println(s"----------> (prev: ${prev})${unit}")
        (new Traverse).traverse(unit.body)
      }
    }
  }
}
