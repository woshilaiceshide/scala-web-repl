package woshilaiceshide.wrepl.repl

import woshilaiceshide.sserver.nio._
import woshilaiceshide.sserver.http._
import woshilaiceshide.sserver.http.WebSocket13
import WebSocket13.WSText
import spray.can._
import spray.http._
import spray.http.HttpEntity.apply
import spray.http.StatusCode.int2StatusCode

object HttpServer {

  trait AuthenticationOffice {
    //return user's name if authenticated, otherwise none.
    def authenticate(request: HttpRequest): Option[String]
  }

}

import HttpServer._

class HttpServer(interface: String, port: Int, born: TaskRunner => IOBridge, office: AuthenticationOffice) extends HttpChannelHandler {

  private val taskRunner = new TaskRunner() {
    def post(runnable: Runnable) {
      server.post_to_io_thread(runnable)
    }
    def scheduleFuzzily(task: Runnable, delayInSeconds: Int) {
      server.schedule_fuzzily(task, delayInSeconds)
    }
    def registerOnTermination[T](code: => T): Boolean = {
      server.register_on_termination(code)
    }
  }

  def build_default_wrepl_websocket_handler(request: HttpRequest): WebSocketChannel => WebSocketChannelHandler = c => {

    val bridge = born(taskRunner)

    c.tryAccept(request, Nil, Nil)
    new WebSocketChannelHandler() {

      def inputEnded() = {
        c.close(WebSocket13.CloseCode.NORMAL_CLOSURE_OPTION)
      }

      def channelWritable() {}
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

  def channelClosed(channel: HttpChannel): Unit = {}

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

  private def copy_cookies(to_response: HttpResponse, from_request: HttpRequest, exculde: String*) = {

  }
  private def temporary_redirect(url: String) = {

  }
  private def to_login_cookie(user: String, md5: String) = {

  }

  import spray.json._
  import woshilaiceshide.wrepl.util.Utility._
  private val ASSET_PATH = Uri.Path("/asset/")
  def requestReceived(request: HttpRequest, channel: HttpChannel, classifier: RequestClassifier): ResponseAction = request match {
    case HttpRequest(HttpMethods.GET, path, _, _, _) if path.path.startsWith(ASSET_PATH) =>
      val response = fromResource(path.path.toString())
      channel.writeResponse(response, size_hint = response.entity.data.length.toInt + 1024 * 4, write_server_and_date_headers = true)
      ResponseAction.responseNormally

    case x @ HttpRequest(HttpMethods.POST, Uri.Path("/login"), HttpCharsets.`UTF-8`, _, _) =>
      channel.writeResponse(new HttpResponse(404), write_server_and_date_headers = true)
      ResponseAction.responseNormally

    case x @ HttpRequest(HttpMethods.POST, Uri.Path("/logout"), HttpCharsets.`UTF-8`, _, _) =>
      channel.writeResponse(new HttpResponse(404), write_server_and_date_headers = true)
      ResponseAction.responseNormally

    case x @ HttpRequest(HttpMethods.GET, Uri.Path("/wrepl"), _, _, _) =>
      ResponseAction.acceptWebsocket { build_default_wrepl_websocket_handler(x) }

    case _: HttpRequest =>
      channel.writeResponse(new HttpResponse(404), write_server_and_date_headers = true)
      ResponseAction.responseNormally

    case _ =>
      //I DOES NOT support chunked request.
      channel.writeResponse(new HttpResponse(400), write_server_and_date_headers = true)
      ResponseAction.responseNormally
  }

  private val http_configurator = new HttpConfigurator(max_request_in_pipeline = 8, use_direct_byte_buffer_for_cached_bytes_rendering = false)

  protected val factory = new HttpChannelHandlerFactory(this, http_configurator)

  val listening_channel_configurator: ServerSocketChannelWrapper => Unit = wrapper => {
    wrapper.setOption[java.lang.Boolean](java.net.StandardSocketOptions.SO_REUSEADDR, true)
    wrapper.setBacklog(1024 * 8)
  }

  val accepted_channel_configurator: SocketChannelWrapper => Unit = wrapper => {
    wrapper.setOption[java.lang.Boolean](java.net.StandardSocketOptions.TCP_NODELAY, true)
  }

  val configurator = XNioConfigurator(
    count_for_reader_writers = 0,
    enable_fuzzy_scheduler = true,
    listening_channel_configurator = listening_channel_configurator,
    accepted_channel_configurator = accepted_channel_configurator,
    max_bytes_waiting_for_written_per_channel = 128 * 1024)

  protected def default_server: SelectorRunner = NioSocketServer(
    interface,
    port,
    factory,
    configurator)

  lazy val server = default_server

  def start(asynchronously: Boolean) = {
    server.start(asynchronously)
  }

  def stop(timeout: Int) = {
    server.stop(timeout)
  }

}