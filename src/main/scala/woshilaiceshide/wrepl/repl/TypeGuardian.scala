package woshilaiceshide.wrepl.repl

import scala.tools.nsc.Global
import scala.tools.nsc.Phase
import scala.tools.nsc.plugins.Plugin
import scala.tools.nsc.plugins.PluginComponent

object TypeGuardian {

  object AuditResult extends scala.Enumeration {
    val Unknown, Deny, Allow = Value
  }

  //only support types whose kind is 1
  sealed trait TypeRule {
    def audit(qualified_type_name: String): AuditResult.Value
  }

  case class InvalidTypeRuleException(s: String) extends Exception(s"${s} is not a valid type rule.")

  final class RegexTypeRule(pattern: java.util.regex.Pattern, result: AuditResult.Value) extends TypeRule {

    def audit(qualified_type_name: String): AuditResult.Value = {
      if (pattern.matcher(qualified_type_name).matches()) {
        result
      } else {
        AuditResult.Unknown
      }

    }
  }
  final object AllowAll extends TypeRule {
    def audit(qualified_type_name: String): AuditResult.Value = AuditResult.Allow
  }
  final object DenyAll extends TypeRule {
    def audit(qualified_type_name: String): AuditResult.Value = AuditResult.Deny
  }

  def parse_type_rule(s: String): TypeRule = {
    if (s == "deny all") {
      DenyAll

    } else if (s == "allow all") {
      AllowAll

    } else if (s.startsWith("deny ")) {
      new RegexTypeRule(s.substring("deny ".length()).r.pattern, AuditResult.Deny)

    } else if (s.startsWith("allow ")) {
      new RegexTypeRule(s.substring("allow ".length()).r.pattern, AuditResult.Allow)

    } else {
      throw InvalidTypeRuleException(s)
    }
  }

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

        //derived from https://github.com/dcaoyuan/nbscala/blob/v1.7.0.0/scala.core/src/main/scala/org/netbeans/modules/scala/core/ast/ScalaUtils.scala
        /** use to test if type is the same: when they have same typeSimpleSig true, otherwise false */
        private def typeSimpleSig_(tpe: Type, sb: StringBuilder) {
          if (tpe eq null) return
          tpe match {
            case ErrorType =>
              sb.append("<error>")
            // internal: error
            case WildcardType => sb.append("_")
            // internal: unknown
            case NoType => sb.append("<notype>")
            case NoPrefix => sb.append("<noprefix>")
            case ThisType(sym) =>
              sb append (sym.fullName)
            case SingleType(pre, sym) =>
              sb append (sym.fullName)
            case ConstantType(value) =>
            // int(2)
            case TypeRef(pre, sym, args) =>
              sb append (sym.fullName)
              sb append (args map (x => typeSimpleSig_(x, sb)) mkString ("[", ",", "]"))
            // pre.sym[targs]
            case RefinedType(parents, defs) =>
              sb append (parents map (x => typeSimpleSig_(x, sb)) mkString (" extends ", "with ", ""))
            //case AnnotatedType(annots, tp, selfsym) =>
            //  typeSimpleSig_(tp, sb)
            case TypeBounds(lo, hi) =>
              sb append (">: ")
              typeSimpleSig_(lo, sb)
              sb append (" <: ")
              typeSimpleSig_(hi, sb)
            // >: lo <: hi
            case ClassInfoType(parents, defs, clazz) =>
              sb append (parents map (x => typeSimpleSig_(x, sb)) mkString (" extends ", " with ", ""))
            case MethodType(paramtypes, result) => // same as RefinedType except as body of class
              sb append (paramtypes map (x => typeSimpleSig_(x.tpe, sb)) mkString ("(", ",", ")"))
              sb append (": ")
              typeSimpleSig_(result, sb)
            // (paramtypes): result
            case PolyType(tparams, result) =>
              sb append (tparams map (x => typeSimpleSig_(x.tpe, sb)) mkString ("[", ",", "]"))
              sb append (": ")
              typeSimpleSig_(result, sb)
            // [tparams]: result where result is a MethodType or ClassInfoType
            // or
            // []: T  for a eval-by-name type
            case ExistentialType(tparams, result) =>
              sb append ("ExistantialType")
            // exists[tparams]result

            // the last five types are not used after phase `typer'.

            //case OverloadedType(pre, tparams, alts) => "Overlaod"
            // all alternatives of an overloaded ident
            case AntiPolyType(pre: Type, targs) =>
              sb append ("AntiPolyType")
            case TypeVar(_, _) =>
              sb append (tpe.safeToString)
            // a type variable
            //case DeBruijnIndex(level, index) =>
            //sb append ("DeBruijnIndex")
            case _ =>
              sb append (tpe.safeToString)
          }
        }

        private val string_builder_repo = new ThreadLocal[StringBuilder] {
          override def initialValue() = new StringBuilder
        }

        private def check_type_sign_0(type_sign: String, type_rules: Seq[TypeRule]): Boolean = {
          if (0 == type_rules.size) {
            false
          } else {
            type_rules.head.audit(type_sign) match {
              case AuditResult.Unknown => check_type_sign_0(type_sign, type_rules.tail)
              case x => x == AuditResult.Allow
            }
          }
        }

        private def check_type_sign(tree: Tree, type_sign: String): Boolean = {

          if (type_sign == "" || type_sign.endsWith(".type")) {
            //typeSimpleSig_(tree.tpe, sb)
            global.reporter.error(tree.pos, s"an unexpected type(${type_sign}) arised")
          }

          if (type_sign == "<empty>") {
            //TODO
          }

          if (type_sign.contains("[")) {
            //TODO
          }

          if (type_sign.endsWith(".A")) {
            //TODO
          }

          if (check_type_sign_0(type_sign, type_rules)) {
            false
          } else {
            global.reporter.error(tree.pos, s"a forbidden type(${type_sign}) arised")
            false
          }
        }

        private def inspect(tree: Tree, tpe: global.Type): Unit = {
          tpe match {
            case ExistentialType(tparams, result) =>
            case AntiPolyType(pre: Type, targs) =>
            case SuperType(thistpe, supertpe) =>
            //omitted, especially for UniqueSuperType
            //thistpe and supertpe will be watched elsewhere
            case MethodType(paramtypes, result) =>
            //omitted, MethodType is always permitted
            case ConstantType(value) =>
            //omitted, ConstantType is always permitted
            case _: NoArgsTypeRef with AbstractTypeRef =>
            //A in List[A]
            //omitted, AbstractNoArgsTypeRef 
            case ErrorType | WildcardType | NoType | NoPrefix =>
            //omitted
            case NullaryMethodType(r) =>
              inspect(tree, r)
            case TypeRef(pre, sym, args) => {
              check_type_sign(tree, sym.fullName)
              args.foreach { arg => inspect(tree, arg) }
            }
            case TypeBounds(lo, hi) => {
              inspect(tree, lo)
              inspect(tree, hi)
            }
            case PolyType(tparams, result) => {
              inspect(tree, result)
              tparams.map { x => inspect(tree, x.tpe) }
            }
            case x => {
              val sb = string_builder_repo.get()
              sb.clear()
              typeSimpleSig_(tpe, sb)
              check_type_sign(tree, sb.toString)

            }

          }

        }

        override def traverse(tree: Tree) {

          tree match {
            case tree @ Apply(Select(rcvr, nme.DIV), List(Literal(Constant(0)))) if rcvr.tpe <:< definitions.IntClass.tpe => {
              global.reporter.error(tree.pos, "definitely division by zero")
            }
            case x => {
              inspect(tree, tree.tpe)
              super.traverse(x)
            }
          }
        }
      }

      def apply(unit: CompilationUnit) {
        (new Traverse).traverse(unit.body)
      }
    }
  }
}
