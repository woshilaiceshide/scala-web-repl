package woshilaiceshide.wrepl.util

trait JsonUtility {

  import spray.json._
  implicit class JsonHelper(raw: spray.json.JsValue) {

    def str(key: String): Option[String] = raw match {
      case JsObject(fields) => fields(key) match {
        case JsString(v) => Some(v)
        case _ => None
      }
      case _ => None
    }

    def bool(key: String): Option[Boolean] = raw match {
      case JsObject(fields) => fields(key) match {
        case JsBoolean(v) => Some(v)
        case _ => None
      }
      case _ => None
    }

  }

}

object JsonUtility extends JsonUtility