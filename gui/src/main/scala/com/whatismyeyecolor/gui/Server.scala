package com.whatismyeyecolor.gui

import java.net.InetSocketAddress
import scala.util.control.NonFatal
import com.twitter.finagle.{Http, Filter, Service}
import com.twitter.finagle.http.path._
import com.twitter.finagle.http.service.RoutingService
import com.twitter.finagle.http.{Method, Request, Response, Status}
import com.twitter.io.StreamIO
import com.twitter.util.{Await, Future}
import org.opencv.core.Core
import org.rogach.scallop.ScallopConf
import org.slf4j.LoggerFactory
import scalatags.Text.all._
import scalatags.Text.tags2.title

case class Server(port: Int) {

  import Server._

  val service = ExceptionHandlingFilter andThen RoutingService.byMethodAndPathObject {
    case Method.Get -> Root => IndexHandler
    case Method.Get -> Root / "assets" / ("scripts" | "styles") / _ => AssetsHandler
    case Method.Get -> Root / "assets" / "scripts" / "third-party" / _ => AssetsHandler
  }

  def run(): Unit = {
    val bindAddress = new InetSocketAddress(port)
    val server = Http.serve(bindAddress, service)
    try {
      log.info(s"Server listening at $bindAddress")
      Await.ready(server)
    } catch {
      case e: InterruptedException =>
        log.info("Server interrupted")
        Thread.currentThread.interrupt()
        throw e
    } finally {
      log.info("Server shutting down")
      server.close()
    }
  }
}

object Server {

  private val log = LoggerFactory.getLogger(getClass)

  def main(args: Array[String]): Unit = {
    try {
      log.info("Attempting to load native library " + Core.NATIVE_LIBRARY_NAME)
      System.loadLibrary(Core.NATIVE_LIBRARY_NAME)
    } catch {
      case _: UnsatisfiedLinkError =>
        log.error("Unable to load library " + Core.NATIVE_LIBRARY_NAME)
        sys.exit(1)
    }

    val conf = try {
      new Conf(args)
    } catch {
      case NonFatal(e) =>
        log.error("Error parsing arguments", e)
        sys.exit(1)
    }

    try {
      Server(conf.port()).run()
    } catch {
      case NonFatal(e) =>
        log.error("Unexpected exception encountered", e)
        sys.exit(2)
    }
  }

  private class Conf(args: Seq[String]) extends ScallopConf(args) {
    val port = opt[Int](required = true)
    verify()
  }

  object ExceptionHandlingFilter extends Filter[Request, Response, Request, Response] {
    override def apply(request: Request, continue: Service[Request, Response]): Future[Response] = {
      continue(request).rescue {
        case e: Throwable =>
          val msg = s"An unexpected error was encountered when processing your request"
          log.error(msg, e)
          val response = Response(request.version, Status.InternalServerError)
          response.contentString = s"$msg: ${e.getMessage}"
          Future.value(response)
      }
    }
  }

  object IndexHandler extends Service[Request, Response] {
    val ResponseHtmlString = "<!DOCTYPE html>" + html(
      head(
        title("What is my eye color?"),
        meta(charset := "utf-8"),
        link(href := "/assets/styles/main.css", rel := "stylesheet")
      ),
      body(
        script(src := "/assets/scripts/main.js")
      )
    )

    def apply(request: Request) = {
      val response = Response(request.version, Status.Ok)
      response.write(ResponseHtmlString)
      Future.value(response)
    }
  }

  object AssetsHandler extends Service[Request, Response] {
    def apply(request: Request) = {
      val stream = getClass.getResourceAsStream(request.path)
      val response = Response(request.version, Status.Ok)
      response.withOutputStream(StreamIO.copy(stream, _))
      Future.value(response)
    }
  }
}
