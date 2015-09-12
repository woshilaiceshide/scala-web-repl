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

## How to Run it?
1.
run it directly in sbt, using `'sbt run'`.

Note that Scala-Web-REPL can not run in the same jvm as sbt. It should be forked into another jvm. Write `'fork := true'` in build.sbt or issue `'set fork := true'` in sbt's console.

2.
run it in other applications, add Scala-Web-REPL as a dependency, then follow the example codes in https://github.com/woshilaiceshide/scala-web-repl#code-example .

3.
run it as a java agent as below: 

	java -javaagent:/path/to/scala-web-repl-${version}.jar" -Dwrepl.listen.address=0.0.0.0 -Dwrepl.listen.port=8181 -cp ${classpath} ${main_class}

Or if you use sbt-native-packager, then add the following into build.sbt: 

	libraryDependencies += "woshilaiceshide" %% "scala-web-repl" % "1.0-SNAPSHOT"
	
	bashScriptExtraDefines += """addJava "-javaagent:${lib_dir}/woshilaiceshide.scala-web-repl-1.0-SNAPSHOT.jar""""
	bashScriptExtraDefines += """addJava "-Dwrepl.listen.address=0.0.0.0""""
	bashScriptExtraDefines += """addJava "-Dwrepl.listen.port=8181""""

Note that if `'wrepl.listen.address'` is not specified, it will be `'0.0.0.0'`, and `'wrepl.listen.port'` defaults to `'8484'`.

## How to Manipulate It?
After started, browse http://${host}:${port}/asset/wrepl.html . Type scala expressions in the terminal, which will be executed in the remote jvm.

Make sure that your browser supports javascript and WebSocket(v13).

## Code Example

	package woshilaiceshide.wrepl
	
	import scala.tools.nsc._
	import scala.tools.nsc.interpreter._
	
	import woshilaiceshide.wrepl.util.Utility
	
	object Server {
	  def newServer(interface: String, port: Int) = new Server(interface, port)
	}
	
	class Server(interface: String, port: Int, parameters: Seq[NamedParam] = Seq(), settings: Settings = Utility.defaultSettings, max_lines_kept_in_output_cache: Int = 32, repl_max_idle_time_in_seconds: Int = 60) {
	
	  import woshilaiceshide.wrepl.repl._
	  import scala.tools.nsc.interpreter.NamedParamClass
	
	  val httpServer: HttpServer = new HttpServer(interface, port, taskRunner => {
	
	    val get_http_server: NamedParamClass = NamedParamClass("get_http_server", "() => woshilaiceshide.wrepl.repl.HttpServer", () => httpServer)
	
	    new Bridge(taskRunner, get_http_server +: parameters, bridge => {
	      new PipedRepl(settings, bridge.writer)
	    }, max_lines_kept_in_output_cache, repl_max_idle_time_in_seconds)
	  })
	
	  def start(asynchronously: Boolean = false) { httpServer.start(asynchronously) }
	  def stop(timeout: Int) { httpServer.stop(timeout) }
	
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