package woshilaiceshide.wrepl.util

import com.google.common.hash.Hashing

object Utility extends AkkaUtility with JsonUtility with ReplSettingsUtility with scala.tools.nsc.AccessorUtility {

  def md5(s: String) = Hashing.md5().hashBytes(s.getBytes()).toString()

}