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

class HttpServer(interface: String, port: Int, born: TaskRunner => IOBridge) extends PlainHttpChannelHandler {

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
    case HttpRequest(HttpMethods.GET, Uri.Path("/asset/login.html"), _, _, _) =>
      channel.respond {
        import spray.http._
        val ct = ContentType(MediaTypes.`text/html`, HttpCharsets.`UTF-8`)
        val entity = HttpEntity(ct, fromResource("/asset/login.html"))
        new HttpResponse(200, entity)
      }
    case HttpRequest(HttpMethods.GET, Uri.Path("/asset/default.css"), _, _, _) =>
      channel.respond {
        import spray.http._
        val ct = ContentType(MediaTypes.`text/css`, HttpCharsets.`UTF-8`)
        val entity = HttpEntity(ct, fromResource("/asset/default.css"))
        new HttpResponse(200, entity)
      }
    case HttpRequest(HttpMethods.GET, Uri.Path("/asset/extra.css"), _, _, _) =>
      channel.respond {
        import spray.http._
        val ct = ContentType(MediaTypes.`text/css`, HttpCharsets.`UTF-8`)
        val entity = HttpEntity(ct, HttpData.Empty)
        new HttpResponse(200, entity)
      }
    case HttpRequest(HttpMethods.GET, Uri.Path("/asset/jquery-ui.min.css"), _, _, _) =>
      channel.respond {
        import spray.http._
        val ct = ContentType(MediaTypes.`text/css`, HttpCharsets.`UTF-8`)
        val entity = HttpEntity(ct, fromResource("/asset/jquery-ui.min.css"))
        new HttpResponse(200, entity)
      }
    case HttpRequest(HttpMethods.GET, Uri.Path("/asset/jquery-ui.structure.min.css"), _, _, _) =>
      channel.respond {
        import spray.http._
        val ct = ContentType(MediaTypes.`text/css`, HttpCharsets.`UTF-8`)
        val entity = HttpEntity(ct, fromResource("/asset/jquery-ui.structure.min.css"))
        new HttpResponse(200, entity)
      }
    case HttpRequest(HttpMethods.GET, Uri.Path("/asset/jquery-ui.theme.min.css"), _, _, _) =>
      channel.respond {
        import spray.http._
        val ct = ContentType(MediaTypes.`text/css`, HttpCharsets.`UTF-8`)
        val entity = HttpEntity(ct, fromResource("/asset/jquery-ui.theme.min.css"))
        new HttpResponse(200, entity)
      }
    case HttpRequest(HttpMethods.GET, Uri.Path("/asset/jquery.console.js"), _, _, _) =>
      channel.respond {
        import spray.http._
        val ct = ContentType(MediaTypes.`application/javascript`, HttpCharsets.`UTF-8`)
        val entity = HttpEntity(ct, fromResource("/asset/jquery-console/jquery.console.js"))
        new HttpResponse(200, entity)
      }
    case HttpRequest(HttpMethods.GET, Uri.Path("/asset/jquery-2.2.3.min.js"), _, _, _) =>
      channel.respond {
        import spray.http._
        val ct = ContentType(MediaTypes.`application/javascript`, HttpCharsets.`UTF-8`)
        val entity = HttpEntity(ct, fromResource("/asset/jquery-2.2.3.min.js"))
        new HttpResponse(200, entity)
      }
    case HttpRequest(HttpMethods.GET, Uri.Path("/asset/json2.js"), _, _, _) =>
      channel.respond {
        import spray.http._
        val ct = ContentType(MediaTypes.`application/javascript`, HttpCharsets.`UTF-8`)
        val entity = HttpEntity(ct, fromResource("/asset/json2.js"))
        new HttpResponse(200, entity)
      }
    case HttpRequest(HttpMethods.GET, Uri.Path("/asset/jquery-ui.min.js"), _, _, _) =>
      channel.respond {
        import spray.http._
        val ct = ContentType(MediaTypes.`application/javascript`, HttpCharsets.`UTF-8`)
        val entity = HttpEntity(ct, fromResource("/asset/jquery-ui.min.js"))
        new HttpResponse(200, entity)
      }
    case HttpRequest(HttpMethods.GET, Uri.Path("/asset/demo.html"), _, _, _) =>
      channel.respond {
        import spray.http._
        val ct = ContentType(MediaTypes.`text/html`, HttpCharsets.`UTF-8`)
        val entity = HttpEntity(ct, fromResource("/asset/jquery-console/demo.html"))
        new HttpResponse(200, entity)
      }
    case x @ HttpRequest(HttpMethods.POST, Uri.Path("/login"), HttpCharsets.`UTF-8`, _, _) => {
      channel.respond {
        //x.entity.asString(HttpCharsets.`UTF-8`)
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