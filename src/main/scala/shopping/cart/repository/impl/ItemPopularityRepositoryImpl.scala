package shopping.cart.repository.impl

import scalikejdbc.{ scalikejdbcSQLInterpolationImplicitDef, DBSession }
import shopping.cart.repository.ItemPopularityRepository
import shopping.cart.repository.scalike.ScalikeJdbcSession

object ItemPopularityRepositoryImpl extends ItemPopularityRepository {
  override def update(
      session: ScalikeJdbcSession,
      itemId: String,
      delta: Int): Unit = session.db.withinTx { implicit dbSession=>
    sql"""
           INSERT INTO item_popularity (item_id, count) VALUES ($itemId, $delta)
           ON CONFLICT (item_id) DO UPDATE SET count = item_popularity.count + $delta
         """.executeUpdate().apply()
  }

  override def getItem(
      session: ScalikeJdbcSession,
      itemId: String): Option[Long] = {
    if (session.db.isTxAlreadyStarted) {
      session.db.withinTx { implicit dbSession =>
        select_item_count(itemId)
      }
    }else{
      session.db.readOnly{implicit dbSession =>
        select_item_count(itemId)
      }
    }
  }

  def select_item_count(itemId: String)(
      implicit dbSession: DBSession): Option[Long] =
    sql"""select count from item_popularity where item_id = $itemId"""
      .map(res => res.long("count"))
      .toOption()
      .apply()

}
