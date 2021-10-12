package shopping.cart.repository

import shopping.cart.repository.scalike.ScalikeJdbcSession

trait ItemPopularityRepository {
  def update(session:ScalikeJdbcSession,itemId:String,delta:Int):Unit
  def getItem(session:ScalikeJdbcSession,itemId:String):Option[Long]
}
