package shopping.cart.repository.impl

import akka.actor.typed.ActorSystem
import akka.projection.eventsourced.EventEnvelope
import akka.projection.jdbc.scaladsl.JdbcHandler
import org.slf4j.LoggerFactory
import shopping.cart.entity.ShoppingCart
import shopping.cart.repository.ItemPopularityRepository
import shopping.cart.repository.scalike.ScalikeJdbcSession

/**
 * @param tag used for event sourcing persistence
 * @param system sys env
 * @param repo db action for projection
 */
class ItemPopularityProjectionHandler(
    tag: String,
    system: ActorSystem[_],
    repo: ItemPopularityRepository)
    extends JdbcHandler[EventEnvelope[ShoppingCart.Event], ScalikeJdbcSession] {
  private val logger = LoggerFactory.getLogger(getClass)
  override def process(
      session: ScalikeJdbcSession,
      envelope: EventEnvelope[ShoppingCart.Event]): Unit = {
    envelope.event match {
      /** event based projection */
      case ShoppingCart.ItemAdded(_, itemId, quantity) =>
        repo.update(session, itemId, quantity)
        logger.info(
          "ItemPopularityProjectionHandler({}) item popularity for '{}': [{}]",
          tag,
          itemId,
          repo.getItem(session, itemId).getOrElse(0))

      /** no operation with checkout */
      case _: ShoppingCart.CheckedOut =>
    }
  }
}
