package net.corda.samples.carinsurance

import com.spotify.docker.client.DefaultDockerClient
import com.spotify.docker.client.DockerClient
import com.spotify.docker.client.messages.ContainerConfig
import com.spotify.docker.client.messages.HostConfig
import com.spotify.docker.client.messages.PortBinding
import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.readAllLines
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.seconds
import net.corda.node.services.Permissions
import net.corda.testing.core.TestIdentity
import net.corda.testing.driver.DriverDSL
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.NodeHandle
import net.corda.testing.driver.driver
import net.corda.testing.node.TestCordapp
import net.corda.testing.node.User
import net.corda.testing.node.internal.FINANCE_CORDAPPS
import org.junit.Assert
import org.junit.Test
import java.io.BufferedReader
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.Future
import kotlin.test.assertEquals

class CordaPostgresDockerContainer(val host: String, val port: String, val database: String, val password: String) {
    val connectionString get() = "jdbc:postgresql://$host:$port/$database"

    private lateinit var containerId: String

    private val dockerClient: DockerClient = DefaultDockerClient.fromEnv().build()
            ?: throw Exception("Unable to initialise docker client")

    private fun waitUntilStarted(): Boolean {
        var timeout = 200.seconds.toMillis()

        while (timeout > 0) {
            dockerClient.logs(containerId, DockerClient.LogsParam.stdout(), DockerClient.LogsParam.stderr()).use {
                val contents = it.readFully()
                if (contents.contains("server started")) {
                    return true
                }
            }

            timeout -= 10.seconds.toMillis()
            Thread.sleep(10.seconds.toMillis())
        }
        return false
    }

    fun createSchemasAndUsers(nodes: List<String>) {
        val userFile = this::class.java.classLoader.getResourceAsStream("schematemplate.sql")!!
        val content = userFile.bufferedReader().use(BufferedReader::readText)
        val sqlFile = StringBuilder()
        nodes.forEach {
            sqlFile.append(content.replace("$", it))
        }

        println(sqlFile)

        val schemaFileDir = Files.createTempDirectory("user-sql-folder")
        schemaFileDir.resolve("schemasetup.sql").toFile().writeText(sqlFile.toString())
        dockerClient.copyToContainer(schemaFileDir, containerId, "./")

        val execCreation = dockerClient.execCreate(
                containerId,
                arrayOf("psql", "postgres", "-h", host, "-d", "postgres", "-a", "-f", "schemasetup.sql"),
                DockerClient.ExecCreateParam.attachStdout(),
                DockerClient.ExecCreateParam.attachStderr()
        )
        val logStream = dockerClient.execStart(execCreation.id())
        println(logStream.readFully())
    }

    private fun createPostgresContainer(): String {
        dockerClient.pull("postgres:latest")

        val hostConfig = HostConfig
                .builder()
                .portBindings(mapOf(port to listOf(PortBinding.of("0.0.0.0", port))))
                .build()

        val config = ContainerConfig.builder()
                .hostConfig(hostConfig)
                .image("postgres:latest")
                .exposedPorts(port)
                .env(listOf("POSTGRES_PASSWORD=$password"))
                .build()

        val creation = dockerClient.createContainer(config)
        return creation.id()
                ?: throw Exception("Unable to initialise postgres container")
    }

    fun start(nodes: List<String>) {
        containerId = createPostgresContainer()
        dockerClient.startContainer(containerId)
        if (!waitUntilStarted()) {
            throw Exception("Postgres server failed to start up")
        }
        createSchemasAndUsers(nodes)
    }

    fun stop() {
        dockerClient.killContainer(containerId)
        dockerClient.removeContainer(containerId)
        dockerClient.close()
    }
}

class DriverBasedTest {
    companion object {
        const val POSTGRES_PASSWORD = "pass1234"
    }

    private val postgresContainer = CordaPostgresDockerContainer("localhost", "5432", "postgres", "pass1234")
    private val bankAName = CordaX500Name("BankA", "", "GB")
    private val bankBName = CordaX500Name("BankB", "", "US")
    private val bankA = TestIdentity(bankAName)
    private val bankB = TestIdentity(bankBName)

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
//                Permissions.startFlow<IssueCashFlow.Initiator>(),
//                Permissions.startFlow<ExchangeForIVUFlow.Initiator>(),
                Permissions.invokeRpc("vaultTrackBy")
        )
    }

    private fun createRpcUser(nodeName: String): User {
        return User("${nodeName}User", "testPassword1", permissions = getPermissions())
    }

    @Test
    fun `node postgres test`() {
        try {
            postgresContainer.start(listOf("notary", "banka", "bankb"))
            driver(
                    DriverParameters(
                            notaryCustomOverrides = getPostgresSettings("notary"),
                            cordappsForAllNodes = setOf(
                                    TestCordapp.findCordapp("net.corda.samples.carinsurance.flows"),
                                    TestCordapp.findCordapp("net.corda.samples.carinsurance.contracts")
                            ),
                            allowHibernateToManageAppSchema = false,
                            inMemoryDB = false // This flag is required to force schemas to be created
                    )
            ) {
                val (bankAHandle, bankBHandle) = listOf(
                        startNode(
                                providedName = bankAName,
                                rpcUsers = listOf(createRpcUser("banka")),
                                customOverrides = getPostgresSettings("banka")
                        ),
                        startNode(
                                providedName = bankBName,
                                rpcUsers = listOf(createRpcUser("bankb")),
                                customOverrides = getPostgresSettings("bankb")
                        )
                ).map { it.getOrThrow() }

                // If the SQL fails with errors, they logged in the diagnostics log file, but don't always cause the node not to start up
                listOf(bankAName, bankBName)
                        .forEach {
                            checkLogFilesForSqlErrors(baseDirectory(it).resolve("logs"))
                        }

                assertEquals(bankBName, bankAHandle.resolveName(bankAName))
                assertEquals(bankAName, bankBHandle.resolveName(bankBName))
            }

        } catch (ex: Exception) {
            Assert.fail(ex.message)
        } finally {
            postgresContainer.stop()
        }
    }

    @Test
    fun `node test`() = withDriver {
        // Start a pair of nodes and wait for them both to be ready.
        val (partyAHandle, partyBHandle) = startNodes(bankA, bankB)

        // From each node, make an RPC call to retrieve another node's name from the network map, to verify that the
        // nodes have started and can communicate.

        // This is a very basic test: in practice tests would be starting flows, and verifying the states in the vault
        // and other important metrics to ensure that your CorDapp is working as intended.
        assertEquals(bankB.name, partyAHandle.resolveName(bankB.name))
        assertEquals(bankA.name, partyBHandle.resolveName(bankA.name))
    }

    // Runs a test inside the Driver DSL, which provides useful functions for starting nodes, etc.
    private fun withDriver(test: DriverDSL.() -> Unit) = driver(
            DriverParameters(
                    isDebug = true,
                    startNodesInProcess = true
            )
    ) { test() }

    // Makes an RPC call to retrieve another node's name from the network map.
    private fun NodeHandle.resolveName(name: CordaX500Name) = rpc.wellKnownPartyFromX500Name(name)!!.name

    // Resolves a list of futures to a list of the promised values.
    private fun <T> List<Future<T>>.waitForAll(): List<T> = map { it.getOrThrow() }

    // Starts multiple nodes simultaneously, then waits for them all to be ready.
    private fun DriverDSL.startNodes(vararg identities: TestIdentity) = identities
            .map { startNode(providedName = it.name) }
            .waitForAll()
}