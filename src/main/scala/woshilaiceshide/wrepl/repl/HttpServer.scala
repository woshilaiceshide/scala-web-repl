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

class HttpServer(interface: String, port: Int, born: Bridge.TaskRunner => Bridge) {

  private val taskRunner = new Bridge.TaskRunner() {
    def post(runnable: Runnable) {
      server.post(runnable)
    }
    def scheduleFuzzily(task: Runnable, delayInSeconds: Int) {
      server.scheduleFuzzily(task, delayInSeconds)
    }
  }
  val bridge = born(taskRunner)

  def build_default_wrepl_websocket_handler: WebSocketChannelWrapper => WebSocketChannelHandler = c => {

    new WebSocketChannelHandler() {

      def inputEnded() = {
        c.close(WebSocket13.CloseCode.NORMAL_CLOSURE_OPTION)
      }

      def becomeWritable() {}
      def pongReceived(frame: WebSocket13.WSFrame): Unit = {}

      def frameReceived(frame: WebSocket13.WSFrame): Unit = {
        import WebSocket13._
        frame match {
          case x: WSText => bridge ! Bridge.ClientInput(x.text, c)
          case _         => c.close(Some(WebSocket13.CloseCode.CAN_NOT_ACCEPT_THE_TYPE_OF_DATA))
        }
      }
      def fireClosed(code: WebSocket13.CloseCode.Value, reason: String): Unit = {
        bridge ! Bridge.Disconnect(c)
      }
    }

  }

  def handler = new PlainHttpChannelHandler {

    def channelClosed(channel: HttpChannelWrapper): Unit = {

    }

    private def fromResource(path: String) = {
      val stream = this.getClass.getResourceAsStream(path)
      val count = stream.available()
      val bytes = com.google.common.io.ByteStreams.toByteArray(stream)
      stream.close()
      bytes
    }

    def requestReceived(request: HttpRequestPart, channel: HttpChannelWrapper): HttpRequestProcessor = request match {
      case HttpRequest(HttpMethods.GET, Uri.Path("/asset/websocket.html"), _, _, _) =>
        channel.respond {
          import spray.http._
          val ct = ContentType(MediaTypes.`text/html`, HttpCharsets.`UTF-8`)
          val entity = HttpEntity(ct, fromResource("/asset/websocket.html"))
          new HttpResponse(200, entity)
        }
      case HttpRequest(HttpMethods.GET, Uri.Path("/asset/wrepl.html"), _, _, _) =>
        channel.respond {
          import spray.http._
          val ct = ContentType(MediaTypes.`text/html`, HttpCharsets.`UTF-8`)
          val entity = HttpEntity(ct, fromResource("/asset/wrepl.html"))
          new HttpResponse(200, entity)
        }
      case HttpRequest(HttpMethods.GET, Uri.Path("/asset/jquery.console.js"), _, _, _) =>
        channel.respond {
          import spray.http._
          val ct = ContentType(MediaTypes.`application/javascript`, HttpCharsets.`UTF-8`)
          val entity = HttpEntity(ct, fromResource("/asset/jquery-console/jquery.console.js"))
          new HttpResponse(200, entity)
        }
      case HttpRequest(HttpMethods.GET, Uri.Path("/asset/jquery-2.1.4.min.js"), _, _, _) =>
        channel.respond {
          import spray.http._
          val ct = ContentType(MediaTypes.`application/javascript`, HttpCharsets.`UTF-8`)
          val entity = HttpEntity(ct, fromResource("/asset/jquery-2.1.4.min.js"))
          new HttpResponse(200, entity)
        }
      case HttpRequest(HttpMethods.GET, Uri.Path("/asset/json2.js"), _, _, _) =>
        channel.respond {
          import spray.http._
          val ct = ContentType(MediaTypes.`application/javascript`, HttpCharsets.`UTF-8`)
          val entity = HttpEntity(ct, fromResource("/asset/json2.js"))
          new HttpResponse(200, entity)
        }
      case HttpRequest(HttpMethods.GET, Uri.Path("/asset/demo.html"), _, _, _) =>
        channel.respond {
          import spray.http._
          val ct = ContentType(MediaTypes.`text/html`, HttpCharsets.`UTF-8`)
          val entity = HttpEntity(ct, fromResource("/asset/jquery-console/demo.html"))
          new HttpResponse(200, entity)
        }
      case x @ HttpRequest(HttpMethods.GET, Uri.Path("/wrepl"), _, _, _) =>
        channel.toWebSocketChannelHandler(x, Nil, 1024, build_default_wrepl_websocket_handler)
      case _: HttpRequest => {
        channel.respond { new HttpResponse(404) }
      }
      case _ => {
        //I DOES NOT support chunked request.
        channel.respond { new HttpResponse(404) }
      }
    }
  }
  protected val factory = new HttpChannelHandlerFactory(handler, 8)
  protected def default_server: NioSocketServer = new NioSocketServer(interface, port, factory, max_bytes_waiting_for_written_per_channel = 128 * 1024, enable_fuzzy_scheduler = true)

  lazy val server = default_server

  def start(asynchronously: Boolean) = {
    server.start(asynchronously)
  }

  def stop(timeout: Int) = {
    server.stop(timeout)
  }

}