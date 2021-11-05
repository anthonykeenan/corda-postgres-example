package net.corda.samples.carinsurance

import com.spotify.docker.client.DefaultDockerClient
import com.spotify.docker.client.DockerClient
import com.spotify.docker.client.messages.ContainerConfig
import com.spotify.docker.client.messages.HostConfig
import com.spotify.docker.client.messages.PortBinding
import net.corda.core.utilities.seconds
import java.io.BufferedReader
import java.nio.file.Files

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

        val hostConfig = HostConfig.builder()
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