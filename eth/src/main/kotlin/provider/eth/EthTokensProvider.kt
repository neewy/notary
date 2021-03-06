package provider.eth

import com.github.kittinunf.result.Result

/** Interface of an instance that provides with ethereum ERC20 token white list. */
interface EthTokensProvider {

    /** Returns token list in form of (Ethereum wallet -> token name) */
    fun getTokens(): Result<Map<String, String>, Exception>

    /** Return token precision by asset name */
    fun getTokenPrecision(name: String): Result<Int, Exception>

    /** Return token precision by asset name */
    fun getTokenAddress(name: String): Result<String, Exception>
}
