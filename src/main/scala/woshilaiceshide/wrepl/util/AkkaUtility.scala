package woshilaiceshide.wrepl.util

import akka.actor._

trait AkkaUtility {

  import AkkaUtility._

  object Restore

  trait RestorableActor extends Actor {

    private var wrapper: Option[ReceiveWrapper] = None

    abstract override def receive = {
      case Restore           => wrapper = None
      case x: ReceiveWrapper => wrapper = Some(x)
      case x: ActorProcessor => x.process(this)
      case x                 => super.receive(x)
    }

    override def aroundReceive(receive: Actor.Receive, msg: Any): Unit = {
      wrapper match {
        case None    => super.aroundReceive(receive, msg)
        case Some(x) => x.aroundReceive(receive, msg, super.aroundReceive)
      }

    }
  }

}

object AkkaUtility extends AkkaUtility {

  trait ActorProcessor {
    def process(actor: Actor)
  }

  trait ReceiveWrapper {
    def aroundReceive(receive: Actor.Receive, msg: Any, superAroundReceive: (Actor.Receive, Any) => Unit): Unit
  }
}