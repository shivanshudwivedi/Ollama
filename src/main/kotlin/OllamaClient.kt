package com.example.ollama

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.time.Duration.Companion.seconds

@Serializable
data class OllamaRequest(
    val model: String,
    val prompt: String,
    val stream: Boolean = false
)

sealed class OllamaResult {
    data class Success(val completeResponse: String, val filePath: String) : OllamaResult()
    data class Error(val message: String) : OllamaResult()
}

class OllamaClient {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        prettyPrint = true
    }

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(json)
        }

        // Add timeout configurations
        install(HttpTimeout) {
            requestTimeoutMillis = 60.seconds.inWholeMilliseconds  // 60 seconds timeout
            connectTimeoutMillis = 10.seconds.inWholeMilliseconds // 10 seconds connection timeout
            socketTimeoutMillis = 60.seconds.inWholeMilliseconds  // 60 seconds socket timeout
        }

        // Add engine specific configurations
        engine {
            requestTimeout = 60.seconds.inWholeMilliseconds // Engine specific timeout
            endpoint {
                connectTimeout = 10.seconds.inWholeMilliseconds
                keepAliveTime = 30.seconds.inWholeMilliseconds
            }
        }
    }

    private fun createOutputFile(prompt: String): File {
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
        val fileName = "ollama_response_${timestamp}.txt"
        return File(fileName).also { file ->
            file.writeText("Prompt: $prompt\n\nResponse:\n")
        }
    }

    suspend fun generateResponse(model: String, prompt: String): OllamaResult {
        val outputFile = createOutputFile(prompt)

        return try {
            println("Connecting to Ollama server...")
            val response: HttpResponse = client.post("http://localhost:11434/api/generate") {
                contentType(ContentType.Application.Json)
                setBody(OllamaRequest(model, prompt))
            }

            println("Receiving response...")
            val responseText = response.bodyAsText()
            val stringBuilder = StringBuilder()

            println("Processing response...")
            responseText.split("\n").forEach { line ->
                if (line.isNotBlank()) {
                    try {
                        val jsonObject = json.parseToJsonElement(line) as? JsonObject

                        // Check for error
                        if (jsonObject?.containsKey("error") == true) {
                            val errorMsg = jsonObject["error"]?.jsonPrimitive?.content ?: "Unknown error"
                            return OllamaResult.Error(errorMsg)
                        }

                        // Extract response text
                        jsonObject?.get("response")?.jsonPrimitive?.content?.let { responseContent ->
                            stringBuilder.append(responseContent)
                            outputFile.appendText(responseContent)
                            // Print each chunk as it arrives
                            print(responseContent)
                        }
                    } catch (e: Exception) {
                        println("\nWarning: Skipping malformed JSON line: $line")
                    }
                }
            }

            val completeResponse = stringBuilder.toString()
            if (completeResponse.isBlank()) {
                OllamaResult.Error("No valid response content received")
            } else {
                // Add a final newline to the file
                outputFile.appendText("\n\nGenerated at: ${LocalDateTime.now()}\n")
                OllamaResult.Success(completeResponse, outputFile.absolutePath)
            }

        } catch (e: Exception) {
            outputFile.appendText("\nError occurred: ${e.message}\n")
            OllamaResult.Error("Request failed: ${e.message}")
        }
    }

    fun close() {
        client.close()
    }
}