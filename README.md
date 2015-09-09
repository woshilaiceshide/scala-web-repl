# Scala-Web-REPL
Scala-Web-REPL uses a web terminal as the interactive console. Especially, it can be used as a inspector for applications, or even as a hot code modification mechanism.

## Show
![image](https://raw.githubusercontent.com/woshilaiceshide/scala-web-repl/master/scala-web-repl.jpg)

## The Http Server
I wrote both a nio-socket server and a http server for Scala-Web-REPL. You can see it in https://github.com/woshilaiceshide/s-server .

## How to Build It?
1.
build s-server, which provides `'HTTP'`, from https://github.com/woshilaiceshide/s-server . Maybe you can just `'publishLocal'` for test. 
2.
build Scala-Web-REPL
* git clone https://github.com/woshilaiceshide/scala-web-repl.git
* cd ./scala-web-repl
* sbt package

## Code Example

	package woshilaiceshide.wrepl
	
	case class Cat(color: String, age: Int) {
	  def mewl = s"mewling at ${System.currentTimeMillis()}"
	}
	
	object DefaultBootstrap extends App {
	
	  val whiteCat = Cat("white", 3)
	  val blackCat = Cat("black", 10)
	
	  def who_is_older(a: Cat, b: Cat) = {
	    if (a.age > b.age) Some(a)
	    else if (a.age < b.age) Some(b)
	    else None
	  }
	
	  import scala.tools.nsc.interpreter.NamedParam
	  import scala.tools.nsc.interpreter.NamedParamClass
	
	  //val config = com.typesafe.config.ConfigFactory.parseFileAnySyntax(new java.io.File("conf/application.conf"))
	  val config = com.typesafe.config.ConfigFactory.load()
	  val max_lines_kept_in_output_cache = config.getInt("max_lines_kept_in_output_cache")
	  val repl_max_idle_time_in_seconds = config.getInt("repl_max_idle_time_in_seconds")
	
	  val server = new Server(
	    "0.0.0.0",
	    8181,
	    Seq(NamedParam("whiteCat", whiteCat),
	      NamedParam("blackCat", blackCat),
	      NamedParamClass("who_is_older", "(woshilaiceshide.wrepl.Cat, woshilaiceshide.wrepl.Cat) => Option[woshilaiceshide.wrepl.Cat]", who_is_older _)),
	    max_lines_kept_in_output_cache = max_lines_kept_in_output_cache,
	    repl_max_idle_time_in_seconds = repl_max_idle_time_in_seconds)
	
	  server.start()
	}
	
## Enjoy It
Any feedback is expected.