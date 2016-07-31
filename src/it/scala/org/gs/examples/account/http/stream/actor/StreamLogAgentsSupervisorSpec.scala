/** Copyright 2016 Gary Struthers

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
package org.gs.examples.account.http.stream.actor

import akka.actor.{ActorSystem, Props}
import akka.event.{Logging, LoggingAdapter}
import akka.stream.ActorMaterializer
import akka.testkit.{ImplicitSender, TestActorRef, TestKit}
import com.twitter.algebird.{CMSHasher, DecayedValue, DecayedValueMonoid, HLL, QTreeSemigroup}
import com.twitter.algebird.CMSHasherImplicits._
import org.scalatest.{Matchers, WordSpecLike}
import org.scalatest.concurrent.PatienceConfiguration.Timeout
import org.scalatest.concurrent.ScalaFutures._
import org.scalatest.time.SpanSugar._
import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContext.Implicits.global
import scala.reflect.runtime.universe.TypeTag
import org.gs.algebird.AlgebirdConfigurer
import org.gs.algebird.BigDecimalField
import org.gs.algebird.cmsHasherBigDecimal
import org.gs.algebird.agent.Agents
import org.gs.algebird.typeclasses.{HyperLogLogLike, QTreeLike}
import org.gs.examples.account.{GetCustomerAccountBalances, Savings}
import org.gs.stream.actor.CallStream.CompleteMessage

/** 
  */
class StreamLogAgentsSupervisorSpec extends WordSpecLike with Matchers {

  implicit val system = ActorSystem("dendrites")
  implicit val materializer = ActorMaterializer()
  implicit val logger = Logging(system, getClass)
  val timeout = Timeout(3000 millis)

  "A StreamLogAgentsSupervisor of BigDecimals" should {
    "update all agents and return their latest values" in {
      val level = AlgebirdConfigurer.qTreeLevel
      implicit val qtSG = new QTreeSemigroup[BigDecimal](level)
      val agents = new Agents[BigDecimal]("test BigDecimal approximators agents")
      val props = StreamLogAgentsSupervisor.props[BigDecimal](agents)
      val supervisor = TestActorRef(props)
      val streamSuperRef = supervisor.getSingleChild("streamSuper")
      streamSuperRef ! GetCustomerAccountBalances(1, Set(Savings))
      Thread.sleep(6000)//Stream completes before agent updates
      val avgAgent = agents.avgAgent
      val cmsAgent = agents.cmsAgent
      val dvAgent = agents.dcaAgent
      val hllAgent = agents.hllAgent
      val qtAgent = agents.qtAgent
      val updateAvgFuture = avgAgent.agent.future()
      whenReady(updateAvgFuture, timeout) { result =>
        val count = result.count
        count should equal(3)
      }
    }
  }
/*
  test("Test request 3 account types") {
    val actors = Map(Checking -> Props[CheckingAccountProxy],
      MoneyMarket -> Props[MoneyMarketAccountProxy], Savings -> Props[SavingsAccountProxy])
    val props = Props(classOf[AccountBalanceRetriever], actors)
    system.actorOf(props) !
      GetCustomerAccountBalances(2, Set(Checking, Savings, MoneyMarket))
    receiveOne(10.seconds) match {
      case result: IndexedSeq[Product] ⇒ {
        assert(isElementEqual(result(0), 0, Checking))
        assert(isElementEqual(result(1), 0, Savings))
        assert(isElementEqual(result(2), 0, MoneyMarket))
      }
      case result ⇒ assert(false, s"Expect 3 AccountTypes, got $result")
    }
  }*/
}

