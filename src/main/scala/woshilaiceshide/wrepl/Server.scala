package woshilaiceshide.wrepl

import scala.tools.nsc._
import scala.tools.nsc.interpreter._

import woshilaiceshide.wrepl.util.Utility

class Server(interface: String, port: Int, parameters: Seq[NamedParam], settings: Settings = Utility.defaultSettings, max_lines_kept_in_output_cache: Int = 32, repl_max_idle_time_in_seconds: Int = 60) {

  import woshilaiceshide.wrepl.repl._

  val httpServer = new HttpServer(interface, port, taskRunner => {
    new Bridge(taskRunner, parameters, bridge => {
      new PipedRepl(settings, bridge.writer)
    }, max_lines_kept_in_output_cache, repl_max_idle_time_in_seconds)
  })

  def start() { httpServer.start() }
  def stop(timeout: Int) { httpServer.stop(timeout) }

}