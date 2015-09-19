# Scala-Web-REPL
Scala-Web-REPL uses a web terminal as the interactive console. Especially, it can be used as :
 
* an inspector for running applications 
* a command line interface for running applications, which will save your time writing a dedicated interface for its management. 
* a hot code modification mechanism

## How does It Work?
It uses a scala interpreter to execute codes internally. If NO connected clients, this internal interpreter will be closed after 60 seconds(configurable). When you typed in the terminal, the lines will be transmitted to the running application, got compiled, and executed finnaly.  

## Show
![image](https://raw.githubusercontent.com/woshilaiceshide/scala-web-repl/master/scala-web-repl.jpg)

## The Http Server
After searching a small http server for several days, I planned to write one for Scala-Web-REPL, which is named s-server. You can see it on https://github.com/woshilaiceshide/s-server . 

At the very beginning, s-server is intended for a dedicated server for this project, which helps saving resource and simplifing development. But now it's a general nio socket server and a general http server, and will evolve independently. 

## How to Build It?
If you can NOT access my maven repository https://dl.bintray.com/woshilaiceshide/maven/ , please build from source codes as below: 

1.
build s-server, which provides `'HTTP'`, from https://github.com/woshilaiceshide/s-server . Maybe you can just `'publishLocal'` for test.
 
2.
build Scala-Web-REPL
* git clone https://github.com/woshilaiceshide/scala-web-repl.git
* cd ./scala-web-repl
* sbt package

## How to Run it?
1.
run it directly in sbt, using `'sbt run'`.

Note that Scala-Web-REPL can not run in the same jvm as sbt. It should be forked into another jvm. Write `'fork := true'` in build.sbt or issue `'set fork := true'` in sbt's console.

2.
run it in other applications, add Scala-Web-REPL as a dependency, then follow the example codes in https://github.com/woshilaiceshide/scala-web-repl#code-example .

3.
run it as a java agent as below: 

	java -javaagent:/path/to/scala-web-repl_2.11-${version}.jar" -Dwrepl.listen.address=0.0.0.0 -Dwrepl.listen.port=8484 -cp ${classpath} ${main_class}

Or if you use sbt-native-packager, then add the following into build.sbt: 

	resolvers += "Woshilaiceshide Releases" at "http://dl.bintray.com/woshilaiceshide/maven/"

	libraryDependencies += "woshilaiceshide" %% "scala-web-repl" % "1.0" withSources()
	
	bashScriptExtraDefines += """addJava "-javaagent:${lib_dir}/woshilaiceshide.scala-web-repl_2.11-1.0.jar""""
	bashScriptExtraDefines += """addJava "-Dwrepl.listen.address=0.0.0.0""""
	bashScriptExtraDefines += """addJava "-Dwrepl.listen.port=8484""""

Note: 
* If `'wrepl.listen.address'` is not specified, it will be `'0.0.0.0'`, and `'wrepl.listen.port'` defaults to `'8484'`.
* If it used as a java agent, you can not bind parameters to repl, SO, just keep the objects you want to manipulate in the web repl in some `'Scala Objects'` or `'static fields of some Java Objects'`.

## How to Manipulate It?
After started, browse http://${host}:${port}/asset/wrepl.html . Type scala expressions in the terminal, which will be executed in the remote jvm.

Make sure that your browser supports javascript and WebSocket(v13).

## Code Example

	package woshilaiceshide.wrepl
	
	case class Cat(color: String, age: Int) {
	  def mewl = s"mewling at ${System.currentTimeMillis()}"
	}
	
	object DefaultBootstrap extends App {
	
	  //will be imported to repl's session
	  val whiteCat = Cat("white", 3)
	  //will be imported to repl's session
	  val blackCat = Cat("black", 10)
	
	  //will be imported to repl's session
	  def who_is_older(a: Cat, b: Cat) = {
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
	    Seq(NamedParam("whiteCat", whiteCat),
	      NamedParam("blackCat", blackCat),
	      NamedParamClass("who_is_older", "(woshilaiceshide.wrepl.Cat, woshilaiceshide.wrepl.Cat) => Option[woshilaiceshide.wrepl.Cat]", who_is_older _)),
	    max_lines_kept_in_repl_output_cache = max_lines_kept_in_repl_output_cache,
	    repl_max_idle_time_in_seconds = repl_max_idle_time_in_seconds)
	
	  //after started, open your brower and go to http://${host}:${port}/asset/wrepl.html , then do what you want to do.
	  server.start()
	}


## Builtin Parameters
Paramters are those object imported into scala's repl, and you can use it in your interaction.

Besides your own imported paramters, Scala-Web-REPL import 4 other parameters automatically: 

1.
**runtime_mxbean**, which is of type `'java.lang.management.RuntimeMXBean'`, and it will provides some simple system inforamtion.

2.
**println_to_wrepl**, which is of type `'Any => Unit'`, and it can instruct your codes to print on Scala-Web-REPL.

3.
**get_http_server**, which is of type `'woshilaiceshide.wrepl.repl.HttpServer'`, and it is the server that listen for your connection. I import this parameter just for no reason, and it will be removed in some future's version.

4.
**clear_repl_io_cache**, which is of type `'() => Unit'`, and it can clear Scala-Web-REPL's io cache. Scala-Web-REPL will stay for one minute(configurable) after the user disconnected, and the user's input and repl's output are also cached, so your previous work will be there for one minute(maybe longer if you configured). If you reconnected, the previous I/O will re-print on your screen. Use `'clear_repl_io_cache'` to clear this cache. 


## Utilities for Akka & Hot Code Modification Example
Scala-Web-REPL provides some simple utilities for manipulation on akka's actors in running applications. The following example is from https://github.com/woshilaiceshide/scala-web-repl/blob/master/src/test/scala/woshilaiceshide/wrepl/AkkaHouse.scala : 

	package woshilaiceshide.wrepl

	import akka.actor._
	import woshilaiceshide.wrepl.util.Utility
	
	object AkkaHouse {
	
	  class RawPrinter extends Actor {
	    override def receive = {
	      case x => println(s"""I'm printing: ${x}""")
	    }
	  }
	
	  //Utility.RestorableActor makes it posssible that you can modify Printer's behavior and read its internal status. 
	  class Printer extends RawPrinter with Utility.RestorableActor
	
	  val config = com.typesafe.config.ConfigFactory.load()
	  val system = ActorSystem("akka-house", config)
	  val printer = system.actorOf(Props(classOf[Printer]), "printer")
	
	}

After it is launched, type the following expressions in the connected Scala-Web-REPL line by line except the comments, and watch the screen: 

	import akka.actor.Actor
	import akka.pattern.ask
	import woshilaiceshide.wrepl.util.AkkaUtility
	
	//the actor that we'll operate on
	val printer = woshilaiceshide.wrepl.AkkaHouse.printer
	
	val new_behavior = new AkkaUtility.ReceiveWrapper {
	def aroundReceive(receive: Actor.Receive, msg: Any, superAroundReceive: (Actor.Receive, Any) => Unit): Unit = {
	println(s"""I received a msg: ${msg}, and begin to process...""")
	superAroundReceive(receive, msg)
	println(s"""process finished""")
	}
	}
	//make it happen
	printer ! new_behavior
	
	//test
	printer ! "ruok"
	
	//restore the actor's original behavior
	printer ! AkkaUtility.Restore
	
	//retieve the actor's hash code
	val actor_inspector = new AkkaUtility.ActorProcessor { def process(x: Actor) { println_to_wrepl(x.hashCode); } }
	//make it happen
	printer ! actor_inspector

You'll see how you controlled the actor in the running application.

## TODO
* I'll write some utilities to help introspection for applications based on akka, spray and play. Any interested guy is appreciated.

* How to forbid `'System.exit(-1)'`, or do more sophisticated restrictions?

* Will some jvm options make Scala-Web-REPL sad?

* Dynamic class load

* Logging

## FAQ
1.
**How to customize the web ui?**

You can override the `'woshilaiceshide.wrepl.repl.HttpServer'` to provide your own `'/asset/extra.css'` and other necessary codes.

2.
**How to authenticate clients?**

I leave this for you! Override `'woshilaiceshide.wrepl.repl.HttpServer'` to implement the authentication.

3.
**What scala versions does it support?**

Only scala-2.11.7 is tested. May be scala-2.11.x is OK, but I give no promise.

4.
**How to make the internal scala interpreter stopped right now, without waiting for one minute?**

Just type the power command `':quit'` in the repl, then ENTER.

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
