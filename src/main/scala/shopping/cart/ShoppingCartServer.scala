package shopping.cart

import akka.actor.typed.ActorSystem
import akka.grpc.scaladsl.{ ServerReflection, ServiceHandler }
import akka.http.scaladsl.Http

import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration.DurationInt
import scala.util.{ Failure, Success }

object ShoppingCartServer {
  def start(
      interface: String,
      port: Int,
      system: ActorSystem[_],
      grpcService: proto.ShoppingCartService): Unit = {
    implicit val sys: ActorSystem[_] = system
    implicit val ec: ExecutionContextExecutor = system.executionContext

    val service = ServiceHandler.concatOrNotFound(
      proto.ShoppingCartServiceHandler.partial(grpcService),
      ServerReflection.partial(List(proto.ShoppingCartService)))

    val bindingFuture = Http()
      .newServerAt(interface, port)
      .bind(service)
      .map(_.addToCoordinatedShutdown(10.seconds))

    bindingFuture.onComplete {
      case Success(bound) =>
        val address = bound.localAddress
        system.log.info(
          "Shopping Cart initialized at gRPC server - {}:{}",
          address.getHostString,
          address.getPort)
      case Failure(exception) =>
        system.log
          .error("Failed to bind gRPC endpoint, terminating...", exception)
        system.terminate()
    }
  }
}
