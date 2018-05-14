package notary

/**
 * Class represents [Notary] intention to [sideChain.iroha.consumer.IrohaConsumer] to add transaction
 * @param creator account id of transaction creator
 * @param commands commands to be sent to Iroha
 */
class IrohaTransaction(val creator: String, val commands: List<IrohaCommand>)