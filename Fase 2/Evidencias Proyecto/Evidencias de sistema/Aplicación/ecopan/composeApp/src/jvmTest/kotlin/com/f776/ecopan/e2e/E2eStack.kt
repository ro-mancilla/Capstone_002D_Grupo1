package com.f776.ecopan.e2e

import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

/** Fault injection on the throwaway stack: stop and start its containers, or run SQL in its database. Only its own project. */
internal object E2eStack {
    private const val PROJECT = "ecopan-acc"

    private val root: File = generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
        .first { File(it, "docker-compose.yml").exists() }

    private fun compose(vararg args: String, stdin: String? = null): String {
        val command = listOf(
            "docker", "compose", "-p", PROJECT, "-f", "docker-compose.yml",
            "-f", "scripts/acceptance/compose.yml", "-f", "scripts/e2e/compose.yml", *args,
        )
        val process = ProcessBuilder(command).directory(root).redirectErrorStream(true).start()
        if (stdin != null) process.outputStream.use { it.write(stdin.toByteArray()) } else process.outputStream.close()
        val output = process.inputStream.bufferedReader().readText()
        check(process.waitFor() == 0) { "docker compose ${args.joinToString(" ")} failed: $output" }
        return output
    }

    fun stop(service: String) { compose("stop", service) }

    fun start(service: String) {
        compose("start", service)
        if (service == "web") awaitBackend()
    }

    fun awaitBackend(timeoutMs: Long = 120_000) {
        val http = HttpClient.newHttpClient()
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val up = runCatching {
                http.send(HttpRequest.newBuilder(URI.create("${E2eBackend.api}/health")).build(), HttpResponse.BodyHandlers.discarding()).statusCode() == 200
            }.getOrDefault(false)
            if (up) return
            Thread.sleep(1_000)
        }
        error("the backend did not come back")
    }

    /** Runs [sql] in the throwaway database. */
    fun sql(sql: String): String = compose("exec", "-T", "db", "psql", "-U", "postgres", "-d", "ecopan", "-v", "ON_ERROR_STOP=1", "-X", "-q", "-t", "-A", "-c", sql)
}
