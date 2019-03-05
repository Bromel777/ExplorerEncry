package encry.database

import java.net.InetSocketAddress
import cats.implicits._
import com.typesafe.scalalogging.StrictLogging
import doobie.free.connection.ConnectionIO
import doobie.util.update.Update
import doobie.postgres.implicits._
import doobie.implicits._
import encry.blockchain.modifiers.{Block, Header, Transaction}
import encry.blockchain.nodeRoutes.InfoRoute
import encry.database.data._

object Queries extends StrictLogging {

  def proccessBlock(block: Block, node: InetSocketAddress): ConnectionIO[Int] = for {
    header <- insertHeaderQuery(block.header)
    nodeToHeader <- insertNodeToHeader(block.header, node)
    txs <- insertTransactionsQuery(block)
    inputs <- insertInputsQuery(block.getDBInputs)
    inputsToNode <- insertInputToNodeQuery(block.getDBInputs, node)
    nonActiveOutputs <- markOutputsAsNonActive(block.getDBInputs)
    tokens <- insertTokens(block.getDbOutputs)
    accounts <- insertAccounts(block.getDbOutputs)
    outputs <- insertOutputsQuery(block.getDbOutputs)
    outputsToNode <- insertOutputToNodeQuery(block.getDbOutputs, node)
    updateNode <- updateNode(block, node)
  } yield header + nodeToHeader + txs + inputs + inputsToNode + nonActiveOutputs + tokens + accounts + outputs + outputsToNode

  def nodeInfoQuery(addr: InetSocketAddress): ConnectionIO[Option[Node]] = {
    val test = addr.getAddress.getHostName
    sql"""SELECT * FROM public.nodes WHERE ip = $test;""".query[Node].option
  }

  def insertNode(addr: InetSocketAddress, nodeInfo: InfoRoute): ConnectionIO[Int] = {
    val nodeIns = Node(addr, nodeInfo)
    val query = "INSERT INTO public.nodes (ip, status, lastFullBlock, lastFullHeight) VALUES(?, ?, ?, ?) ON CONFLICT (ip) DO " +
      "UPDATE SET status = true"
    Update[Node](query).run(nodeIns)
  }

  def updateNode(block: Block, address: InetSocketAddress): ConnectionIO[Int] = {
    val query = "UPDATE public.nodes SET lastFullBlock = ?, lastFullHeight = ? WHERE ip = ?"
    Update[(String, Int, String)](query).run(block.header.id, block.header.height, address.getAddress.getHostName)
  }

  def insertHeaderQuery(header: Header): ConnectionIO[Int] = {
    val query: String =
      s"""
        |INSERT INTO public.headers (id, version, parent_id, adProofsRoot, stateRoot, transactionsRoot, timestamp, height, nonce,
        |       difficulty, equihashSolution)
        |VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT DO NOTHING;
      """.stripMargin
    Update[Header](query).run(header)
  }

  private def insertTransactionsQuery(block: Block): ConnectionIO[Int] = {
    val txs: List[DBTransaction] = block.payload.txs.map(tx => DBTransaction(tx, block.header.id))
    val query: String =
      """
        |INSERT INTO public.transactions (id, fee, timestamp, proof, coinbase, blockId)
        |VALUES (?, ?, ?, ?, ?, ?) ON CONFLICT DO NOTHING;
        |""".stripMargin
    Update[DBTransaction](query).updateMany(txs)
  }

  private def insertInputsQuery(inputs: List[DBInput]): ConnectionIO[Int] = {
    val query: String =
      """
        |INSERT INTO public.inputs (bxId, txId, contract, proofs)
        |VALUES (?, ?, ?, ?) ON CONFLICT DO NOTHING;
        |""".stripMargin
    Update[DBInput](query).updateMany(inputs)
  }

  private def markOutputsAsNonActive(inputs: List[DBInput]): ConnectionIO[Int] = {
    val query: String =
      """
        |UPDATE public.outputs SET isActive = false WHERE id = ?
        |""".stripMargin
    Update[String](query).updateMany(inputs.map(_.bxId))
  }

  private def insertInputToNodeQuery(inputs: List[DBInput], node: InetSocketAddress): ConnectionIO[Int] = {
    val inputsToNodes = inputs.map(input => InputToNode(input, node))
    val query: String =
      """
        |INSERT INTO public.inputsToNodes (inputId, nodeIp)
        |VALUES (?, ?) ON CONFLICT DO NOTHING;
        |""".stripMargin
    Update[InputToNode](query).updateMany(inputsToNodes)
  }

  private def insertOutputsQuery(outputs: List[DBOutput]): ConnectionIO[Int] = {
    val query: String =
      """
        |INSERT INTO public.outputs (id, txId, monetaryValue, coinId, contractHash, data, isActive)
        |VALUES (?, ?, ?, ?, ?, ?, ?) ON CONFLICT DO NOTHING;
        |""".stripMargin
    Update[DBOutput](query).updateMany(outputs)
  }

  private def insertTokens(outputs: List[DBOutput]): ConnectionIO[Int] = {
    val tokens = outputs.map(output => Token(output.coinId))
    val query: String =
      """
        |INSERT INTO public.tokens (id)
        |VALUES (?) ON CONFLICT DO NOTHING;
        |""".stripMargin
    Update[Token](query).updateMany(tokens)
  }

  private def insertAccounts(outputs: List[DBOutput]): ConnectionIO[Int] = {
    val accounts = outputs.map(output => Account(output.contractHash))
    val query: String =
      """
        |INSERT INTO public.accounts (contractHash)
        |VALUES (?) ON CONFLICT DO NOTHING;
        |""".stripMargin
    Update[Account](query).updateMany(accounts)
  }

  private def insertOutputToNodeQuery(outputs: List[DBOutput], node: InetSocketAddress): ConnectionIO[Int] = {
    val outputsToNodes = outputs.map(output => data.OutputToNode(output.id, node.getAddress.getHostAddress))
    val query: String =
      """
        |INSERT INTO public.outputsToNodes (outputId, nodeIp)
        |VALUES (?, ?) ON CONFLICT DO NOTHING;
        |""".stripMargin
    Update[OutputToNode](query).updateMany(outputsToNodes)
  }

  def insertNodeToHeader(header: Header, addr: InetSocketAddress): ConnectionIO[Int] = {
    val headerToNode = HeaderToNode(header.id, addr.getAddress.getHostAddress)
    val query: String =
      s"""
         |INSERT INTO public.headerToNode (id, nodeIp)
         |VALUES(?, ?) ON CONFLICT DO NOTHING;
      """.stripMargin
    Update[HeaderToNode](query).run(headerToNode)
  }
}
