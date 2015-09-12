package woshilaiceshide.wrepl.repl

import spray.json._
import spray.json.DefaultJsonProtocol._

import com.google.common.collect.EvictingQueue

import spray.can.WebSocketChannelWrapper

import scala.collection.JavaConverters._

import scala.tools.nsc.interpreter.NamedParam
import scala.tools.nsc.interpreter.NamedParamClass
import scala.tools.nsc.interpreter.Completion
import scala.tools.nsc.interpreter.NoCompletion

object Bridge {

  case class Connect(channel: WebSocketChannelWrapper, force: Boolean)
  case class Disconnect(channel: WebSocketChannelWrapper)
  private[repl] case class CheckDisconnection(connection_behavior_id: Int)
  private[repl] case class ReplDied(repl: PipedRepl)

  case class RegisterPipeAndCompleter(repl: PipedRepl, output: java.io.OutputStream, completer: Completion.ScalaCompleter)

  case class Write(cbuf: Array[Char], off: Int, len: Int)
  case class ClientInput(s: String, channel: WebSocketChannelWrapper)

  import spray.json._
  implicit class JsonHelper(raw: spray.json.JsValue) {

    def str(key: String): Option[String] = raw match {
      case JsObject(fields) => fields(key) match {
        case JsString(v) => Some(v)
        case _           => None
      }
      case _ => None
    }

    def bool(key: String): Option[Boolean] = raw match {
      case JsObject(fields) => fields(key) match {
        case JsBoolean(v) => Some(v)
        case _            => None
      }
      case _ => None
    }

  }

  def turn2String(cmd: String, fields: (String, String)*) = {
    val js = JsObject(("cmd" -> JsString(cmd)) +: fields.map { x => (x._1, JsString(x._2)) }: _*)
    js.compactPrint
  }

  def formatNamedParam(p: NamedParam) = s"""${p.name}: ${p.tpe} = ${p.value}"""

}

import Bridge._

class Bridge(taskRunner: TaskRunner, val parameters: Seq[NamedParam], born: Bridge => PipedRepl, val max_lines_kept_in_repl_output_cache: Int = 32, val repl_max_idle_time_in_seconds: Int = 60 * 1) {

  private val actor = new BridgeActor(max_lines_kept_in_repl_output_cache, Bridge.this, born)
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

  val writer = new java.io.Writer {

    override def write(cbuf: Array[Char], off: Int, len: Int) {
      val copied = Array.fill[Char](len)(0)
      System.arraycopy(cbuf, off, copied, 0, len)
      Bridge.this ! Bridge.Write(copied, 0, len)
    }
    override def close() {}
    override def flush() {}

  }

  taskRunner.registerOnTermination {
    actor.shutdown()
  }
}

private[repl] class BridgeActor(maxKept: Int = 10, bridge: Bridge, born: Bridge => PipedRepl) {

  private val io_cache: EvictingQueue[String] = EvictingQueue.create(maxKept + 1)
  private val clear_repl_io_cache = NamedParamClass("clear_repl_io_cache", "() => Unit", () => { io_cache.clear(); })

  private def write_repl_output(channel: WebSocketChannelWrapper, s: String) = {
    channel.writeString(Bridge.turn2String("repl-output", "msg" -> s))
  }

  private def write_bindings_to_client(channel: WebSocketChannelWrapper, parameters: NamedParam*) = {
    write_repl_output(channel, s"${bridge.parameters.length + 1} parameter(s) bound")
    bridge.parameters.foreach { p =>
      write_repl_output(channel, Bridge.formatNamedParam(p))
    }
    write_repl_output(channel, Bridge.formatNamedParam(clear_repl_io_cache))
  }

  private var channel = Option.empty[WebSocketChannelWrapper]
  private var pipe = Option.empty[java.io.OutputStream]
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

  val receive: PartialFunction[Any, Unit] = {

    case Connect(channel, false) if this.channel != None && this.channel != Some(channel) => {
      channel.writeString(Bridge.turn2String("unconnected", "cause" -> "connected_by_others"))
      channel.close()
    }

    case Connect(channel, force) => {
      this.channel.map { c =>
        c.writeString(Bridge.turn2String("kicked"))
        c.close()
      }

      this.channel = Some(channel)
      connection_behavior_id = connection_behavior_id + 1
      if (repl.isEmpty) {
        repl = Some(born(bridge))
        io_cache.iterator().asScala.foreach { x => channel.writeString(x) }
        repl.map {
          _.loop(true,
            after_initialized = Some(x => {

              bridge ! Bridge.RegisterPipeAndCompleter(x, x.pipedOutputStream, x.repl.in.completion.completer())

              //x.out.write("binding parameters")
              bridge.parameters.foreach {
                p => x.repl.beQuietDuring(x.repl.bind(p))
                //p => x.repl.bind(p)
              }
              x.repl.beQuietDuring(x.repl.bind(clear_repl_io_cache))

              write_bindings_to_client(channel, (clear_repl_io_cache +: bridge.parameters): _*)
              write_repl_output(channel, "~~~")
              write_repl_output(channel, repl.get.welcome_msg)
              channel.writeString(Bridge.turn2String("connected"))

            }),
            after_stopped = Some(x => {
              x.close()
              bridge ! Bridge.ReplDied(x)
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

        channel.writeString(Bridge.turn2String("connected"))
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
        c.writeString(Bridge.turn2String("internal-server-error"))
        val code = woshilaiceshide.sserver.httpd.WebSocket13.CloseCode.INTERNAL_SERVER_ERROR
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
      channel.map { _.writeString(Bridge.turn2String("connected")) }

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
            bridge ! Bridge.Connect(c, x)
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

          def writeCompletion(candidates: Completion.Candidates, prefix: String, prev_cursor: Int, channel: WebSocketChannelWrapper) = {
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