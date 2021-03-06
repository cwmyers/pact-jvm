package au.com.dius.pact.model

import org.specs2.mutable.Specification
import Fixtures._

class MatchingSpec extends Specification {
  "Matching" should {
    import Matching._
    import JsonDiff._
    implicit val autoParse = JsonDiff.autoParse _
    "Body Matching" should {
      val config = DiffConfig()
      "Handle both None" in {
        matchBodies(None, None, config) must beEqualTo(MatchFound)
      }
      "Handle left None" in {
        val expected = BodyContentMismatch(missing(request.body.get))
        matchBodies(request.body, None, config) must beEqualTo(expected)
      }
      "Handle right None" in {
        val expected = BodyContentMismatch(added(request.body.get))
        matchBodies(None, request.body, config) must beEqualTo(expected)
      }
    }

    "Method Matching" should {
      "match same"  in {
        matchMethod("a", "a") must beEqualTo(MatchFound)
      }

      "match ignore case" in {
        matchMethod("a", "A") must beEqualTo(MatchFound)
      }

      "mismatch different" in {
        matchMethod("a", "b") must beEqualTo(MethodMismatch("a", "b"))
      }
    }
  }
}
