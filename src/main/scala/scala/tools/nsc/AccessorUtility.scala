package scala.tools.nsc

trait AccessorUtility {
  def explicitParentLoader(settings: Settings) = settings.explicitParentLoader
}

object AccessorUtility extends AccessorUtility {

}