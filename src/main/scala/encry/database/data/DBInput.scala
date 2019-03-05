package encry.database.data

import org.encryfoundation.common.Algos
import org.encryfoundation.common.transaction.Input

case class DBInput(bxId: String, txId: String, contract: String, proofs: String)

object DBInput {

  //todo: remove input.contract.toString
  def apply(input: Input, txId: String): DBInput =
    new DBInput(
      Algos.encode(input.boxId),
      txId,
      input.contract.toString,
      input.proofs.map(_.toString).mkString(",")
    )

  def apply(bxId: String, txId: String, contract: String, proofs: String): DBInput = new DBInput(bxId, txId, contract, proofs)
}