/*
 * Copyright (C) 2009-2018 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.http.scaladsl.server.directives

import akka.http.scaladsl.model.{ HttpResponse, StatusCodes }
import akka.http.scaladsl.server.Route
import docs.CompileOnlySpec
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives._
import akka.stream.ActorMaterializer
import akka.http.scaladsl.model.HttpEntity._
import akka.http.scaladsl.model._
import com.typesafe.config.{ Config, ConfigFactory }
import org.scalatest.concurrent.ScalaFutures
import scala.concurrent.duration._
import scala.concurrent.{ Future, Promise }
import akka.testkit.{ AkkaSpec, SocketUtil }

private[this] object TimeoutDirectivesInfiniteTimeoutTestConfig {
  val testConf: Config = ConfigFactory.parseString("""
    akka.loggers = ["akka.testkit.TestEventListener"]
    akka.loglevel = ERROR
    akka.stdout-loglevel = ERROR
    windows-connection-abort-workaround-enabled = auto
    akka.log-dead-letters = OFF
    akka.http.server.request-timeout = infinite""")
}

class TimeoutDirectivesExamplesSpec extends AkkaSpec(TimeoutDirectivesInfiniteTimeoutTestConfig.testConf)
  with ScalaFutures with CompileOnlySpec {
  //#testSetup
  import system.dispatcher
  implicit val materializer = ActorMaterializer()

  def slowFuture(): Future[String] = Promise[String].future // TODO: move to Future.never in Scala 2.12

  def runRoute(route: Route, routePath: String): HttpResponse = {
    val (hostname, port) = SocketUtil.temporaryServerHostnameAndPort()
    val binding = Http().bindAndHandle(route, hostname, port)

    val response = Http().singleRequest(HttpRequest(uri = s"http://$hostname:$port/$routePath")).futureValue

    binding.flatMap(_.unbind()).futureValue

    response
  }

  //#testSetup

  // demonstrates that timeout is correctly set despite infinite initial value of akka.http.server.request-timeout
  "Request Timeout" should {
    "be configurable in routing layer despite infinite initial value of request-timeout" in {
      //#withRequestTimeout-plain
      val route =
        path("timeout") {
          withRequestTimeout(1.seconds) { // modifies the global akka.http.server.request-timeout for this request
            val response: Future[String] = slowFuture() // very slow
            complete(response)
          }
        }

      // check
      runRoute(route, "timeout").status should ===(StatusCodes.ServiceUnavailable) // the timeout response
      //#withRequestTimeout-plain
    }
    "without timeout" in compileOnlySpec {
      //#withoutRequestTimeout
      val route =
        path("timeout") {
          withoutRequestTimeout {
            val response: Future[String] = slowFuture() // very slow
            complete(response)
          }
        }

      // no check as there is no time-out, the future would time out failing the test
      //#withoutRequestTimeout
    }

    "allow mapping the response while setting the timeout" in {
      //#withRequestTimeout-with-handler
      val timeoutResponse = HttpResponse(
        StatusCodes.EnhanceYourCalm,
        entity = "Unable to serve response within time limit, please enhance your calm.")

      val route =
        path("timeout") {
          // updates timeout and handler at
          withRequestTimeout(1.milli, request => timeoutResponse) {
            val response: Future[String] = slowFuture() // very slow
            complete(response)
          }
        }

      // check
      runRoute(route, "timeout").status should ===(StatusCodes.EnhanceYourCalm) // the timeout response
      //#withRequestTimeout-with-handler
    }

    // make it compile only to avoid flaking in slow builds
    "allow mapping the response" in compileOnlySpec {
      //#withRequestTimeoutResponse
      val timeoutResponse = HttpResponse(
        StatusCodes.EnhanceYourCalm,
        entity = "Unable to serve response within time limit, please enhance your calm.")

      val route =
        path("timeout") {
          withRequestTimeout(100.milli) { // racy! for a very short timeout like 1.milli you can still get 503
            withRequestTimeoutResponse(request => timeoutResponse) {
              val response: Future[String] = slowFuture() // very slow
              complete(response)
            }
          }
        }

      // check
      runRoute(route, "timeout").status should ===(StatusCodes.EnhanceYourCalm) // the timeout response
      //#withRequestTimeoutResponse
    }

    // read currently set timeout
    "allow extraction of currently set timeout" in {
      //#extractRequestTimeout
      val timeout1 = 500.millis
      val timeout2 = 1000.millis
      val route =
        path("timeout") {
          withRequestTimeout(timeout1) {
            extractRequestTimeout { t1 ⇒
              withRequestTimeout(timeout2) {
                extractRequestTimeout { t2 ⇒
                  complete(
                    if (t1 == timeout1 && t2 == timeout2) StatusCodes.OK
                    else StatusCodes.InternalServerError
                  )
                }
              }
            }
          }
        }
      //#extractRequestTimeout

      runRoute(route, "timeout").status should ===(StatusCodes.OK)
    }
  }

}

private[this] object TimeoutDirectivesFiniteTimeoutTestConfig {
  val testConf: Config = ConfigFactory.parseString("""
    akka.loggers = ["akka.testkit.TestEventListener"]
    akka.loglevel = ERROR
    akka.stdout-loglevel = ERROR
    windows-connection-abort-workaround-enabled = auto
    akka.log-dead-letters = OFF
    akka.http.server.request-timeout = 1000s""")
}

class TimeoutDirectivesFiniteTimeoutExamplesSpec extends AkkaSpec(TimeoutDirectivesFiniteTimeoutTestConfig.testConf)
  with ScalaFutures with CompileOnlySpec {
  import system.dispatcher
  implicit val materializer = ActorMaterializer()

  def slowFuture(): Future[String] = Promise[String].future // TODO: move to Future.never in Scala 2.12

  def runRoute(route: Route, routePath: String): HttpResponse = {
    val (hostname, port) = SocketUtil.temporaryServerHostnameAndPort()
    val binding = Http().bindAndHandle(route, hostname, port)

    val response = Http().singleRequest(HttpRequest(uri = s"http://$hostname:$port/$routePath")).futureValue

    binding.flatMap(_.unbind()).futureValue

    response
  }

  // demonstrates that timeout is correctly modified for finite initial values of akka.http.server.request-timeout
  "Request Timeout" should {
    "be configurable in routing layer for finite initial value of request-timeout" in {
      val route =
        path("timeout") {
          withRequestTimeout(1.seconds) { // modifies the global akka.http.server.request-timeout for this request
            val response: Future[String] = slowFuture() // very slow
            complete(response)
          }
        }
      runRoute(route, "timeout").status should ===(StatusCodes.ServiceUnavailable) // the timeout response
    }
  }

}
