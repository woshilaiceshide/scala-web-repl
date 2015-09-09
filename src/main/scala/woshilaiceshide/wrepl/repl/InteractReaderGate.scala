package woshilaiceshide.wrepl.repl

import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PipedInputStream
import scala.tools.nsc.interpreter
import scala.tools.nsc.interpreter.Completion
import scala.tools.nsc.interpreter.InteractiveReader
import scala.tools.nsc.interpreter.session.History
import scala.tools.nsc.interpreter.session.NoHistory
import akka.actor.ActorRef
import akka.actor.actorRef2Scala
import java.io.PipedOutputStream

class InteractReaderGate(completer: Completer, reader: java.io.Reader, writer: java.io.Writer) extends InteractiveReader {

  private val in = new BufferedReader(reader)

  override def postInit(): Unit = {
    _completion = completer()
  }

  val interactive: Boolean = true
  def reset(): Unit = {}
  def history: History = NoHistory

  private[this] var _completion: Completion = interpreter.NoCompletion
  def completion: Completion = _completion

  def redrawLine(): Unit = {}

  protected def readOneLine(prompt: String): String = {
    writer.write(prompt)
    in.readLine()
  }

  protected def readOneKey(prompt: String): Int = {
    readOneLine(prompt).headOption.map(_.toInt).getOrElse(-1)
  }

  override def readLine(prompt: String): String = {
    readOneLine(prompt)
  }

  def readLineWithoutPrompt(): String = {
    in.readLine()
  }

}