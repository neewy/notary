package sidechain.eth

import com.github.kittinunf.result.Result
import hu.akarnokd.rxjava.interop.RxJavaInterop
import io.reactivex.Observable
import mu.KLogging
import org.web3j.protocol.Web3j
import org.web3j.protocol.core.DefaultBlockParameter
import org.web3j.protocol.core.methods.response.EthBlock
import sidechain.ChainListener
import java.math.BigInteger

/**
 * Implementation of [ChainListener] for Ethereum sidechain
 * @param web3 - notary.endpoint of Ethereum client
 * @param confirmationPeriod - number of block to consider block final
 */
class EthChainListener(
    private val web3: Web3j,
    private val confirmationPeriod: BigInteger
) : ChainListener<EthBlock> {

    init {
        logger.info {
            """Init EthChainListener:
                |confirmation period: $confirmationPeriod
            """.trimMargin()
        }
    }

    /** Keep counting blocks to prevent double emitting in case of chain reorganisation */
    private var lastBlock = confirmationPeriod

    override fun getBlockObservable(): Result<Observable<EthBlock>, Exception> {
        return Result.of {
            // convert rx1 to rx2
            RxJavaInterop.toV2Observable(web3.blockObservable(false))
                // skip up to confirmationPeriod blocks in case of chain reorganisation
                .filter { lastBlock < it.block.number }
                .map {
                    lastBlock = it.block.number
                    val block = web3.ethGetBlockByNumber(
                        DefaultBlockParameter.valueOf(
                            it.block.number - confirmationPeriod
                        ), true
                    ).send()
                    block
                }
        }
    }

    /**
     * Logger
     */
    companion object : KLogging()
}
