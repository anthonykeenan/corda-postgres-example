package net.corda.samples.carinsurance

import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.readAllLines
import net.corda.core.utilities.getOrThrow
import net.corda.node.services.Permissions
import net.corda.samples.carinsurance.flows.InsuranceInfo
import net.corda.samples.carinsurance.flows.IssueInsurance
import net.corda.samples.carinsurance.flows.VehicleInfo
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.NodeHandle
import net.corda.testing.driver.driver
import net.corda.testing.node.TestCordapp
import net.corda.testing.node.User
import org.junit.Assert
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path

class DriverBasedTest {
    companion object {
        const val POSTGRES_PASSWORD = "pass1234"
    }

    private val postgresContainer = CordaPostgresDockerContainer("localhost", "5432", "postgres", "pass1234")
    private val insurerName = CordaX500Name("Insurer", "London", "GB")
    private val insureeName = CordaX500Name("Insuree", "New York", "US")

    private fun checkLogFilesForSqlErrors(directory: Path) {
        // Check for sql errors
        val list = Files.list(directory)
        list.filter { it.fileName.toString().contains("diagnostic-") }
            .forEach {
                if (it.readAllLines().any { text -> text.contains("Error executing DDL") }) {
                    Assert.fail("Error creating schema - check node diagnostics log for more info: $it")
                }
            }
    }

    private fun getPostgresSettings(user: String): Map<String, String> {
        return mapOf(
            "dataSourceProperties.dataSource.url" to postgresContainer.connectionString,
            "dataSourceProperties.dataSource.user" to user,
            "dataSourceProperties.dataSource.password" to postgresContainer.password,
            "dataSourceProperties.dataSource.currentSchema" to "${user}_schema",
            "dataSourceProperties.dataSourceClassName" to "org.postgresql.ds.PGSimpleDataSource"
        )
    }

    private fun getPermissions(): Set<String> {
        return setOf(
            Permissions.startFlow<IssueInsurance>(),
            Permissions.invokeRpc("vaultTrackBy"),
            Permissions.invokeRpc("wellKnownPartyFromX500Name")
        )
    }

    private fun createRpcUser(nodeName: String): User {
        return User("${nodeName}User", "testPassword1", permissions = getPermissions())
    }

    val car = VehicleInfo(
        "I4U64FY56I48Y",
        "165421658465465",
        "BMW",
        "M3",
        "MPower",
        "Black",
        "gas"
    )

    val policy1 = InsuranceInfo(
        "8742",
        2000,
        18,
        49,
        car
    )

    @Test
    fun `node postgres test`() {
        try {
            postgresContainer.start(listOf("notary", "insurer", "insuree"))
            driver(
                DriverParameters(
                    notaryCustomOverrides = getPostgresSettings("notary"),
                    cordappsForAllNodes = setOf(
                        TestCordapp.findCordapp("net.corda.samples.carinsurance.flows"),
                        TestCordapp.findCordapp("net.corda.samples.carinsurance.contracts")
                    ),
                    // This setting is required to force Corda to validate the schema on startup (which is what happens in production) -
                    // this helps to check the migration files will work in production
                    allowHibernateToManageAppSchema = false,
                    // This setting is required to force schemas to be created
                    inMemoryDB = false
                )
            ) {
                val (insurerHandle, insureeHandle) = listOf(
                    startNode(
                        providedName = insurerName,
                        rpcUsers = listOf(createRpcUser("insurer")),
                        customOverrides = getPostgresSettings("insurer")
                    ),
                    startNode(
                        providedName = insureeName,
                        rpcUsers = listOf(createRpcUser("insuree")),
                        customOverrides = getPostgresSettings("insuree")
                    )
                ).map { it.getOrThrow() }

                // If the SQL fails with errors, they get logged in the diagnostics log file, but don't always cause the node not to start up
                listOf(insurerName, insureeName)
                    .forEach {
                        checkLogFilesForSqlErrors(baseDirectory(it).resolve("logs"))
                    }

                val insureeIdentity = insureeHandle.nodeInfo.legalIdentities[0]
                insurerHandle.rpc.startFlowDynamic(
                    IssueInsurance::class.java,
                    policy1,
                    insureeIdentity
                ).use { it.returnValue.getOrThrow() }
            }
        } catch (ex: Exception) {
            Assert.fail(ex.message)
        } finally {
            postgresContainer.stop()
        }
    }
}
