# Scala-Web-REPL
Scala-Web-REPL uses a web terminal as the interactive console. Especially, it can be used as a inspector for applications, or even as a hot code modification mechanism.

It uses a scala interpreter to execute codes internally. If NO connected clients, this internal interpreter will be closed after 60 seconds(configurable).  

## Show
![image](https://raw.githubusercontent.com/woshilaiceshide/scala-web-repl/master/scala-web-repl.jpg)

## The Http Server
After searching a small http server for several days, I planned to write one for Scala-Web-REPL, which is named s-server. You can see it on https://github.com/woshilaiceshide/s-server . 

At the very beginning, s-server is intended for a dedicated server for this project, which helps saving resource and simplifing development. But now it's a general nio socket server and a general http server, and will evolve independently. 

## How to Build It?
1.
build s-server, which provides `'HTTP'`, from https://github.com/woshilaiceshide/s-server . Maybe you can just `'publishLocal'` for test.
 
2.
build Scala-Web-REPL
* git clone https://github.com/woshilaiceshide/scala-web-repl.git
* cd ./scala-web-repl
* sbt package

## How to Use It?
After started, browse http://${host}:${port}/asset/wrepl.html

Make sure that your browser supports javascript and WebSocket(v13).

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
	

## TODO
* I'll write some utilities to help introspection for applications based on akka, spray and play. Any interested guy is appreciated.

* How to forbid `'System.exit(-1)'`, or do more sophisticated restrictions?

* Will some jvm options make Scala-Web-REPL sad?

## FAQ
1.
**How to customize the web ui?**

You can override the `'woshilaiceshide.wrepl.repl.HttpServer'` to provide your own `'/asset/extra.css'` and other necessary codes.

2.
**How to authenticate clients?**

I leave this for you! Override `'woshilaiceshide.wrepl.repl.HttpServer'` to implement the authentication.


## Similar Projects
1.
**CRaSH**

http://www.crashub.org/

2.
**liverepl**

https://github.com/djpowell/liverepl

3.
**scalive**

https://github.com/xitrum-framework/scalive

## Enjoy It
Any feedback is expected.