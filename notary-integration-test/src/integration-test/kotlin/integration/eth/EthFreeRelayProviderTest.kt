package integration.eth

import com.github.kittinunf.result.failure
import com.github.kittinunf.result.success
import integration.helper.IntegrationHelperUtil
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.assertEquals
import provider.eth.EthFreeRelayProvider
import sidechain.iroha.consumer.IrohaConsumerImpl
import sidechain.iroha.consumer.IrohaNetworkImpl
import sidechain.iroha.util.ModelUtil.setAccountDetail
import java.io.File

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EthFreeRelayProviderTest {

    /** Test configurations */
    val integrationHelper = IntegrationHelperUtil()

    val testConfig = integrationHelper.configHelper.testConfig

    private val irohaNetwork = IrohaNetworkImpl(testConfig.iroha.hostname, testConfig.iroha.port)

    /** Iroha consumer */
    private val irohaConsumer = IrohaConsumerImpl(integrationHelper.testCredential, irohaNetwork)

    /** Iroha transaction creator */
    val creator = integrationHelper.testCredential.accountId


    @BeforeAll
    fun setUp() {
        integrationHelper.sendMultitransaction()
    }

    @AfterAll
    fun dropDown() {
        integrationHelper.close()
        irohaNetwork.close()
    }

    /**
     * @given Iroha network running and Iroha master account with attribute ["eth_wallet", "free"] set by master account
     * @when getRelay() of FreeRelayProvider is called
     * @then "eth_wallet" attribute key is returned
     */
    @Test
    fun getFreeWallet() {
        val ethFreeWallet = "eth_free_wallet_stub"

        setAccountDetail(irohaConsumer, integrationHelper.accountHelper.notaryAccount.accountId, ethFreeWallet, "free")
            .failure { fail(it) }

        val freeWalletsProvider =
            EthFreeRelayProvider(
                integrationHelper.testCredential,
                irohaNetwork,
                integrationHelper.accountHelper.notaryAccount.accountId,
                creator
            )
        val result = freeWalletsProvider.getRelay()

        assertEquals(ethFreeWallet, result.get())
    }

    /**
     * @given Iroha network running and Iroha master account
     * @when getRelay() of FreeRelayProvider is called with wrong master account
     * @then "eth_wallet" attribute key is returned
     */
    @Test
    fun getFreeWalletException() {
        val wrongMasterAccount = "wrong@account"

        val freeWalletsProvider =
            EthFreeRelayProvider(integrationHelper.testCredential, irohaNetwork, creator, wrongMasterAccount)
        freeWalletsProvider.getRelay()
            .success { fail { "should return Exception" } }
    }

    /**
     * @given Iroha network running, file with relay addresses is created
     * @when Relays are imported from the file
     * @then Free wallet provider returns same relays as in the file
     */
    @Test
    fun testStorageFromFile() {
        val relayHolder = File.createTempFile("relay", "free")
        relayHolder.deleteOnExit()
        val existingRelays = setOf(
            "0x6fab8fe8e0e5f4a3e8b2ff794e023fd359137f35",
            "0x2d864560b9b48c99c633427c67020c8f883be360",
            "0x20d0b61725d279c0e4fd73e059ab163f2aea0761"
        )
        existingRelays.map { relayHolder.appendText(it + System.lineSeparator()) }

        integrationHelper.importRelays(relayHolder.absolutePath)

        val freeWalletsProvider =
            EthFreeRelayProvider(
                integrationHelper.testCredential,
                irohaNetwork,
                integrationHelper.accountHelper.notaryAccount.accountId,
                integrationHelper.accountHelper.registrationAccount.accountId
            )

        freeWalletsProvider.getRelays()
            .fold(
                { assertEquals(existingRelays, it) },
                { ex -> fail("result has exception", ex) }
            )
    }
}
