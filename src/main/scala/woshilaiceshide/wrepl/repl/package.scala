package woshilaiceshide.wrepl

import scala.tools.nsc._
import scala.tools.nsc.interpreter._

package object repl {

  type Completer = () => Completion
  type ReaderMaker = Completer => InteractiveReader

  def safeOp[T](x: => T) = try { x } catch {
    case _: Throwable => {
      //TODO logging???
    }
  }

  trait TaskRunner {
    def post(runnable: Runnable)
    def scheduleFuzzily(task: Runnable, delayInSeconds: Int)
    def registerOnTermination[T](code: => T): Boolean
  }

}