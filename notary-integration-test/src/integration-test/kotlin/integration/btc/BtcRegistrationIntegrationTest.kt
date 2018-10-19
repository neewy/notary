package integration.btc

import integration.helper.IntegrationHelperUtil
import jp.co.soramitsu.iroha.ModelCrypto
import kotlinx.coroutines.experimental.delay
import kotlinx.coroutines.experimental.runBlocking
import model.IrohaCredential
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import provider.btc.address.BtcAddressesProvider
import provider.btc.address.BtcRegisteredAddressesProvider
import registration.IrohaAccountCreator
import registration.btc.BtcRegistrationServiceInitialization
import registration.btc.BtcRegistrationStrategyImpl
import sidechain.iroha.CLIENT_DOMAIN
import sidechain.iroha.consumer.IrohaConsumerImpl
import sidechain.iroha.consumer.IrohaNetworkImpl
import sidechain.iroha.util.ModelUtil
import util.getRandomString
import java.math.BigInteger

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BtcRegistrationIntegrationTest {

    private val integrationHelper = IntegrationHelperUtil()

    private val btcRegistrationConfig = integrationHelper.configHelper.createBtcRegistrationConfig()

    private val btcRegistrationCredential = ModelUtil.loadKeypair(
        btcRegistrationConfig.registrationCredential.pubkeyPath,
        btcRegistrationConfig.registrationCredential.privkeyPath
    ).fold(
        { keypair ->
            IrohaCredential(btcRegistrationConfig.registrationCredential.accountId, keypair)
        },
        { ex -> throw ex }
    )

    private val irohaNetwork = IrohaNetworkImpl(btcRegistrationConfig.iroha.hostname, btcRegistrationConfig.iroha.port)

    private val btcClientCreatorConsumer = IrohaConsumerImpl(btcRegistrationCredential, irohaNetwork)

    private val btcRegistrationServiceInitialization = BtcRegistrationServiceInitialization(
        btcRegistrationConfig,
        BtcRegistrationStrategyImpl(btcAddressesProvider(), btcRegisteredAddressesProvider(), irohaAccountCreator())
    )

    init {
        btcRegistrationServiceInitialization.init()
        runBlocking { delay(5_000) }
    }

    private val btcTakenAddressesProvider = BtcRegisteredAddressesProvider(
        integrationHelper.testCredential,
        irohaNetwork,
        btcRegistrationConfig.registrationCredential.accountId,
        integrationHelper.accountHelper.notaryAccount.accountId
    )

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
     * Test US-001 Client registration
     * Note: Iroha must be deployed to pass the test.
     * @given new client
     * @when client name is passed to registration service
     * @then client has btc address in related Iroha account details
     */
    @Test
    fun testRegistration() {
        integrationHelper.preGenBtcAddress()
        integrationHelper.sendMultitransaction()
        val keypair = ModelCrypto().generateKeypair()
        val userName = String.getRandomString(9)
        val res = khttp.post(
            "http://127.0.0.1:${btcRegistrationConfig.port}/users",
            data = mapOf("name" to userName, "pubkey" to keypair.publicKey().hex())
        )
        assertEquals(200, res.statusCode)
        val registeredBtcAddress = String(res.content)
        btcTakenAddressesProvider.getRegisteredAddresses().fold({ addresses ->
            assertEquals("$userName@$CLIENT_DOMAIN", addresses[registeredBtcAddress])
        }, { ex -> fail("cannot get addresses", ex) })
        assertEquals(
            BigInteger.ZERO.toString(),
            integrationHelper.getIrohaAccountBalance("$userName@$CLIENT_DOMAIN", "btc#bitcoin")
        )
    }

    /**
     * Test US-002 Client registration
     * Note: Iroha must be deployed to pass the test.
     * @given multiple clients
     * @when client names are passed to registration service
     * @then all the clients have btc address in related Iroha account details
     */
    @Test
    fun testRegistrationMultiple() {
        val takenAddresses = HashSet<String>()
        val addressesToRegister = 3
        for (i in 1..addressesToRegister) {
            integrationHelper.preGenBtcAddress()
        }
        integrationHelper.sendMultitransaction()

        for (i in 1..addressesToRegister) {
            val keypair = ModelCrypto().generateKeypair()
            val userName = String.getRandomString(9)
            val res = khttp.post(
                "http://127.0.0.1:${btcRegistrationConfig.port}/users",
                data = mapOf("name" to userName, "pubkey" to keypair.publicKey().hex())
            )
            assertEquals(200, res.statusCode)
            val registeredBtcAddress = String(res.content)
            assertFalse(takenAddresses.contains(registeredBtcAddress))
            takenAddresses.add(registeredBtcAddress)
            btcTakenAddressesProvider.getRegisteredAddresses().fold({ addresses ->
                assertEquals("$userName@$CLIENT_DOMAIN", addresses[registeredBtcAddress])
            }, { ex -> fail("cannot get addresses", ex) })
            assertEquals(
                BigInteger.ZERO.toString(),
                integrationHelper.getIrohaAccountBalance("$userName@$CLIENT_DOMAIN", "btc#bitcoin")
            )
        }
    }

    /**
     * Test US-002 Client registration
     * Note: Iroha must be deployed to pass the test.
     * @given no registered btc addreses
     * @when client name is passed to registration service
     * @then client stays unregistered
     */
    @Test
    fun testRegistrationNoFree() {
        val clientsBeforeRegistration = btcTakenAddressesProvider.getRegisteredAddresses().get().size
        val keypair = ModelCrypto().generateKeypair()
        val userName = String.getRandomString(9)
        val res = khttp.post(
            "http://127.0.0.1:${btcRegistrationConfig.port}/users",
            data = mapOf("name" to userName, "pubkey" to keypair.publicKey().hex())
        )
        assertEquals(400, res.statusCode)
        assertEquals(clientsBeforeRegistration, btcTakenAddressesProvider.getRegisteredAddresses().get().size)

    }

    private fun btcAddressesProvider(): BtcAddressesProvider {
        return BtcAddressesProvider(
            btcRegistrationCredential,
            irohaNetwork,
            btcRegistrationConfig.mstRegistrationAccount,
            btcRegistrationConfig.notaryAccount
        )
    }

    private fun btcRegisteredAddressesProvider(): BtcRegisteredAddressesProvider {
        return BtcRegisteredAddressesProvider(
            btcRegistrationCredential,
            irohaNetwork,
            btcRegistrationCredential.accountId,
            btcRegistrationConfig.notaryAccount
        )
    }

    private fun irohaAccountCreator(): IrohaAccountCreator {
        return IrohaAccountCreator(
            btcClientCreatorConsumer,
            btcRegistrationConfig.notaryAccount,
            "bitcoin"
        )
    }
}
