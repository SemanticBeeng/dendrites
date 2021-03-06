/**

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/
package com.github.garyaiki.dendrites.cassandra.stream

import akka.NotUsed
import akka.actor.ActorSystem
import akka.event.{Logging, LoggingAdapter}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import akka.stream.testkit.scaladsl.{TestSink, TestSource}
import com.datastax.driver.core.{Cluster, ResultSet, Row, Session}
import com.datastax.driver.core.policies.{DefaultRetryPolicy, LoggingRetryPolicy}
import java.util.UUID
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import scala.collection.immutable.Iterable
import scala.concurrent.ExecutionContext
import com.github.garyaiki.dendrites.cassandra.{Playlists, PlaylistSongConfig}
import com.github.garyaiki.dendrites.cassandra.Playlists.{bndInsert, bndQuery, Playlist}
import com.github.garyaiki.dendrites.cassandra.{close, connect, createCluster, createLoadBalancingPolicy, createSchema,
  dropSchema, initLoadBalancingPolicy, logMetadata, registerQueryLogger}

class CassandraPlaylistSpec extends WordSpecLike with Matchers with BeforeAndAfterAll {
  implicit val system = ActorSystem("dendrites")
  implicit val ec: ExecutionContext = system.dispatcher
  implicit val materializer = ActorMaterializer()
  implicit val logger = Logging(system, getClass)
  val myConfig = PlaylistSongConfig
  val schema = myConfig.keySpace
  var cluster: Cluster = null
  var session: Session = null
  var songId: UUID = null
  var plId: UUID = null
  var playlist: Playlist = null
  var playlists: Seq[Playlist] = null
  var playlistIds: Seq[UUID] = null

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
    val createSchemaRS = createSchema(session, schema, strategy, 3)
    songId = UUID.randomUUID()
    plId = UUID.randomUUID()
    playlist = Playlist(plId,"La Petite Tonkinoise","Bye Bye Blackbird","Joséphine Baker",songId)
    playlists = Seq(playlist)
    playlistIds = Seq(plId)
    val songTRS = Playlists.createTable(session, schema)
  }

  "A Cassandra Playlist client" should {
    "insert Playlists " in {
      val iter = Iterable(playlists.toSeq:_*)
      val source = Source[Playlist](iter)
      val bndStmt = new CassandraBind(Playlists.prepInsert(session, schema), bndInsert)
      val sink = new CassandraSink(session)
      source.via(bndStmt).runWith(sink)
    }
  }

  "query a Playlist" in {
      val source = TestSource.probe[UUID]
      val bndStmt = new CassandraBind(Playlists.prepQuery(session, schema), bndQuery)
      val query = new CassandraQuery(session)
      val paging = new CassandraPaging(10)
      def toPlaylists: Flow[Seq[Row], Seq[Playlist], NotUsed] = Flow[Seq[Row]].map(Playlists.mapRows)

      def sink = TestSink.probe[Seq[Playlist]]
      val (pub, sub) = source.via(bndStmt)
        .via(query).via(paging)
        .via(toPlaylists)
        .toMat(sink)(Keep.both).run()
      sub.request(1)
      pub.sendNext(plId)
      val response = sub.expectNext()
      pub.sendComplete()
      sub.expectComplete()

      response shouldBe playlists
  }

  override def afterAll() {
    dropSchema(session, schema)
    close(session, cluster)
  }
}
