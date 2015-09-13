package woshilaiceshide.wrepl

import akka.actor._
import woshilaiceshide.wrepl.util.Utility

object AkkaHouse {

  class RawPrinter extends Actor {
    override def receive = {
      case x => println(s"""I'm printing: ${x}""")
    }
  }

  class Printer extends RawPrinter with Utility.RestorableActor

  val config = com.typesafe.config.ConfigFactory.load()
  val system = ActorSystem("akka-house", config)
  val printer = system.actorOf(Props(classOf[Printer]), "printer")

}