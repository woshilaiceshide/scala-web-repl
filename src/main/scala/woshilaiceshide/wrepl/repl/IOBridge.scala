package woshilaiceshide.wrepl.repl

import spray.json._
import spray.json.DefaultJsonProtocol._

import com.google.common.collect.EvictingQueue

import woshilaiceshide.sserver.http.WebSocketChannel

import scala.collection.JavaConverters._

import scala.tools.nsc.interpreter.NamedParam
import scala.tools.nsc.interpreter.NamedParamClass
import scala.tools.nsc.interpreter.Completion
import scala.tools.nsc.interpreter.NoCompletion

import java.io._

object IOBridge {

  trait PipedReplFactory {
    def apply(reader: Reader, writer: Writer, user: String): PipedRepl
  }

  //commands that the io bridge actor should respond with
  case class Connect(channel: WebSocketChannel, force: Boolean)
  case class Disconnect(channel: WebSocketChannel)
  private[repl] case class CheckDisconnection(connection_behavior_id: Int)
  private[repl] case class ReplDied(repl: PipedRepl)

  case class RegisterPipeAndCompleter(repl: PipedRepl, output: PipedOutputStream, completer: Completion.ScalaCompleter)

  case class Write(cbuf: Array[Char], off: Int, len: Int)
  case class ClientInput(s: String, channel: WebSocketChannel)

  def turn2ClientCmdJsString(cmd: String, fields: (String, String)*) = {
    val js = JsObject(("cmd" -> JsString(cmd)) +: fields.map { x => (x._1, JsString(x._2)) }: _*)
    js.compactPrint
  }

  def formatNamedParam(index: Int, p: NamedParam) = s"""${index}). ${p.name}: ${p.tpe} = ${p.value}"""

}

import IOBridge._

//A bridge that manages the i/o interaction between repl and web terminal. It will create the repl if needed. 
class IOBridge(taskRunner: TaskRunner, parameters_to_bind: Seq[NamedParam], factory: PipedReplFactory, val max_lines_kept_in_repl_output_cache: Int = 32, val repl_max_idle_time_in_seconds: Int = 60 * 1) {

  private val actor = new IOBridgeActor(max_lines_kept_in_repl_output_cache, IOBridge.this, factory)
  def !(msg: Any) = {
    taskRunner.post(new Runnable() {
      def run() { safeOp { actor.receive(msg) } }
    })
  }

  def scheduleFuzzily(msg: Any, delayInSeconds: Int) {
    taskRunner.scheduleFuzzily(new Runnable() {
      def run() { safeOp { actor.receive(msg) } }
    }, delayInSeconds)
  }

  val writer = new Writer {

    override def write(cbuf: Array[Char], off: Int, len: Int) {
      val copied = Array.fill[Char](len)(0)
      System.arraycopy(cbuf, off, copied, 0, len)
      IOBridge.this ! Write(copied, 0, len)
    }
    override def close() {}
    override def flush() {}

  }

  taskRunner.registerOnTermination {
    actor.shutdown()
  }

  import scala.tools.nsc.interpreter.NamedParamClass
  val println_to_wrepl: NamedParamClass = NamedParamClass("println_to_wrepl", "Any => Unit", (x: Any) => writer.write(x.toString))
  val runtime_mxbean: NamedParamClass = NamedParamClass("runtime_mxbean", "java.lang.management.RuntimeMXBean", java.lang.management.ManagementFactory.getRuntimeMXBean())
  val parameters = runtime_mxbean +: println_to_wrepl +: parameters_to_bind
}

private[repl] class IOBridgeActor(maxKept: Int = 10, bridge: IOBridge, factory: PipedReplFactory) {

  private val io_cache: EvictingQueue[String] = EvictingQueue.create(maxKept + 1)
  private val clear_repl_io_cache = NamedParamClass("clear_repl_io_cache", "() => Unit", () => { io_cache.clear(); })

  private def write_repl_output(channel: WebSocketChannel, s: String) = {
    channel.writeString(IOBridge.turn2ClientCmdJsString("repl-output", "msg" -> s))
  }

  private def write_bindings_to_client(channel: WebSocketChannel, parameters: NamedParam*) = {
    write_repl_output(channel, s"${bridge.parameters.length + 1} imported parameters those can be used in the interaction.")
    val numbered = bridge.parameters zip (1 to bridge.parameters.size)

    numbered.foreach { p =>
      write_repl_output(channel, IOBridge.formatNamedParam(p._2, p._1))
    }
    write_repl_output(channel, IOBridge.formatNamedParam(bridge.parameters.size + 1, clear_repl_io_cache))
  }

  private var channel = Option.empty[WebSocketChannel]
  private var pipe = Option.empty[OutputStream]
  private var repl = Option.empty[PipedRepl]
  private var completer: Completion.ScalaCompleter = Completion.NullCompleter

  private var connection_behavior_id = 1024

  private val NEW_LINE_BYTES = "\n".getBytes("utf-8")

  protected[wrepl] def shutdown() {

    io_cache.clear()
    pipe.map { _.close() }
    pipe = None
    repl = None
    completer = Completion.NullCompleter

    connection_behavior_id = -1

  }

  import woshilaiceshide.wrepl.util.Utility._

  val receive: PartialFunction[Any, Unit] = {

    case Connect(channel, false) if this.channel != None && this.channel != Some(channel) => {
      channel.writeString(IOBridge.turn2ClientCmdJsString("unconnected", "cause" -> "connected_by_others"))
      channel.close()
    }

    case Connect(channel, force) => {
      this.channel.map { c =>
        c.writeString(IOBridge.turn2ClientCmdJsString("kicked"))
        c.close()
      }

      this.channel = Some(channel)
      connection_behavior_id = connection_behavior_id + 1
      if (repl.isEmpty) {

        val piped_input = new PipedInputStream(128)
        val piped_output = new PipedOutputStream()
        piped_output.connect(piped_input)
        val reader = new InputStreamReader(piped_input)

        //TODO hard coded right currently. user should be retrieved from the http requests in the future. 
        repl = Some(factory(reader, bridge.writer, "root"))
        io_cache.iterator().asScala.foreach { x => channel.writeString(x) }
        repl.map {
          _.loop(true,
            after_initialized = Some(x => {

              bridge ! IOBridge.RegisterPipeAndCompleter(x, piped_output, x.repl.in.completion.completer())

              //x.out.write("binding parameters")
              bridge.parameters.foreach {
                p => x.repl.beQuietDuring(x.repl.bind(p))
                //p => x.repl.bind(p)
              }
              x.repl.beQuietDuring(x.repl.bind(clear_repl_io_cache))

              write_bindings_to_client(channel, (clear_repl_io_cache +: bridge.parameters): _*)
              write_repl_output(channel, "~~~")
              write_repl_output(channel, repl.get.welcome_msg)
              channel.writeString(IOBridge.turn2ClientCmdJsString("connected"))

            }),
            after_stopped = Some(x => {
              x.close()
              bridge ! IOBridge.ReplDied(x)
            }))
        }
      } else {

        write_bindings_to_client(channel, (clear_repl_io_cache +: bridge.parameters): _*)
        write_repl_output(channel, "~~~")
        write_repl_output(channel, repl.get.welcome_msg)

        if (io_cache.remainingCapacity() == 0) {
          write_repl_output(channel, "......")
          io_cache.iterator().asScala.drop(1).foreach { x => channel.writeString(x) }
        } else {
          io_cache.iterator().asScala.foreach { x => channel.writeString(x) }
        }

        channel.writeString(IOBridge.turn2ClientCmdJsString("connected"))
      }
    }

    case Disconnect(channel) => {
      if (this.channel == Some(channel)) {
        this.channel = None
        bridge.scheduleFuzzily(CheckDisconnection(connection_behavior_id), bridge.repl_max_idle_time_in_seconds)
      }
    }

    case ReplDied(repl) if this.repl == Some(repl) => {
      this.channel.map { c =>
        c.writeString(IOBridge.turn2ClientCmdJsString("internal-server-error"))
        val code = woshilaiceshide.sserver.http.WebSocket13.CloseCode.INTERNAL_SERVER_ERROR
        c.close(Some(code))
      }
      io_cache.clear()
      pipe.map { _.close() }
      pipe = None
      this.repl = None
      completer = Completion.NullCompleter
    }

    case CheckDisconnection(id) => {
      if (connection_behavior_id == id) {
        io_cache.clear()
        pipe.map { _.close() }
        pipe = None
        repl = None
        completer = Completion.NullCompleter
      }
    }

    case RegisterPipeAndCompleter(repl, output1, completer1) if this.repl == Some(repl) => {
      pipe = Some(output1)
      completer = completer1
      channel.map { _.writeString(IOBridge.turn2ClientCmdJsString("connected")) }

    }

    case Write(cbuf, off, len) => {
      val js = JsObject("cmd" -> JsString("repl-output"), "msg" -> JsString(new String(cbuf, off, len)))
      val s = js.compactPrint
      io_cache.add(s)
      channel.map { _.writeString(s) }
    }

    case ClientInput(s, c) => {

      val input = s.parseJson
      input.str("cmd") match {
        case Some("connect") => {
          input.bool("force") map { x =>
            bridge ! IOBridge.Connect(c, x)
          }
        }

        case Some("user-input") if Some(c) == channel => {
          input.str("msg") map { x =>
            pipe.map { in =>
              io_cache.add(s)
              in.write(x.getBytes("utf-8"))
              in.write(NEW_LINE_BYTES)
              in.flush()
            }
          }
        }
        case Some("completion") if Some(c) == channel => {

          def writeCompletion(candidates: Completion.Candidates, prefix: String, prev_cursor: Int, channel: WebSocketChannel) = {
            val cur_cursor = candidates.cursor

            val js = JsObject("cmd" -> JsString("completion"),
              "prefix" -> JsString(prefix),
              "prev_cursor" -> JsNumber(prev_cursor),
              "cur_cursor" -> JsNumber(cur_cursor),
              "completion" -> JsArray(candidates.candidates.map(JsString(_)).toVector))
            val s = js.compactPrint
            //DO NOT cache completion
            //io_cache.add(s)
            channel.writeString(s)
          }

          val completer1 = completer
          input.str("prefix") match {
            case Some(prefix) if (null != completer1) => {
              val prev_cursor = prefix.length()
              val candidates = completer1.complete(prefix, prefix.length())
              writeCompletion(candidates, prefix, prefix.length(), c)
            }
            case _ => {}
          }

        }
        case x =>
      }

    }

    case x =>
  }

}