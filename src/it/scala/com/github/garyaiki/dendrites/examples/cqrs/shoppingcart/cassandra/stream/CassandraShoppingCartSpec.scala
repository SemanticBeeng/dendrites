/**
  *
  * Licensed under the Apache License, Version 2.0 (the "License");
  * you may not use this file except in compliance with the License.
  * You may obtain a copy of the License at
  *
  * http://www.apache.org/licenses/LICENSE-2.0
  *
  * Unless required by applicable law or agreed to in writing, software
  * distributed under the License is distributed on an "AS IS" BASIS,
  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  * See the License for the specific language governing permissions and
  * limitations under the License.
  */
package com.github.garyaiki.dendrites.examples.cqrs.shoppingcart.cassandra.stream

import akka.NotUsed
import akka.actor.ActorSystem
import akka.event.{Logging, LoggingAdapter}
import akka.stream.{ActorAttributes, ActorMaterializer}
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import akka.stream.testkit.scaladsl.{TestSink, TestSource}
import com.datastax.driver.core.{Cluster, PreparedStatement, ResultSet, Row, Session}
import com.datastax.driver.core.policies.{DefaultRetryPolicy, LoggingRetryPolicy}
import java.util.UUID
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import scala.collection.immutable.Iterable
import scala.concurrent.ExecutionContext
import com.github.garyaiki.dendrites.cassandra.{close, connect, createCluster, createLoadBalancingPolicy, createSchema,
  dropSchema, getConditionalError, initLoadBalancingPolicy, logMetadata, registerQueryLogger}
import com.github.garyaiki.dendrites.cassandra.stream.{CassandraBind, CassandraBoundQuery, CassandraConditional,
  CassandraMappedPaging, CassandraPaging, CassandraQuery, CassandraRetrySink, CassandraSink}
import com.github.garyaiki.dendrites.examples.cqrs.shoppingcart.{ShoppingCart, SetItems, SetOwner}
import com.github.garyaiki.dendrites.examples.cqrs.shoppingcart.cassandra.{ShoppingCartConfig, CassandraShoppingCart}
import com.github.garyaiki.dendrites.examples.cqrs.shoppingcart.cassandra.CassandraShoppingCart.{bndDelete, bndInsert,
  bndQuery, bndUpdateItems, bndUpdateOwner, checkAndSetOwner, createTable, mapRows, prepDelete, prepInsert, prepQuery,
  prepUpdateItems, prepUpdateOwner, rowToString}
import com.github.garyaiki.dendrites.examples.cqrs.shoppingcart.cassandra.RetryConfig

class CassandraShoppingCartSpec extends WordSpecLike with Matchers with BeforeAndAfterAll {
  implicit val system = ActorSystem("dendrites")
  implicit val ec: ExecutionContext = system.dispatcher
  implicit val materializer = ActorMaterializer()
  implicit val logger = Logging(system, getClass)
  val myConfig = ShoppingCartConfig
  val schema = myConfig.keySpace
  var cluster: Cluster = null
  var session: Session = null
  val cartId: UUID = UUID.randomUUID
  val ownerId: UUID = UUID.randomUUID
  val updatedOwnerId: UUID = UUID.randomUUID
  val firstItemId: UUID = UUID.randomUUID
  val items: Map[UUID, Int] = Map(firstItemId -> 1, UUID.randomUUID -> 1)
  val updatedItems: Map[UUID, Int] = items + (firstItemId -> 2)
  val cart: ShoppingCart = ShoppingCart(cartId, ownerId, items)
  val carts: Seq[ShoppingCart] = Seq(cart)
  val cartIds: Seq[UUID] = Seq(cartId)
  val setOwner: SetOwner = SetOwner(cartId, updatedOwnerId)
  val setOwners = Seq(setOwner)
  val setItems: SetItems = SetItems(cartId, updatedItems)
  val updatedItemsCart: ShoppingCart = ShoppingCart(cartId, ownerId, updatedItems, 1)
  val updatedItemsCarts = Seq(updatedItemsCart)
  val updatedCart: ShoppingCart = ShoppingCart(cartId, updatedOwnerId, updatedItems, 2)
  val updatedCarts = Seq(updatedCart)
  var queryPrepStmt: PreparedStatement = null

  override def beforeAll() {
    val addresses = myConfig.getInetAddresses()
    val retryPolicy = new LoggingRetryPolicy(DefaultRetryPolicy.INSTANCE)
    cluster = createCluster(addresses, retryPolicy)
    val lbp = createLoadBalancingPolicy(myConfig.localDataCenter)
    initLoadBalancingPolicy(cluster, lbp)
    logMetadata(cluster)
    registerQueryLogger(cluster)
    session = connect(cluster)
    val strategy = myConfig.replicationStrategy
    val createSchemaRS = createSchema(session, schema, strategy, 1) // 1 instance
    val cartTableRS = createTable(session, schema)
    queryPrepStmt = prepQuery(session, schema)
  }

  "A Cassandra ShoppingCart client" should {
    "insert ShoppingCart " in {
      val iter = Iterable(carts.toSeq: _*)
      val source = Source[ShoppingCart](iter)
      val bndStmt = new CassandraBind(prepInsert(session, schema), bndInsert)
      val sink = new CassandraSink(session)
      source.via(bndStmt).runWith(sink)
    }
  }

  "query a ShoppingCart" in {
    val source = TestSource.probe[UUID]
    val bndStmt = new CassandraBind(queryPrepStmt, bndQuery)
    val query = new CassandraQuery(session)
    val paging = new CassandraPaging(10)
    def toCarts: Flow[Seq[Row], Seq[ShoppingCart], NotUsed] = Flow[Seq[Row]].map(CassandraShoppingCart.mapRows)
    def sink = TestSink.probe[Seq[ShoppingCart]]
    val (pub, sub) = source.via(bndStmt).via(query).via(paging).via(toCarts).toMat(sink)(Keep.both).run()
    sub.request(1)
    pub.sendNext(cartId)
    val response = sub.expectNext()
    pub.sendComplete()
    sub.expectComplete()

    response shouldBe carts
  }

  "update a ShoppingCart item" in {
    val source = TestSource.probe[SetItems]
    val bndStmt = new CassandraBind(prepUpdateItems(session, schema), bndUpdateItems)
    val curriedErrorHandler = getConditionalError(rowToString) _
    val conditional = new CassandraConditional(session, curriedErrorHandler)
    def sink = TestSink.probe[Option[Row]]
    val (pub, sub) = source.via(bndStmt).via(conditional).toMat(sink)(Keep.both).run()
    sub.request(1)
    pub.sendNext(setItems)
    val response = sub.expectNext()
    pub.sendComplete()
    sub.expectComplete()

    response shouldBe None
  }

  "query a ShoppingCart with combined stages" in {
    val source = TestSource.probe[UUID]
    val query = new CassandraBoundQuery[UUID](session, 1, queryPrepStmt, bndQuery)
    val paging = new CassandraMappedPaging[ShoppingCart](10, mapRows)
    def sink = TestSink.probe[Seq[ShoppingCart]]
    val (pub, sub) = source.via(query).via(paging).toMat(sink)(Keep.both).run()
    sub.request(1)
    pub.sendNext(cartId)
    val response = sub.expectNext()
    pub.sendComplete()
    sub.expectComplete()

    response shouldBe updatedItemsCarts
  }

  val dispatcher = ActorAttributes.dispatcher("dendrites.blocking-dispatcher")

  "check and set a ShoppingCart owner" in {
    val iter = Iterable(setOwners.toSeq: _*)
    val setStmt = prepUpdateOwner(session, schema)
    val curriedCheckAndSetOwner = checkAndSetOwner(session, queryPrepStmt, setStmt) _
    val source = Source[SetOwner](iter)
    val sink = new CassandraRetrySink[SetOwner](RetryConfig, curriedCheckAndSetOwner)
      .withAttributes(dispatcher)
    source.runWith(sink)
  }

  "query a ShoppingCart after updating items and then owner" in {
    val source = TestSource.probe[UUID]
    val query = new CassandraBoundQuery[UUID](session, 1, queryPrepStmt, bndQuery)
    val paging = new CassandraMappedPaging[ShoppingCart](10, mapRows)
    def sink = TestSink.probe[Seq[ShoppingCart]]
    val (pub, sub) = source.via(query).via(paging).toMat(sink)(Keep.both).run()
    sub.request(1)
    pub.sendNext(cartId)
    val response = sub.expectNext()
    pub.sendComplete()
    sub.expectComplete()

    response shouldBe updatedCarts
  }

  "delete ShoppingCart " in {
    val iter = Iterable(cartIds.toSeq: _*)
    val source = Source[UUID](iter)
    val bndStmt = new CassandraBind(prepDelete(session, schema), bndDelete)
    val sink = new CassandraSink(session)
    source.via(bndStmt).runWith(sink)
  }

  override def afterAll() {
    dropSchema(session, schema)
    close(session, cluster)
  }
}
