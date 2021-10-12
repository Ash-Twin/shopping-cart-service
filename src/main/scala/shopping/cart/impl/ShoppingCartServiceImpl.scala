package shopping.cart.impl

import akka.actor.typed.{ActorSystem, DispatcherSelector}
import akka.cluster.sharding.typed.scaladsl.ClusterSharding
import akka.grpc.GrpcServiceException
import akka.util.Timeout
import io.grpc.Status
import org.slf4j.LoggerFactory
import scalikejdbc.DB.withinTxSession
import shopping.cart.entity.ShoppingCart
import shopping.cart.proto._
import shopping.cart.repository.ItemPopularityRepository
import shopping.cart.repository.scalike.ScalikeJdbcSession

import scala.concurrent.{ExecutionContext, Future, TimeoutException}

class ShoppingCartServiceImpl(system: ActorSystem[_],itemPopularityRepository: ItemPopularityRepository)
    extends ShoppingCartService {
  private val logger = LoggerFactory.getLogger(getClass)
  implicit val ec: ExecutionContext = system.executionContext
  implicit val timeout: Timeout = Timeout.create(
    system.settings.config.getDuration("shopping-cart-service.ask-timeout"))
  private val sharding = ClusterSharding(system)
  def toProtoCart(cart: ShoppingCart.Summary): Cart = {
    Cart(
      cart.items.map { case (itemId, quantity) =>
        Item(itemId, quantity)
      }.toSeq,
      cart.isCheckedOut)
  }

  override def addItem(in: AddItemRequest): Future[Cart] = {
    logger.info("addItem {} to cart {}", in.itemId, in.cartId)
    val entityRef = sharding.entityRefFor(ShoppingCart.EntityKey, in.cartId)
    val reply =
      entityRef.askWithStatus(ShoppingCart.AddItem(in.itemId, in.quantity, _))
    val response = reply.map(cart => toProtoCart(cart))
    convertError(response)
  }

  /** Cluster Sharding */
  override def get(in: GetCartRequest): Future[Cart] = {
    logger.info("get current cart:{}", in.cartId)
    val entityRef = sharding.entityRefFor(ShoppingCart.EntityKey, in.cartId)
    val response = entityRef.ask(ShoppingCart.Get).map { cart =>
      if (cart.items.isEmpty) {
        throw new GrpcServiceException(
          Status.NOT_FOUND.withDescription(s"Cart:${in.cartId} Not Found"))
      } else {
        toProtoCart(cart)
      }
    }
    convertError(response)
  }

  /** Cluster Sharding */
  override def checkout(in: CheckoutRequest): Future[Cart] = {
    logger.info("checkout cart:{}", in.cartId)
    val entityRef = sharding.entityRefFor(ShoppingCart.EntityKey, in.cartId)
    val reply = entityRef.askWithStatus(ShoppingCart.Checkout)
    val response = reply.map(toProtoCart)
    convertError(response)
  }

  private def convertError[T](response: Future[T]): Future[T] = {
    response.recoverWith {
      case _: TimeoutException =>
        Future.failed(
          new GrpcServiceException(
            Status.UNAVAILABLE.withDescription("Operation timed out")))
      case exc =>
        Future.failed(
          new GrpcServiceException(
            Status.INVALID_ARGUMENT.withDescription(exc.getMessage)))
    }
  }

  private val blockingJdbcExecutor:ExecutionContext = system.dispatchers.lookup(DispatcherSelector.fromConfig("akka.projection.jdbc.blocking-jdbc-dispatcher"))
  override def getItemPopularity(in: GetItemPopularityRequest): Future[GetItemPopularityResponse] = {
    Future{
      ScalikeJdbcSession.withSession(session => itemPopularityRepository.getItem(session,in.itemId))
    }(blockingJdbcExecutor).map {
      case Some(popularity) =>
        GetItemPopularityResponse(in.itemId,popularity)
      case None =>
        GetItemPopularityResponse(in.itemId)
    }
  }
}
