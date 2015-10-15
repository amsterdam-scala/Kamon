package kamon.elasticsearch

import akka.actor.ActorSystem
import akka.event.Logging
import akka.io.IO
import akka.util.Timeout
import spray.can.client.HostConnectorSettings
import scala.concurrent.duration._
import akka.pattern.ask
import spray.can._
import spray.client.pipelining._
import spray.http._
import scala.concurrent._

trait ElasticSearchClient {
  def doRest(request: HttpRequest): Future[HttpResponse]
  def doCheckedRest(request: HttpRequest): Future[HttpResponse]
}

object SprayElasticSearchClient {
  val MaxLogSize = 16 * 1024
}

class SimpleElasticSearchClient(val system: ActorSystem, val endpoint: String, val port: Int) extends SprayElasticSearchClient

trait SprayElasticSearchClient extends ElasticSearchClient {
  val log = Logging(system, classOf[SprayElasticSearchClient])

  import SprayElasticSearchClient._

  // TODO: shutdown client
  implicit protected[this] val system: ActorSystem
  implicit protected[this] val ec: ExecutionContext = system.dispatcher
  implicit protected[this] val timeout = Timeout(30.seconds)

  protected def endpoint: String
  protected def port: Int

  private lazy val pipeline =
    for {
      Http.HostConnectorInfo(connector, _) <- IO(Http) ? Http.HostConnectorSetup(endpoint, port)
    } yield sendReceive(connector)

  def doRest(request: HttpRequest): Future[HttpResponse] = {
    log.info(s"Doing HTTP request to http://${endpoint}:${port}/${request.uri}")
    log.debug(s"HTTP Content:\n${request.entity.asString}")
    pipeline.flatMap(_(request))
  }

  def doCheckedRest(request: HttpRequest): Future[HttpResponse] = {
    log.info(s"Doing HTTP request to ${request.uri}")
    log.debug(s"HTTP Request Body Content (${request.entity.asString.length()} bytes):\n${request.entity.asString.take(MaxLogSize)}")
    pipeline.flatMap(_(request)).map { response =>
      if (response.status == StatusCodes.OK) {
        log.debug(s"HTTP Response Body Content (${response.entity.asString.length()} bytes):\n${response.entity.asString.take(MaxLogSize)}")
        response
      } else {
        log.error(s"Could not process REST call to ${request.uri}, received code ${response.status.intValue} with message:\n\nRequest:\n${request.entity.asString.take(MaxLogSize)}\n\n${response.entity.asString.take(MaxLogSize)}")
        throw new IllegalStateException(s"Could not complete REST call to ${request.uri}, received code ${response.status.intValue}.\n\nRequest:\n${request.entity.asString.take(MaxLogSize)}\n\n${response.entity.asString.take(MaxLogSize)}")
      }
    }
  }
}
