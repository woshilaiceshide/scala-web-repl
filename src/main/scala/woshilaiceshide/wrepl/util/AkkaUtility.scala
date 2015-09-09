package woshilaiceshide.wrepl.util

import akka.actor._

trait AkkaUtility {

  abstract class SentByRepl {
    def run(actor: Actor)
  }
  trait ReplAware extends Actor {

    abstract override def receive = {

      case sbr: SentByRepl => sbr.run(this)
      case x               => super.receive(x)
    }
  }

  abstract class ReceiveWrapperSentByRepl {
    def aroundReceive(receive: Actor.Receive, msg: Any, superAroundReceive: (Actor.Receive, Any) => Unit): Unit
  }
  object Restore

  trait RestorableReplAware extends Actor {

    private var wrapper: Option[ReceiveWrapperSentByRepl] = None
    abstract override def receive = {

      case Restore                     => wrapper = None
      case x: ReceiveWrapperSentByRepl => wrapper = Some(x)
      case x: SentByRepl               => x.run(this)
      case x                           => super.receive(x)
    }

    //TODO
    override def aroundReceive(receive: Actor.Receive, msg: Any): Unit = {
      wrapper match {
        case None    => super.aroundReceive(receive, msg)
        case Some(x) => x.aroundReceive(receive, msg, super.aroundReceive)
      }

    }
  }

}

object AkkaUtility extends AkkaUtility