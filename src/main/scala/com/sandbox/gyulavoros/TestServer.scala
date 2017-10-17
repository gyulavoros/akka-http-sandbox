package com.sandbox.gyulavoros

import akka.actor.ActorSystem
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpResponse}
import akka.http.scaladsl.server.{HttpApp, Route}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Source
import akka.util.ByteString

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContextExecutor, Promise}
import scala.util.{Random, Success}

class TestServer extends HttpApp {

  private implicit val system: ActorSystem = ActorSystem("server")
  private implicit val materializer: ActorMaterializer = ActorMaterializer()

  private implicit val ec: ExecutionContextExecutor = system.dispatcher

  private val json = ByteString(
    """
      |{ "name": "value" }
    """.stripMargin)

  override protected def routes: Route = {
    get {
      pathSingleSlash {
        complete {
          val p = Promise[HttpResponse]
          val responseDelay = if (Random.nextBoolean()) 1.millis else 1000.millis // server either responds in 1 or 1000 millis
          val entityDelay = if (Random.nextBoolean()) 1.millis else 500.millis // server either writes json entity in 1 or 500 millis
          system.scheduler.scheduleOnce(responseDelay) {
            val entity = Source.single(json).initialDelay(entityDelay)
            val response = HttpResponse()
              .withEntity(HttpEntity(ContentTypes.`application/json`, json.length.toLong, entity))
            p.complete(Success(response))
          }
          p.future
        }
      }
    }
  }
}
