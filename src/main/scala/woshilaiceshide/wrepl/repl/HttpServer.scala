package woshilaiceshide.wrepl.repl

import woshilaiceshide.sserver.httpd._
import woshilaiceshide.sserver.httpd.WebSocket13
import woshilaiceshide.sserver.nio.NioSocketServer
import WebSocket13.WSText
import spray.can._
import spray.http._
import spray.http.HttpEntity.apply
import spray.http.StatusCode.int2StatusCode
import spray.can.WebSocketChannelHandler
import spray.can.PlainHttpChannelHandler
import spray.can.HttpRequestProcessor
import spray.can.HttpChannelWrapper
import spray.can.HttpChannelHandlerFactory

object HttpServer {

  trait AuthenticationOffice {
    //return user's name if authenticated, otherwise oone.
    def authenticate(request: HttpRequest): Option[String]
  }

}

import HttpServer._

class HttpServer(interface: String, port: Int, born: TaskRunner => IOBridge, office: AuthenticationOffice) extends PlainHttpChannelHandler {

  private val taskRunner = new TaskRunner() {
    def post(runnable: Runnable) {
      server.post(runnable)
    }
    def scheduleFuzzily(task: Runnable, delayInSeconds: Int) {
      server.scheduleFuzzily(task, delayInSeconds)
    }
    def registerOnTermination[T](code: => T): Boolean = {
      server.registerOnTermination(code)
    }
  }

  def build_default_wrepl_websocket_handler(): WebSocketChannelWrapper => WebSocketChannelHandler = c => {

    val bridge = born(taskRunner)

    new WebSocketChannelHandler() {

      def inputEnded() = {
        c.close(WebSocket13.CloseCode.NORMAL_CLOSURE_OPTION)
      }

      def becomeWritable() {}
      def pongReceived(frame: WebSocket13.WSFrame): Unit = {}

      def frameReceived(frame: WebSocket13.WSFrame): Unit = {
        import WebSocket13._
        frame match {
          case x: WSText => bridge ! IOBridge.ClientInput(x.text, c)
          case _ => c.close(Some(WebSocket13.CloseCode.CAN_NOT_ACCEPT_THE_TYPE_OF_DATA))
        }
      }
      def fireClosed(code: WebSocket13.CloseCode.Value, reason: String): Unit = {
        bridge ! IOBridge.Disconnect(c)
      }
    }

  }

  def channelClosed(channel: HttpChannelWrapper): Unit = {}

  import spray.http._
  private def fromResource(path: String) = {
    val stream = this.getClass.getResourceAsStream(path)
    if (null != stream) {
      val count = stream.available()
      val bytes = com.google.common.io.ByteStreams.toByteArray(stream)
      stream.close()
      val ct = if (path.endsWith(".html")) {
        ContentType(MediaTypes.`text/html`, HttpCharsets.`UTF-8`)
      } else if (path.endsWith(".css")) {
        ContentType(MediaTypes.`text/css`, HttpCharsets.`UTF-8`)
      } else if (path.endsWith(".js")) {
        ContentType(MediaTypes.`application/javascript`, HttpCharsets.`UTF-8`)
      } else {
        ContentTypes.`application/octet-stream`
      }
      new HttpResponse(200, HttpEntity(ct, HttpData(bytes)))
    } else {
      new HttpResponse(404)
    }

  }

  import spray.json._
  import woshilaiceshide.wrepl.util.Utility._
  private val ASSET_PATH = Uri.Path("/asset/")
  def requestReceived(request: HttpRequestPart, channel: HttpChannelWrapper): HttpRequestProcessor = request match {
    case HttpRequest(HttpMethods.GET, path, _, _, _) if path.path.startsWith(ASSET_PATH) =>
      channel.respond {
        fromResource(path.path.toString())
      }
    case x @ HttpRequest(HttpMethods.POST, Uri.Path("/login"), HttpCharsets.`UTF-8`, _, _) => {
      channel.respond {
        new HttpResponse(404)
      }
    }
    case x @ HttpRequest(HttpMethods.GET, Uri.Path("/wrepl"), _, _, _) =>
      channel.toWebSocketChannelHandler(x, Nil, 1024, build_default_wrepl_websocket_handler())
    case _: HttpRequest =>
      channel.respond {
        new HttpResponse(404)
      }
    case _ => {
      //I DOES NOT support chunked request.
      channel.respond { new HttpResponse(400) }
    }
  }

  protected val factory = new HttpChannelHandlerFactory(this, 8)
  protected def default_server: NioSocketServer = new NioSocketServer(interface, port, factory, max_bytes_waiting_for_written_per_channel = 128 * 1024, enable_fuzzy_scheduler = true)

  lazy val server = default_server

  def start(asynchronously: Boolean) = {
    server.start(asynchronously)
  }

  def stop(timeout: Int) = {
    server.stop(timeout)
  }

}