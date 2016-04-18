package woshilaiceshide.wrepl

import scala.tools.nsc._
import scala.tools.nsc.interpreter._

import woshilaiceshide.wrepl.repl._
import woshilaiceshide.wrepl.util.Utility

import java.io._

object Server {
  def newServer(interface: String, port: Int) = new Server(interface, port)
}

class Server(interface: String, port: Int, type_rules: Map[String, Seq[TypeGuardian.TypeRule]] = Map(), parameters: Seq[NamedParam] = Seq(), settings: Settings = Utility.defaultSettings, max_lines_kept_in_repl_output_cache: Int = 32, repl_max_idle_time_in_seconds: Int = 60) {

  import scala.tools.nsc.interpreter.NamedParamClass

  val httpServer: HttpServer = new HttpServer(interface, port, taskRunner => {

    val get_http_server: NamedParamClass = NamedParamClass("get_http_server", "() => woshilaiceshide.wrepl.repl.HttpServer", () => httpServer)

    val born = new IOBridge.PipedReplFactory {
      def apply(reader: Reader, writer: Writer, user: String): PipedRepl = {
        val my_type_rules = type_rules.withDefault { x => Seq() }(user)
        new PipedRepl(settings, reader, writer, my_type_rules)
      }
    }

    new IOBridge(taskRunner, get_http_server +: parameters, born, max_lines_kept_in_repl_output_cache, repl_max_idle_time_in_seconds)
  })

  def start(asynchronously: Boolean = false) { httpServer.start(asynchronously) }
  def stop(timeout: Int) { httpServer.stop(timeout) }

}
