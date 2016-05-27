/**
  */
package org.gs

import _root_.akka.actor.ActorSystem
import _root_.akka.event.LoggingAdapter
import _root_.akka.http.scaladsl.Http
import _root_.akka.http.scaladsl.model.{ HttpEntity, HttpResponse, HttpRequest }
import _root_.akka.http.scaladsl.model.StatusCodes._
import _root_.akka.http.scaladsl.unmarshalling.Unmarshal
import _root_.akka.stream.Materializer
import com.typesafe.config.{ Config, ConfigFactory }
import scala.concurrent.{ ExecutionContextExecutor, Future }
import scala.concurrent.ExecutionContext.Implicits.global

/** Provides Class to create an HostConnectionPool. Also functions to create requests and handle
  * response
  *
  * @see [[http://typesafehub.github.io/config/latest/api/ "Config API"]]
  * @author Gary Struthers
  */
package object http {

  /** Get host URL config from a Config
		*
		* @param ipPath config key
    * @param portPath config key
    * @param config
    * @return config plus ip address and port number 
    */
  def getHostConfig(ipPath: String, portPath: String, config: Config = ConfigFactory.load()):
            (Config, String, Int) = {
    val ip = config.getString(ipPath)
    val port = config.getInt(portPath)
    (config, ip, port)
  }

  /** Get path from Config append to host URL
		*
		* @param pathPath config key
    * @param hostConfig config plus ip address and port number
    * @return URL string for host/path 
    */
  def configBaseUrl(pathPath: String, hostConfig: (Config, String, Int)): StringBuilder = {
    val config = hostConfig._1
    val ip = hostConfig._2
    val port = hostConfig._3
    val path = config.getString(pathPath)
    createUrl("http", ip, port, path)
  }

  /** Create URL string from components
    *
    * @param scheme http or https
    * @param domain IP address or domain name
    * @param port
    * @param path
    * @return StringBuilder
    */
  def createUrl(scheme: String, domain: String, port: Int, path: String): StringBuilder = {
    require(scheme == "http" || scheme == "https", s"scheme:$scheme must be http or https")
    val domainPattern = """[a-zA-Z0-9][a-zA-Z0-9-]{1,61}[a-zA-Z0-9]\.[a-zA-Z]{2,}""".r
    val ipPattern = """\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}""".r
    val goodDomain = if (domainPattern.findFirstIn(domain).isDefined ||
        ipPattern.findFirstIn(domain).isDefined) true else false
    require(goodDomain, s"domain:$domain looks invalid")
    val portRange = 0 to 65536
    require(portRange.contains(port), s"port:$port must be ${portRange.start} to ${portRange.end}")
    val pathPattern = """\/[/.a-zA-Z0-9-]+""".r
    require(pathPattern.findFirstIn(path).isDefined, s"path:path looks invalid")
    new StringBuilder(scheme).append("://").append(domain).append(':').append(port).append(path)
  }
  
  /** Transform case class to http GET query
    *  
    *  @param cc case class or tuple
    *  @param requestPath last part of path before '?', default is case class name
    *  @return field names and values as GET query string preceded with case class name? 
    */
  def caseClassToGetQuery(cc: Product)(requestPath: String = cc.productPrefix): StringBuilder = {
    val sb = new StringBuilder(requestPath)
    sb.append('?')
    val fields = ccToMap(cc).filterKeys(_ != "$outer")
    fields.foreach{
      case (key, value) => sb.append(key).append('=').append(value).append('&')
    }
    if(sb.last == '&') sb.setLength(sb.length() - 1)
    sb
  }

  /** Call server with GET query, case class is turned into Get query, appended to baseURL
		*
		* @see [[http://doc.akka.io/api/akka/2.4.6/#akka.http.scaladsl.Http$ "Http"]]
		* @example [[org.gs.examples.account.http.actor.CheckingAccountClient]]
		* 
    * @param cc case class 
    * @param baseURL
    * @param system implicit ActorSystem
    * @param materializer implicit Materializer
    * @return Future[HttpResponse]
    */
  def typedQuery(cc: Product, baseURL: StringBuilder)(implicit system: ActorSystem, 
          materializer: Materializer): Future[HttpResponse] = {
    val balancesQuery = caseClassToGetQuery(cc)()
    val uriS = (baseURL ++ balancesQuery).mkString
    Http().singleRequest(HttpRequest(uri = uriS))
  }
  
  /**	Map response to a Future Either Left for error, Right for good result
		*
	  * @see [[http://doc.akka.io/api/akka/2.4.6/#akka.http.scaladsl.model.HttpResponse "HttpResponse"]]
	  * @see [[http://doc.akka.io/api/akka/2.4.6/#akka.http.scaladsl.unmarshalling.Unmarshal "Unmarshal"]]
		* @example [[org.gs.examples.account.http.actor.CheckingAccountClient]]
	  * 
	  * @param caller future returned by query
    * @param mapLeft plain text response to Left
    * @param mapRight json response to Right
    * @param system implicit ActorSystem
    * @param logger implicit LoggingAdapter
    * @param materializer implicit Materializer
    * @return Future[Either[String, AnyRef]]
    */
def typedResponse(caller: Future[HttpResponse], 
                  mapLeft: (HttpEntity) => Future[Left[String, Nothing]], 
                  mapRight: (HttpEntity) => Future[Right[String, AnyRef]])
                 (implicit system: ActorSystem, logger: LoggingAdapter, 
                  materializer: Materializer): Future[Either[String, AnyRef]] = {

    caller.flatMap { response =>
      response.status match {
        case OK => {
          val st = response.entity.contentType.mediaType.subType
          st match {
            case "json"  => mapRight(response.entity)
            case "plain" => mapLeft(response.entity)
          }
        }
        case BadRequest => Future.successful(Left(s"FAIL bad request:${response.status}"))
        case _ => Unmarshal(response.entity).to[String].flatMap { entity =>
          val error = s"FAIL ${response.status} $entity"
          logger.error(error)
          Unmarshal(error).to[String].map(Left(_))
        }
      }
    }
  }

  /** Query server, map response
    *
    * Create a Partial Function by initializing first parameter list
    *
    * @example [[org.gs.examples.account.http.CheckingCallSpec]]
    *   
    * @param baseURL
    * @param mapLeft plain text response to Left
    * @param mapRight json response to Right
    * @param cc case class mapped to GET query
    * @param system implicit ActorSystem
    * @param logger implicit LoggingAdapter
    * @param materializer implicit Materializer
    * @return Future[Either[String, AnyRef]]
    */
  def typedQueryResponse(baseURL: StringBuilder, 
               mapLeft: (HttpEntity) => Future[Left[String, Nothing]], 
               mapRight: (HttpEntity) => Future[Right[String, AnyRef]])
              (cc: Product)
              (implicit system: ActorSystem, logger: LoggingAdapter, materializer: Materializer): 
               Future[Either[String, AnyRef]] = {
    
    val callFuture = typedQuery(cc, baseURL)
    typedResponse(callFuture, mapLeft, mapRight)
  }
}
