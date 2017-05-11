package io.iohk.ethereum.jsonrpc

import io.circe.Json.JString
import org.json4s.JsonAST._
import org.json4s.JsonDSL._
import org.scalatest.{FlatSpec, Matchers}

import scala.concurrent.Await
import scala.concurrent.duration.Duration

class JsonRpcControllerSpec extends FlatSpec with Matchers {

  "JsonRpcController" should "handle valid sha3 request" in new TestSetup {
    val rpcRequest = JsonRpcRequest("2.0", "web3_sha3", Some(JArray(JString("0x1234") :: Nil)), Some(1))

    val response = Await.result(jsonRpcController.handleRequest(rpcRequest), Duration.Inf)

    response.jsonrpc shouldBe "2.0"
    response.id shouldBe JInt(1)
    response.error shouldBe None
    response.result shouldBe Some(JString("0x56570de287d73cd1cb6092bb8fdee6173974955fdef345ae579ee9f475ea7432"))
  }

  it should "fail when invalid request is received" in new TestSetup {
    val rpcRequest = JsonRpcRequest("2.0", "web3_sha3", Some(JArray(JString("asdasd") :: Nil)), Some(1))

    val response = Await.result(jsonRpcController.handleRequest(rpcRequest), Duration.Inf)

    response.jsonrpc shouldBe "2.0"
    response.id shouldBe JInt(1)
    response.error shouldBe Some(JsonRpcErrors.InvalidParams.copy(message = "Data 'asdasd' should have 0x prefix"))
  }

  it should "handle clientVersion request" in new TestSetup {
    val rpcRequest = JsonRpcRequest("2.0", "web3_clientVersion", None, Some(1))

    val response = Await.result(jsonRpcController.handleRequest(rpcRequest), Duration.Inf)

    response.jsonrpc shouldBe "2.0"
    response.id shouldBe JInt(1)
    response.error shouldBe None
    response.result shouldBe Some(JString("etc-client/v0.1"))
  }

  it should "eth_protocolVersion" in new TestSetup {
    val rpcRequest = JsonRpcRequest("2.0", "eth_protocolVersion", None, Some(1))

    val response = Await.result(jsonRpcController.handleRequest(rpcRequest), Duration.Inf)

    response.jsonrpc shouldBe "2.0"
    response.id shouldBe JInt(1)
    response.error shouldBe None
    response.result shouldBe Some(JString("0x3f"))
  }

  trait TestSetup {
    val web3Service = new Web3Service
    val ethService = new EthService
    val jsonRpcController = new JsonRpcController(web3Service, ethService)
  }

}