package shopping.cart.impl

import org.slf4j.LoggerFactory
import shopping.cart.proto.{AddItemRequest, Cart, Item, ShoppingCartService}

import scala.concurrent.Future

class ShoppingCartServiceImpl extends ShoppingCartService{
  private val logger = LoggerFactory.getLogger(getClass)
  override def addItem(in: AddItemRequest): Future[Cart] = {
    logger.info("addItem {} to cart {}", in.itemId, in.cartId)
    Future.successful{
      Cart(items = List(Item(in.itemId,in.quantity)))
    }
  }
}
