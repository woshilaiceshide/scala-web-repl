package woshilaiceshide.wrepl

case class Cat(color: String, age: Int) {
  def mewl = s"mewling at ${System.currentTimeMillis()}"
}

object DefaultBootstrap extends App {

  //will be imported to repl's session
  val white_cat = Cat("white", 3)
  //will be imported to repl's session
  val black_cat = Cat("black", 10)

  //will be imported to repl's session
  def which_cat_is_older(a: Cat, b: Cat) = {
    if (a.age > b.age) Some(a)
    else if (a.age < b.age) Some(b)
    else None
  }

  import scala.tools.nsc.interpreter._

  val config = com.typesafe.config.ConfigFactory.load().getConfig("scala-web-repl")
  val max_lines_kept_in_repl_output_cache = config.getInt("max_lines_kept_in_repl_output_cache")
  val repl_max_idle_time_in_seconds = config.getInt("repl_max_idle_time_in_seconds")
  val interface = config.getString("interface")
  val port = config.getInt("port")

  val server = new Server(
    interface,
    port,
    //these named parameters will be imported to repl'session, so you can operate on them directly.
    Seq(NamedParam("white_cat", white_cat),
      NamedParam("black_cat", black_cat),
      NamedParamClass("which_cat_is_older", "(woshilaiceshide.wrepl.Cat, woshilaiceshide.wrepl.Cat) => Option[woshilaiceshide.wrepl.Cat]", which_cat_is_older _)),
    max_lines_kept_in_repl_output_cache = max_lines_kept_in_repl_output_cache,
    repl_max_idle_time_in_seconds = repl_max_idle_time_in_seconds)

  //after started, open your brower and go to http://${host}:${port}/asset/wrepl.html , then do what you want to do.
  server.start()
}