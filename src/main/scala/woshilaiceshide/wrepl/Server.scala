package woshilaiceshide.wrepl

import scala.tools.nsc._
import scala.tools.nsc.interpreter._

import woshilaiceshide.wrepl.repl._
import woshilaiceshide.wrepl.util.Utility

import java.io._

object Server {

  def newSimpleServer(interface: String,
    port: Int,
    user: String,
    password: String) = {

    val type_rules = Map[String, Seq[TypeGuardian.TypeRule]]().withDefault { x => Seq(TypeGuardian.AllowAll) }

    val office = new HttpServer.AuthenticationOffice {

      import spray.http._
      def authenticate(request: HttpRequest): Option[String] = {

        import Utility._
        import spray.json._

        val json = request.entity.asString(HttpCharsets.`UTF-8`).parseJson
        (json.str("user"), json.str("password")) match {
          case (Some(user1), Some(password1)) if (user1 == user && password1 == Utility.md5(password)) => {
            Some(user1)
          }
          case _ => {
            None
          }
        }
      }
    }

    new Server(interface, port, type_rules, office, Seq(), Utility.defaultSettings, 32, 60)
  }

  def newServer(interface: String,
    port: Int,
    type_rules: Map[String, Seq[TypeGuardian.TypeRule]],
    user_tokens: Map[String, String],
    md5_salt: String,
    parameters: Seq[NamedParam] = Seq(),
    settings: Settings = Utility.defaultSettings,
    max_lines_kept_in_repl_output_cache: Int = 32,
    repl_max_idle_time_in_seconds: Int = 60) = {

    val office = new HttpServer.AuthenticationOffice {

      import spray.http._
      def authenticate(request: HttpRequest): Option[String] = {

        import Utility._
        import spray.json._

        val json = request.entity.asString(HttpCharsets.`UTF-8`).parseJson
        (json.str("user"), json.str("password")) match {
          case (Some(user1), Some(password1)) if (user_tokens.get(user1) == Some(Utility.md5(s"${password1}${md5_salt}"))) => {
            Some(user1)
          }
          case _ => {
            None
          }
        }
      }
    }

    new Server(interface, port, type_rules, office, parameters, settings, max_lines_kept_in_repl_output_cache, repl_max_idle_time_in_seconds)
  }
}

class Server(interface: String,
    port: Int,
    type_rules: Map[String, Seq[TypeGuardian.TypeRule]],
    office: HttpServer.AuthenticationOffice,
    parameters: Seq[NamedParam] = Seq(),
    settings: Settings = Utility.defaultSettings,
    max_lines_kept_in_repl_output_cache: Int,
    repl_max_idle_time_in_seconds: Int) {

  val born: TaskRunner => IOBridge = taskRunner => {

    import scala.tools.nsc.interpreter.NamedParamClass

    val get_http_server: NamedParamClass = NamedParamClass("get_http_server", "() => woshilaiceshide.wrepl.repl.HttpServer", () => httpServer)

    val factory = new IOBridge.PipedReplFactory {
      def apply(reader: Reader, writer: Writer, user: String): PipedRepl = {
        val my_type_rules = type_rules.withDefault { x => Seq() }(user)
        new PipedRepl(settings, reader, writer, my_type_rules)
      }
    }

    new IOBridge(taskRunner, get_http_server +: parameters, factory, max_lines_kept_in_repl_output_cache, repl_max_idle_time_in_seconds)
  }

  val httpServer: HttpServer = new HttpServer(interface, port, born, office)

  def start(asynchronously: Boolean = false) { httpServer.start(asynchronously) }
  def stop(timeout: Int) { httpServer.stop(timeout) }

}
