package shopping.cart

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.management.cluster.bootstrap.ClusterBootstrap
import akka.management.scaladsl.AkkaManagement
import org.slf4j.LoggerFactory
import shopping.cart.entity.ShoppingCart
import shopping.cart.impl.ShoppingCartServiceImpl
import shopping.cart.repository.impl.{
  ItemPopularityProjection,
  ItemPopularityRepositoryImpl
}
import shopping.cart.repository.scalike.ScalikeJdbcSetup

import scala.util.control.NonFatal

object Main {

  private val logger = LoggerFactory.getLogger("shopping.cart.Main")

  def main(args: Array[String]): Unit = {
    val system = ActorSystem[Nothing](Behaviors.empty, "ShoppingCartService")
    try {
      init(system)
    } catch {
      case NonFatal(e) =>
        logger.error("Terminating due to initialization failure.", e)
        system.terminate()
    }
  }

  def init(system: ActorSystem[_]): Unit = {
    AkkaManagement(system).start()
    ClusterBootstrap(system).start()
    val grpcInterface =
      system.settings.config.getString("shopping-cart-service.grpc.interface")
    val grpcPort =
      system.settings.config.getInt("shopping-cart-service.grpc.port")
    val itemPopularityRepository = new ItemPopularityRepositoryImpl()
    val grpcService = new ShoppingCartServiceImpl(system,itemPopularityRepository)
    ShoppingCart.init(system)
    ScalikeJdbcSetup.init(system)
    ItemPopularityProjection.init(system, itemPopularityRepository)
    ShoppingCartServer.start(grpcInterface, grpcPort, system, grpcService)
  }
}
