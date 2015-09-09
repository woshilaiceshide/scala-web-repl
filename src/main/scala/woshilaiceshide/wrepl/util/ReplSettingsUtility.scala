package woshilaiceshide.wrepl.util

import scala.tools.nsc.Settings
import scala.reflect.ClassTag

trait ReplSettingsUtility {

  @scala.annotation.tailrec
  final def classpath(loader: ClassLoader, found: List[String] = Nil): List[String] = {
    val my = loader match {
      case url_class_loader: java.net.URLClassLoader => {
        found ++ url_class_loader.getURLs.map(_.toString)
      }
      case _ => found
    }
    loader.getParent() match {
      case null => my
      case x    => classpath(x, my)
    }
  }

  def defaultSettings = {
    val tmp = new Settings()
    val loader = Thread.currentThread().getContextClassLoader
    tmp.embeddedDefaults(loader)
    tmp.usejavacp.value = true
    tmp.Yreplsync.value = true
    tmp.Ylogcp.value = false
    tmp.classpath.value = classpath(loader).distinct.mkString(java.io.File.pathSeparator)
    tmp.noCompletion.value = false
    tmp
  }

  def defaultSettingsFromClass[T: ClassTag] = {
    val tmp = new Settings()
    val loader = implicitly[ClassTag[T]].runtimeClass.getClassLoader
    tmp.embeddedDefaults(loader)
    tmp.usejavacp.value = true
    tmp.Yreplsync.value = true
    tmp.Ylogcp.value = false
    tmp.classpath.value = classpath(loader).distinct.mkString(java.io.File.pathSeparator)
    tmp.noCompletion.value = false
    tmp
  }

}

object ReplSettingsUtility extends ReplSettingsUtility