package shopping.cart.repository.impl

import akka.actor.typed.ActorSystem
import akka.cluster.sharding.typed.scaladsl.ShardedDaemonProcess
import akka.persistence.jdbc.query.scaladsl.JdbcReadJournal
import akka.persistence.query.Offset
import akka.projection.eventsourced.EventEnvelope
import akka.projection.eventsourced.scaladsl.EventSourcedProvider
import akka.projection.jdbc.scaladsl.JdbcProjection
import akka.projection.scaladsl.{ ExactlyOnceProjection, SourceProvider }
import akka.projection.{ ProjectionBehavior, ProjectionId }
import shopping.cart.entity.ShoppingCart
import shopping.cart.repository.ItemPopularityRepository
import shopping.cart.repository.scalike.ScalikeJdbcSession

/**
 * ShardedDaemonProcess will manage the Projection instances. It ensures the Projection instances are always running and distributes them over the nodes in the Akka Cluster.
 * The tag is selected based on the Projection instance’s index, corresponding to carts-0 to carts-3 as explained in the tagging in the ShoppingCart.
 * The source of the Projection is EventSourcedProvider.eventsByTag with the selected tag.
 * Using the JDBC event journal.
 * Using JDBC for offset storage of the Projection using exactly-once strategy. Offset and projected model will be persisted transactionally.
 * Creating the Projection Handler that we wrote in the beginning of this part.
 */
object ItemPopularityProjection {

  def createProjectionFor(
      system: ActorSystem[_],
      repo: ItemPopularityRepository,
      index: Int)
      : ExactlyOnceProjection[Offset, EventEnvelope[ShoppingCart.Event]] = {
    val tag = ShoppingCart.tags(index)
    val sourceProvider
        : SourceProvider[Offset, EventEnvelope[ShoppingCart.Event]] =
      EventSourcedProvider.eventsByTag(system, JdbcReadJournal.Identifier, tag)
    JdbcProjection.exactlyOnce(
      ProjectionId("ItemPopularityProjection", tag),
      sourceProvider,
      handler = () => new ItemPopularityProjectionHandler(tag, system, repo),
      sessionFactory = () => new ScalikeJdbcSession())(system)
  }

  def init(system: ActorSystem[_], repo: ItemPopularityRepository): Unit = {
    ShardedDaemonProcess(system).init(
      name = "ItemPopularityProjection",
      ShoppingCart.tags.size,
      index => ProjectionBehavior(createProjectionFor(system, repo, index)))
  }
}
