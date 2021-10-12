import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.pattern.StatusReply
import akka.persistence.testkit.scaladsl.EventSourcedBehaviorTestKit
import com.typesafe.config.{ Config, ConfigFactory }
import org.scalatest.BeforeAndAfterEach
import org.scalatest.wordspec.AnyWordSpecLike
import shopping.cart.entity.ShoppingCart
import shopping.cart.entity.ShoppingCart._

object ShoppingCartSpec {
  val config: Config = ConfigFactory
    .parseString("""
      akka.actor.serialization-bindings {
        "shopping.cart.CborSerializable" = jackson-cbor
      }
      """)
    .withFallback(EventSourcedBehaviorTestKit.config)
}
class ShoppingCartSpec
    extends ScalaTestWithActorTestKit(ShoppingCartSpec.config)
    with AnyWordSpecLike
    with BeforeAndAfterEach {
  private val cartId = "test-cart-id"
  private val eventSourcedTestkit: EventSourcedBehaviorTestKit[
    ShoppingCart.Command,
    ShoppingCart.Event,
    ShoppingCart.State] = EventSourcedBehaviorTestKit[
    ShoppingCart.Command,
    ShoppingCart.Event,
    ShoppingCart.State](system, ShoppingCart(cartId))

  override def beforeEach(): Unit = {
    super.beforeEach()
    eventSourcedTestkit.clear()
  }
  "Shopping Cart" should {
    "AddItem" in {
      val addItemResult =
        eventSourcedTestkit.runCommand[StatusReply[Summary]](
          AddItem("item-1", 100, _))
      addItemResult.state shouldEqual State(Map("item-1" -> 100), None)
      addItemResult.event shouldEqual ItemAdded(cartId, "item-1", 100)
    }
    "Add2Item" in {
      val addItemResult = {
        eventSourcedTestkit.runCommand[StatusReply[Summary]](
          AddItem("item-2", 100, _))
        eventSourcedTestkit.runCommand[StatusReply[Summary]](
          AddItem("item-2", 150, _))
      }
      addItemResult.state shouldEqual State(Map("item-2" -> 250), None)
      addItemResult.event shouldEqual ItemAdded(cartId, "item-2", 250)
    }
    "checkout" in {
      val checkoutCartResult = {
      eventSourcedTestkit.runCommand[StatusReply[Summary]](
        AddItem("item-1", 100, _))
      eventSourcedTestkit.runCommand[StatusReply[Summary]](
        AddItem("item-2", 100, _))
        eventSourcedTestkit.runCommand[StatusReply[Summary]](Checkout)
      }
      checkoutCartResult.reply shouldEqual StatusReply.Success(
        ShoppingCart.Summary(Map("item-1" -> 100,"item-2"->100),isCheckedOut = true))
    }
  }
}
