package woshilaiceshide.wrepl.repl

import scala.tools.nsc._
import scala.tools.nsc.interpreter._

import scala.util.Properties.{ jdkHome, javaVersion, versionString, javaVmName }

import java.io._

import woshilaiceshide.wrepl.util.Utility

class PipedRepl(settings: Settings, writer: Writer, type_rules: Seq[TypeGuardian.TypeRule]) {

  val in0: Option[BufferedReader] = None
  val pipedInputStream = new PipedInputStream(128)
  val pipedOutputStream = new PipedOutputStream()
  pipedOutputStream.connect(pipedInputStream)
  val reader = new InputStreamReader(pipedInputStream)

  val out = new JPrintWriter(writer)

  val repl = new ILoop(in0, out) {

    override def chooseReader(settings: Settings): InteractiveReader = {

      def mkReader(maker: ReaderMaker) =
        if (settings.noCompletion) maker(() => NoCompletion)
        else maker(() => new JLineCompletion(intp)) // JLineCompletion is a misnomer -- it's not tied to jline

      mkReader { completer => new InteractReaderGate(completer, reader, out) }
    }

    import scala.reflect.internal.util.ScalaClassLoader.savingContextLoader
    import StdReplTags._
    import scala.reflect.classTag

    private def pathToPhaseWrapper = intp.originalPath("$r") + ".phased.atCurrent"

    private def phaseCommand(name: String): Result = {
      val phased: Phased = power.phased
      import phased.NoPhaseName

      if (name == "clear") {
        phased.set(NoPhaseName)
        intp.clearExecutionWrapper()
        "Cleared active phase."
      } else if (name == "") phased.get match {
        case NoPhaseName => "Usage: :phase <expr> (e.g. typer, erasure.next, erasure+3)"
        case ph => "Active phase is '%s'.  (To clear, :phase clear)".format(phased.get)
      }
      else {
        val what = phased.parse(name)
        if (what.isEmpty || !phased.set(what))
          "'" + name + "' does not appear to represent a valid phase."
        else {
          intp.setExecutionWrapper(pathToPhaseWrapper)
          val activeMessage =
            if (what.toString.length == name.length) "" + what
            else "%s (%s)".format(what, name)

          "Active phase is now: " + activeMessage
        }
      }
    }

    private def loopPostInit() {
      // Bind intp somewhere out of the regular namespace where
      // we can get at it in generated code.
      intp.quietBind(NamedParam[IMain]("$intp", intp)(tagOfIMain, classTag[IMain]))
      // Auto-run code via some setting.
      (replProps.replAutorunCode.option
        flatMap (f => io.File(f).safeSlurp())
        foreach (intp quietRun _))
      // classloader and power mode setup
      intp.setContextClassLoader()
      if (isReplPower) {
        replProps.power setValue true

        //unleashAndSetPhase()
        {
          if (isReplPower) {
            power.unleash()
            // Set the phase to "typer"
            intp beSilentDuring phaseCommand("typer")
          }
        }

        asyncMessage(power.banner)
      }
      // SI-7418 Now, and only now, can we enable TAB completion.
      in.postInit()
    }

    /** Print a welcome message */
    override def printWelcome() {
      echo(s"""
      |Based on Scala $versionString ($javaVmName, Java $javaVersion).
      |Type in expressions to have them evaluated.
      |Type :help for more information.""".trim.stripMargin)
    }

    // start an interpreter with the given settings
    def process(settings: Settings, after_initialized: Option[(PipedRepl) => Unit] = None): Boolean = savingContextLoader {
      this.settings = settings
      createInterpreter()

      // sets in to some kind of reader depending on environmental cues
      in = in0.fold(chooseReader(settings))(r => SimpleReader(r, out, interactive = true))

      //globalFuture = future {}
      {
        intp.initializeSynchronous()
        loopPostInit()
        !intp.reporter.hasErrors
        after_initialized.map { _(PipedRepl.this) }
      }

      loadFiles(settings)
      //printWelcome()

      try loop1() match {
        case LineResults.EOF => out print Properties.shellInterruptedString
        case _ =>
      }
      catch AbstractOrMissingHandler()
      finally closeInterpreter()

      true
    }

    private val crashRecovery: PartialFunction[Throwable, Boolean] = {
      case ex: Throwable =>
        val (err, explain) = (
          if (intp.isInitializeComplete)
            (intp.global.throwableAsString(ex), "")
          else
            (ex.getMessage, "The compiler did not initialize.\n"))
        echo(err)

        ex match {
          case _: NoSuchMethodError | _: NoClassDefFoundError =>
            echo("\nUnrecoverable error.")
            throw ex
          case _ =>
            def fn(): Boolean =
              try in.readYesOrNo(explain + replayQuestionMessage, { echo("\nYou must enter y or n."); fn() })
              catch { case _: RuntimeException => false }

            if (fn()) replay()
            else echo("\nAbandoning crashed session.")
        }
        true
    }

    private def readOneLine() = {
      out.flush()
      in match {
        //just ignore prompt
        case x: InteractReaderGate => { x.readLineWithoutPrompt }
        case _ => in readLine prompt
      }
    }

    import LineResults.LineResult
    @scala.annotation.tailrec final def loop1(): LineResult = {
      import LineResults._
      readOneLine() match {
        case null => EOF
        case line => if (try processLine(line) catch crashRecovery) loop1() else ERR
      }
    }

    // return false if repl should exit
    override def processLine(line: String): Boolean = {
      //import scala.concurrent.duration._
      //Await.ready(globalFuture, 10.minutes) // Long timeout here to avoid test failures under heavy load.

      command(line) match {
        case Result(false, _) => false
        case Result(_, Some(line)) =>
          addReplay(line); true
        case _ => true
      }
    }

    //please see: 
    //1. scala.tools.nsc.interpreter.ReplGlobal
    //2. http://stackoverflow.com/questions/4713031/how-to-use-scalatest-to-develop-a-compiler-plugin-in-scala
    class RevisedILoopInterpreter extends ILoopInterpreter {

      import scala.tools.nsc.plugins.Plugin

      /** Instantiate a compiler.  Overridable. */
      override def newCompiler(settings: Settings, reporter: reporters.Reporter): ReplGlobal = {
        settings.outputDirs setSingleOutput replOutput.dir
        settings.exposeEmptyPackage.value = true
        new Global(settings, reporter) with ReplGlobal {

          override def toString: String = "<global>"

          override def loadRoughPluginsList: List[Plugin] =
            new TypeGuardian(this, type_rules) :: super.loadRoughPluginsList
        }
      }
    }

    /** Create a new interpreter. */
    override def createInterpreter() {
      if (addedClasspath != "")
        settings.classpath append addedClasspath

      intp = new RevisedILoopInterpreter
    }

  }

  object Status extends scala.Enumeration {
    val INITIALIZED, RUNNING, CLOSED = Value
  }

  private var status0 = Status.INITIALIZED
  protected def status = status0

  /** Print a welcome message */
  def welcome_msg = {
    s"""
      |Based on Scala $versionString ($javaVmName, Java $javaVersion).
      |Type in expressions to have them evaluated.
      |Type :help for more information.""".trim.stripMargin
  }

  def loop(asynchronous: Boolean, after_initialized: Option[(PipedRepl) => Unit] = None, after_stopped: Option[(PipedRepl) => Unit] = None) = {

    if (!asynchronous) {

      try {
        synchronized {
          if (Status.INITIALIZED == status0) {
            repl.process(settings, after_initialized)
            status0 = Status.RUNNING
          }
        }
      } finally {
        after_stopped.map { _(PipedRepl.this) }
      }

    } else {
      val worker = new Thread(new Runnable() {
        def run() = {

          try {
            synchronized {
              if (Status.INITIALIZED == status0) {
                repl.process(settings, after_initialized)
                status0 = Status.RUNNING
              }
            }
          } finally {
            after_stopped.map { _(PipedRepl.this) }
          }

        }
      }, "wrepl-worker")
      worker.start()
    }

  }

  def close() = synchronized {
    if (Status.CLOSED != status0) {
      status0 = Status.CLOSED
      safeOp { reader.close() }
      safeOp { out.close() }
    }
  }
}