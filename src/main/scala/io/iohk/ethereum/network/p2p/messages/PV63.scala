package io.iohk.ethereum.network.p2p.messages

import akka.util.ByteString
import io.iohk.ethereum.mpt.HexPrefix.{decode => hpDecode, encode => hpEncode, nibblesToBytes}
import io.iohk.ethereum.network.p2p.Message
import io.iohk.ethereum.rlp.RLPImplicits._
import io.iohk.ethereum.rlp._
import org.spongycastle.util.encoders.Hex

import scala.util.Try

object PV63 {

  object GetNodeData {
    implicit val rlpEndDec = new RLPEncoder[GetNodeData] with RLPDecoder[GetNodeData] {
      override def encode(obj: GetNodeData): RLPEncodeable = {
        import obj._
        commonMptHashes: RLPList
      }

      override def decode(rlp: RLPEncodeable): GetNodeData = rlp match {
        case rlpList: RLPList => GetNodeData(rlpList.items.map(byteStringEncDec.decode))
        case _ => throw new RuntimeException("Cannot decode GetNodeData")
      }
    }

    val code: Int = Message.SubProtocolOffset + 0x0d
  }

  case class GetNodeData(commonMptHashes: Seq[ByteString]) extends Message {
    override def code: Int = GetNodeData.code

    override def toString: String = {
      s"""GetNodeData{
         |hashes: ${commonMptHashes.map(e => Hex.toHexString(e.toArray[Byte]))}
         |}
       """.stripMargin
    }
  }

  object NodeData {
    implicit val rlpEndDec = new RLPEncoder[NodeData] with RLPDecoder[NodeData] {
      override def encode(obj: NodeData): RLPEncodeable = {
        import obj._

        RLPList(values.map {
          case Left(node) => RLPValue(MptNode.rlpEndDec.encode(node))
          case Right(byteValue) => RLPValue(byteValue.toArray[Byte])
        }: _*)
      }

      override def decode(rlp: RLPEncodeable): NodeData = rlp match {
        case rlpList: RLPList =>
          NodeData(rlpList.items.map { e =>
            Try {
              val v = rawDecode(e: Array[Byte])
              Left(MptNode.rlpEndDec.decode(v))
            }.getOrElse(Right(ByteString(e:Array[Byte])))
          })
        case _ => throw new RuntimeException("Cannot decode NodeData")
      }
    }

    val code: Int = Message.SubProtocolOffset + 0x0e
  }

  case class NodeData(values: Seq[Either[MptNode, ByteString]]) extends Message {
    override def code: Int = NodeData.code

    override def toString: String = {
      val v = values.map(v => v.fold(_.toString, b => s"BytString(${Hex.toHexString(b.toArray[Byte])})"))

      s"""NodeData{
         |values: $v
         |}
       """.stripMargin
    }
  }

  object MptNode {
    implicit val rlpEndDec = new RLPEncoder[MptNode] with RLPDecoder[MptNode] {
      override def encode(obj: MptNode): RLPEncodeable = {
        obj match {
          case n: MptLeaf =>
            import n._
            RLPList(hpEncode(keyNibbles.toArray[Byte], isLeaf = true), Account.rlpEndDec.encode(value))
          case n: MptExtension =>
            import n._
            RLPList(hpEncode(keyNibbles.toArray[Byte], isLeaf = false), childHash)
          case n: MptBranch =>
            import n._
            RLPList(childHashes :+ terminator)
        }
      }

      override def decode(rlp: RLPEncodeable): MptNode = rlp match {
        case rlpList: RLPList if rlpList.items.length == 17 =>
          MptBranch(rlpList.items.take(16).map(byteStringEncDec.decode), byteStringEncDec.decode(rlpList.items(16)))
        case RLPList(hpEncoded, value) =>
          hpDecode(hpEncoded:Array[Byte]) match {
            case (decoded, true) => MptLeaf(ByteString(decoded), Account.rlpEndDec.decode(rawDecode(value)))
            case (decoded, false) => MptExtension(ByteString(decoded), byteStringEncDec.decode(value))
          }
        case _ =>
          throw new RuntimeException("Cannot decode NodeData")
      }
    }
  }

  trait MptNode

  case class MptBranch(childHashes: Seq[ByteString], terminator: ByteString) extends MptNode {
    require(childHashes.length == 16, "MptBranch childHashes length have to be 16")

    override def toString: String = {
      s"""MptBranch{
         |childHashes: ${childHashes.map(e => Hex.toHexString(e.toArray[Byte])).mkString("(", ",\n", ")")}
         |terminator: ${Hex.toHexString(terminator.toArray[Byte])}
         |}
       """.stripMargin
    }
  }

  case class MptExtension(keyNibbles: ByteString, childHash: ByteString) extends MptNode { //todo do they exists in messages?
    override def toString: String = {
      s"""MptExtension{
         |key nibbles: $keyNibbles
         |key nibbles length: ${keyNibbles.length}
         |childHash: ${Hex.toHexString(childHash.toArray[Byte])}
         |}
       """.stripMargin
    }
  }

  case class MptLeaf(keyNibbles: ByteString, value: Account) extends MptNode {
    override def toString: String = {
      s"""MptLeaf{
         |key nibbles: $keyNibbles
         |key nibbles length: ${keyNibbles.length}
         |value: $value
         |}
       """.stripMargin
    }
  }

  object Account{
    implicit val rlpEndDec = new RLPEncoder[Account] with RLPDecoder[Account] {
      override def encode(obj: Account): RLPEncodeable = {
        import obj._
        RLPList(nonce, balance, byteStringEncDec.encode(storageRoot), byteStringEncDec.encode(codeHash))
      }

      override def decode(rlp: RLPEncodeable): Account = rlp match {
        case RLPList(nonce, balance, storageRoot, codeHash) =>
          Account(nonce, balance, byteStringEncDec.decode(storageRoot), byteStringEncDec.decode(codeHash))
        case _ => throw new RuntimeException("Cannot decode Account")
      }
    }
  }

  case class Account(nonce: BigInt, balance: BigInt, storageRoot: ByteString, codeHash: ByteString){
    override def toString: String = {
      s"""Account{
         |nonce: $nonce
         |balance: $balance wei
         |storageRoot: ${Hex.toHexString(storageRoot.toArray[Byte])}
         |codeHash: ${Hex.toHexString(codeHash.toArray[Byte])}
         |}
       """.stripMargin
    }
  }

  object GetReceipts {
    implicit val rlpEndDec = new RLPEncoder[GetReceipts] with RLPDecoder[GetReceipts] {
      override def encode(obj: GetReceipts): RLPEncodeable = {
        import obj._
        blockHashes: RLPList
      }

      override def decode(rlp: RLPEncodeable): GetReceipts = rlp match {
        case rlpList: RLPList => GetReceipts(rlpList.items.map(byteStringEncDec.decode))
        case _ => throw new RuntimeException("Cannot decode GetReceipts")
      }
    }

    val code: Int = Message.SubProtocolOffset + 0x0f
  }

  case class GetReceipts(blockHashes: Seq[ByteString]) extends Message {
    override def code: Int = GetReceipts.code

    override def toString: String = {
      s"""GetReceipts{
         |blockHashes: ${blockHashes.map(e => Hex.toHexString(e.toArray[Byte]))}
         |}
       """.stripMargin
    }
  }

  object Receipts {
    implicit val rlpEndDec = new RLPEncoder[Receipts] with RLPDecoder[Receipts] {
      override def encode(obj: Receipts): RLPEncodeable = {
        import obj._
        RLPList(receiptsForBlocks.map(r => RLPList(r.map(Receipt.rlpEndDec.encode): _*)): _*)
      }

      override def decode(rlp: RLPEncodeable): Receipts = rlp match {
        case rlpList: RLPList => Receipts(rlpList.items.collect { case r: RLPList => r.items.map(Receipt.rlpEndDec.decode) })
        case _ => throw new RuntimeException("Cannot decode Receipts")
      }
    }

    val code: Int = Message.SubProtocolOffset + 0x10
  }

  case class Receipts(receiptsForBlocks: Seq[Seq[Receipt]]) extends Message {
    override def code: Int = Receipts.code
  }

  object Receipt {
    implicit val rlpEndDec = new RLPEncoder[Receipt] with RLPDecoder[Receipt] {
      override def encode(obj: Receipt): RLPEncodeable = {
        import obj._
        RLPList(postTransactionStateHash, cumulativeGasUsed, logsBloomFilter, RLPList(logs.map(TransactionLog.rlpEndDec.encode): _*))
      }

      override def decode(rlp: RLPEncodeable): Receipt = rlp match {
        case RLPList(postTransactionStateHash, cumulativeGasUsed, logsBloomFilter, logs: RLPList) =>
          Receipt(byteStringEncDec.decode(postTransactionStateHash), cumulativeGasUsed,
            byteStringEncDec.decode(logsBloomFilter), logs.items.map(TransactionLog.rlpEndDec.decode))
        case _ => throw new RuntimeException("Cannot decode Receipt")
      }
    }
  }

  case class Receipt(
    postTransactionStateHash: ByteString,
    cumulativeGasUsed: BigInt,
    logsBloomFilter: ByteString,
    logs: Seq[TransactionLog]
  ) {
    override def toString: String = {
      s"""
         |Receipt{
         |postTransactionStateHash: ${Hex.toHexString(postTransactionStateHash.toArray[Byte])}
         |cumulativeGasUsed: $cumulativeGasUsed
         |logsBloomFilter: ${Hex.toHexString(logsBloomFilter.toArray[Byte])}
         |logs: $logs
         |}
       """.stripMargin
    }
  }

  object TransactionLog {
    implicit val rlpEndDec = new RLPEncoder[TransactionLog] with RLPDecoder[TransactionLog] {
      override def encode(obj: TransactionLog): RLPEncodeable = {
        import obj._
        RLPList(loggerAddress, RLPList(logTopics.map(byteStringEncDec.encode): _*), data)
      }

      override def decode(rlp: RLPEncodeable): TransactionLog = rlp match {
        case RLPList(loggerAddress, logTopics: RLPList, data) =>
          TransactionLog(byteStringEncDec.decode(loggerAddress), logTopics.items.map(e => byteStringEncDec.decode(e)), byteStringEncDec.decode(data))
        case _ => throw new RuntimeException("Cannot decode TransactionLog")
      }
    }
  }

  case class TransactionLog(loggerAddress: ByteString, logTopics: Seq[ByteString], data: ByteString) {
    override def toString: String = {
      s"""TransactionLog{
         |loggerAddress: ${Hex.toHexString(loggerAddress.toArray[Byte])}
         |logTopics: ${logTopics.map(e => Hex.toHexString(e.toArray[Byte]))}
         |data: ${Hex.toHexString(data.toArray[Byte])}
         |}
       """.stripMargin
    }
  }

}