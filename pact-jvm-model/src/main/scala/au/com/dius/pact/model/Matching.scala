package au.com.dius.pact.model

import org.json4s.{JValue, Diff}
import au.com.dius.pact.model.JsonDiff._
import org.json4s.JsonAST.JNothing

object Matching {
  sealed trait MatchResult {
    def and(o: MatchResult): MatchResult = { (this, o) match {
      case (MatchFound, MatchFound) => MatchFound
      case (a, MatchFound) => a
      case (MatchFound, b) => b
      case (MatchFailure(a), MatchFailure(b)) => MatchFailure(a.reverse_:::(b))
      case (a, MatchFailure(b)) => MatchFailure(b :+ a)
      case (MatchFailure(a), b) => MatchFailure(a :+ b)
      case (a, b) => MatchFailure(List(a, b))
    }}
  }

  case object MatchFound extends MatchResult
  case class MatchFailure(problems: List[MatchResult]) extends MatchResult {
    override def toString: String = {
      s"Multiple Mismatches Found: \n${problems.map(_.toString+ "\n")}"
    }
  }
  case class MethodMismatch(expected: String, actual: String) extends MatchResult {
    override def toString: String = {
      s"Method Mismatch(\n\texpected: $expected\n\tactual: $actual)"
    }
  }
  case class PathMismatch(expected: String, actual: String) extends MatchResult {
    override def toString: String = {
      s"Patch Mismatch(\n\texpected: $expected\n\tactual: $actual)"
    }
  }
  case class HeaderMismatch(expected: Headers, actual: Headers) extends MatchResult {
    override def toString: String = {
      s"Header Mismatch(\n\texpected: $expected\n\tactual: $actual)"
    }
  }
  case class BodyContentMismatch(diff: Diff) extends MatchResult {

    override def toString: String = {
      import org.json4s.jackson.JsonMethods._

      def stringify(msg: String, json: JValue): String = {
        json match {
          case JNothing => ""
          case j => s"$msg: ${compact(render(json))}"
        }
      }

      val changed = stringify("\n\tchanged", diff.changed)
      val added = stringify("\n\tadded", diff.added)
      val missing = stringify("\n\tmissing", diff.deleted)
      s"Body Content Mismatch($changed$added$missing)"
    }
  }
  case class StatusMismatch(expected: Int, actual: Int) extends MatchResult {
    override def toString: String = {
      s"Status Code Mismatch(\n\texpected: $expected\n\tactual: $actual)"
    }
  }

  implicit def pimpPactWithRequestMatch(pact: Pact) = RequestMatching(pact.interactions)

  private type Headers = Option[Map[String, String]]

  def compareHeaders(a: Map[String, String], b: Map[String, String]): MatchResult = {
    val relevantActualHeaders = b.filter { case (key, _) => a.contains(key) }
    if(a == relevantActualHeaders) {
      MatchFound
    } else {
      HeaderMismatch(Some(a), Some(relevantActualHeaders))
    }
  }

  def matchHeaders(expected: Headers, actual: Headers, reverseHeaders: Boolean = false): MatchResult = {
    val e = expected.getOrElse(Map())
    val a = actual.getOrElse(Map())
    if(reverseHeaders) {
      compareHeaders(a, e)
    } else {
      compareHeaders(e, a)
    }
  }

  def matchMethod(expected: String, actual: String): MatchResult = {
    if(expected.equalsIgnoreCase(actual)) {
      MatchFound
    } else {
      MethodMismatch(expected, actual)
    }
  }

  private type Body = Option[JValue]

  def matchBodies(expected: Body, actual: Body, diffConfig: DiffConfig): MatchResult = {
    implicit val autoParse = JsonDiff.autoParse _
    val difference = (expected, actual) match {
      case (None, None) => noChange
      case (None, Some(b)) => if(diffConfig.structural) { noChange } else { added(b) }
      case (Some(a), None) => missing(a)
      case (Some(a), Some(b)) => diff(a, b, diffConfig)
    }
    if(difference == noChange) {
      MatchFound
    } else {
      BodyContentMismatch(difference)
    }
  }

  def matchPath(expected: String, actual: String): MatchResult = {
    val pathFilter = "http[s]*://([^/]*)"
    val replacedActual = actual.replaceFirst(pathFilter, "")
    if(expected == replacedActual) {
      MatchFound
    } else {
      PathMismatch(expected, replacedActual)
    }
  }

  def matchStatus(expected: Int, actual: Int): MatchResult = {
    if(expected == actual) {
      MatchFound
    } else {
      StatusMismatch(expected, actual)
    }
  }
}
