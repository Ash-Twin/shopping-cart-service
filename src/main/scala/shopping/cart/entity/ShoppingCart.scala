package shopping.cart.entity

import akka.actor.typed.{ ActorRef, ActorSystem, Behavior, SupervisorStrategy }
import akka.cluster.sharding.typed.ShardingEnvelope
import akka.cluster.sharding.typed.scaladsl.{
  ClusterSharding,
  Entity,
  EntityContext,
  EntityTypeKey
}
import akka.pattern.StatusReply
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{
  Effect,
  EventSourcedBehavior,
  ReplyEffect,
  RetentionCriteria
}
import shopping.cart.CborSerializable

import java.time.Instant
import scala.concurrent.duration.DurationInt

object ShoppingCart {

  /** Event sourcing definition - Command/Event/State */
  sealed trait Command extends CborSerializable
  case class AddItem(
      itemId: String,
      quantity: Int,
      replyTo: ActorRef[StatusReply[Summary]])
      extends Command
  case class Summary(items: Map[String, Int], isCheckedOut: Boolean = false)
      extends CborSerializable
  case class Checkout(replyTo: ActorRef[StatusReply[Summary]]) extends Command
  case class Get(replyTo: ActorRef[Summary]) extends Command
  sealed trait Event extends CborSerializable
  case class ItemAdded(cartId: String, itemId: String, quantity: Int)
      extends Event
  case class CheckedOut(cartId: String, instant: Instant) extends Event
  final case class State(items: Map[String, Int], checkoutDate: Option[Instant])
      extends CborSerializable {
    def isCheckedOut: Boolean = {
      checkoutDate.isDefined
    }
    def checkout(now: Instant): State = {
      copy(checkoutDate = Some(now))
    }
    def hasItem(itemId: String): Boolean = {
      items.contains(itemId)
    }
    def isEmpty: Boolean = {
      items.isEmpty
    }
    //So elegant!
    def updateItem(itemId: String, quantity: Int): State = {
      quantity match {
        case 0 => copy(items = items - itemId)
        case _ => copy(items = items + (itemId -> quantity))
      }
    }
    def toSummary: Summary = {
      Summary(items, isCheckedOut)
    }
  }
  object State {
    val empty: State = State(items = Map.empty, checkoutDate = None)
  }

  /** Command Handler */
  private def openShoppingCart(
      cartId: String,
      command: Command,
      state: State): ReplyEffect[Event, State] = {
    command match {
      case AddItem(itemId, quantity, replyTo) =>
        if (quantity > 0) {
          if (state.hasItem(itemId)) {
            Effect
              .persist(
                ItemAdded(cartId, itemId, quantity + state.items(itemId)))
              .thenReply(replyTo) { updatedCart =>
                StatusReply.success(updatedCart.toSummary)
              }
          } else {
            Effect
              .persist(ItemAdded(cartId, itemId, quantity))
              .thenReply(replyTo) { updatedCart =>
                StatusReply.success(updatedCart.toSummary)
              }
          }
        } else
          Effect.reply(replyTo)(
            StatusReply.error("Quantity must be greater than 0!"))
      case Checkout(replyTo) =>
        if (state.isEmpty) {
          Effect.reply(replyTo)(
            StatusReply.error("Cannot checkout empty cart!"))
        } else {
          Effect.persist(CheckedOut(cartId, Instant.now())).thenReply(replyTo) {
            checkedOutCart => StatusReply.success(checkedOutCart.toSummary)
          }
        }
      case Get(replyTo) =>
        Effect.reply(replyTo)(state.toSummary)
    }
  }
  private def checkedOutShoppingCart(
      cartId: String,
      command: Command,
      state: State): ReplyEffect[Event, State] = {
    command match {
      case AddItem(_, _, replyTo) =>
        Effect.reply(replyTo)(
          StatusReply.error("Cannot add item into checked out shopping cart!"))
      case Checkout(replyTo) =>
        Effect.reply(replyTo)(StatusReply.error("Cart already checked out!"))
    }

  }

  /** Event Handler */
  private def handleEvent(event: Event, state: State) = {
    event match {
      case ItemAdded(_, itemId, quantity) =>
        state.updateItem(itemId, quantity)
      case CheckedOut(_, instant) =>
        state.checkout(instant)
    }
  }

  /** Core EventSourcedBehavior */
  def apply(cartId: String, tag: String): Behavior[Command] =
    EventSourcedBehavior
      .withEnforcedReplies[Command, Event, State](
        persistenceId = PersistenceId(EntityKey.name, cartId),
        emptyState = State.empty,
        commandHandler =
          (state, command) => openShoppingCart(cartId, command, state),
        eventHandler = (state, event) => handleEvent(event, state))
      .withTagger(_ => Set(tag))
      .withRetention(RetentionCriteria
        .snapshotEvery(numberOfEvents = 100, keepNSnapshots = 3))
      .onPersistFailure(
        SupervisorStrategy.restartWithBackoff(200.millis, 5.seconds, 0.1))

  /** Cluster sharding */
  val EntityKey: EntityTypeKey[Command] = EntityTypeKey[Command]("ShoppingCart")

  /** Cart identifiers */
  val tags: Seq[String] = Vector.tabulate(5)(i => s"cart-$i")

  def init(system: ActorSystem[_]): ActorRef[ShardingEnvelope[Command]] = {
    val behaviorFactory: EntityContext[Command] => Behavior[Command] =
      entityContext => {
        val i = math.abs(entityContext.entityId.hashCode % tags.size)
        val selectedTag = tags(i)
        ShoppingCart(entityContext.entityId, selectedTag)
      }
    ClusterSharding(system).init(entity = Entity(EntityKey)(behaviorFactory))
  }
}
