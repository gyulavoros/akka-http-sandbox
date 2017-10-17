package com.sandbox.gyulavoros

import java.util.concurrent.TimeoutException

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpEntity, HttpRequest, HttpResponse, RequestTimeoutException}
import akka.http.scaladsl.settings.ConnectionPoolSettings
import akka.stream._
import akka.stream.scaladsl.{Keep, Sink, Source}
import org.slf4j.LoggerFactory

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContextExecutor, Future, Promise}
import scala.util.{Failure, Success}

case class QueueItem(request: HttpRequest, deadline: Deadline)

class TestClient(private val host: String, private val port: Int) {

  private val headerTimeout = 500.millis // maximum amount of time that we wait for a response header
  private val entityTimeout = 250.millis // maximum amount of time that we wait for the response entity
  private val queueTimeout = 500.millis // maximum amount of time that a request can be waiting in the queue

  private val logger = LoggerFactory.getLogger("Client")

  private implicit val system: ActorSystem = ActorSystem("client")

  private val materializerSettings = ActorMaterializerSettings(system).withInputBuffer(8, 16)
  private implicit val materializer: ActorMaterializer = ActorMaterializer(materializerSettings)

  private implicit val ec: ExecutionContextExecutor = system.dispatcher

  private val settings = ConnectionPoolSettings(system)
    .withMaxConnections(1)
    .withMaxOpenRequests(1)
    .withMaxRetries(0)
    .withIdleTimeout(1000.millis)

  private val flow = Http(system).cachedHostConnectionPool[Promise[HttpResponse]](host, port, settings)

  private val queue =
    Source.queue[(QueueItem, Promise[HttpResponse])](4, OverflowStrategy.dropNew)
      .flatMapConcat { case (item, promise) =>
        // if the request spent too much time in the queue, we complete the promise with a RequestTimeoutException
        // instead of passing it to the ConnectionPool
        if (item.deadline.isOverdue()) {
          Source.single(promise.failure(RequestTimeoutException(item.request, "queue")))
        } else {
          Source.single((item.request, promise))
            .via(flow)
            .map {
              case ((Success(resp), p)) => p.success(resp)
              case ((Failure(e), p)) => p.failure(e)
            }
        }
      }
      .toMat(Sink.ignore)(Keep.left)
      .run()

  private var successes = 0
  private var queueTimeouts = 0
  private var headerTimeouts = 0
  private var entityTimeouts = 0
  private var overflows = 0
  private var errors = 0

  def runWithTimeout(): Future[HttpEntity.Strict] = {
    val request = HttpRequest()
    // we have to make sure that even if timeouts kick in we discard the entity bytes of ongoing responses
    val responseF = queueRequest(request)
    Source.fromFuture(responseF)
      // this can timeout before we receive response header
      .completionTimeout(headerTimeout)
      .mapError {
        case _: TimeoutException =>
          responseF.onComplete {
            case Success(response) =>
              // if the response was successful we discard entity bytes
              response.discardEntityBytes()
              logger.warn(s"Discarding response entity because of timeout")
            case Failure(_) =>
          }
          RequestTimeoutException(request, "header") // convert TimeoutException to RequestTimeoutException
      }
      .mapAsync(8) { response =>
        // because response entity is another stream, we convert it to a strict entity using a separate timeout
        response.entity.toStrict(entityTimeout).transform {
          case s@Success(_) => s
          case Failure(_) => Failure(RequestTimeoutException(request, "entity"))
        }
      }
      .runWith(Sink.head)
  }

  def scheduleRequests(): Unit = {
    system.scheduler.schedule(0.millis, 250.millis) {
      runWithTimeout()
        .onComplete {
          case Success(_) =>
            successes += 1
            log()
          case Failure(ex) =>
            ex match {
              case _: BufferOverflowException => overflows += 1 // queue is full, new request is overflowed
              case e: RequestTimeoutException if e.message == "queue" => queueTimeouts += 1 // new request spent too much time in queue
              case e: RequestTimeoutException if e.message == "header" => headerTimeouts += 1 // response header didn't arrive in time
              case e: RequestTimeoutException if e.message == "entity" => entityTimeouts += 1 // response entity didn't arrive in time
              case _: Exception => errors += 1
            }
            log()
        }
    }
  }

  private def queueRequest(request: HttpRequest): Future[HttpResponse] = {
    val responsePromise = Promise[HttpResponse]()
    // we set a deadline for every request before putting it to the queue
    queue.offer(QueueItem(request, queueTimeout.fromNow) -> responsePromise).flatMap {
      case QueueOfferResult.Enqueued => responsePromise.future
      case QueueOfferResult.Dropped => Future.failed(BufferOverflowException("Queue overflowed. Try again later."))
      case QueueOfferResult.Failure(ex) => Future.failed(ex)
      case QueueOfferResult.QueueClosed => Future.failed(new RuntimeException("Queue was closed (pool shut down) while running the request. Try again later."))
    }
  }

  private def log(): Unit = {
    logger.info(s"s: $successes | qto: $queueTimeouts | hto: $headerTimeouts | eto: $entityTimeouts | o: $overflows | e: $errors")
  }
}
