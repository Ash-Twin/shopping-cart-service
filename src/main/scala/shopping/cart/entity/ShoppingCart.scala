package shopping.cart.entity

import akka.actor.typed.{ActorRef, ActorSystem, Behavior, SupervisorStrategy}
import akka.cluster.sharding.typed.ShardingEnvelope
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity, EntityTypeKey}
import akka.pattern.StatusReply
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior, ReplyEffect, RetentionCriteria}
import shopping.cart.CborSerializable

import scala.concurrent.duration.DurationInt

object ShoppingCart {

  /** Event sourcing definition - Command/Event/State */
  sealed trait Command extends CborSerializable
  case class AddItem(
      itemId: String,
      quantity: Int,
      replyTo: ActorRef[StatusReply[Summary]])
      extends Command
  case class Summary(items: Map[String, Int]) extends CborSerializable

  sealed trait Event extends CborSerializable
  case class ItemAdded(cartId: String, itemId: String, quantity: Int)
      extends Event
  final case class State(items: Map[String, Int]) extends CborSerializable {
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
  }
  object State {
    val empty: State = State(items = Map.empty)
  }

  /** Command Handler */
  private def handleCommand(
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
                StatusReply.success(Summary(updatedCart.items))
              }
          } else {
            Effect
              .persist(ItemAdded(cartId, itemId, quantity))
              .thenReply(replyTo) { updatedCart =>
                StatusReply.success(Summary(updatedCart.items))
              }
          }
        } else
          Effect.reply(replyTo)(
            StatusReply.error("Quantity must be greater than 0!"))
    }
  }

  /** Event Handler */
  private def handleEvent(event: Event, state: State) = {
    event match {
      case ItemAdded(_, itemId, quantity) =>
        state.updateItem(itemId, quantity)
    }
  }

  /** Core EventSourcedBehavior */
  def apply(cartId: String): Behavior[Command] =
    EventSourcedBehavior
      .withEnforcedReplies[Command, Event, State](
        persistenceId = PersistenceId(EntityKey.name, cartId),
        emptyState = State.empty,
        commandHandler =
          (state, command) => handleCommand(cartId, command, state),
        eventHandler = (state, event) => handleEvent(event, state))
      .withRetention(RetentionCriteria
        .snapshotEvery(numberOfEvents = 100, keepNSnapshots = 3))
      .onPersistFailure(
        SupervisorStrategy.restartWithBackoff(200.millis, 5.seconds, 0.1))

  /** Cluster sharding */
  val EntityKey: EntityTypeKey[Command] = EntityTypeKey[Command]("ShoppingCart")

  def init(system: ActorSystem[_]): ActorRef[ShardingEnvelope[Command]] = {
    ClusterSharding(system).init(entity = Entity(EntityKey) { entityContext =>
      ShoppingCart(entityContext.entityId)
    })
  }
}
