package com.example.routes

import com.example.ollama.OllamaClient
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

@Serializable
data class GenerateRequest(
    val model: String,
    val prompt: String
)

fun Route.ollamaRoutes() {
    val ollamaClient = OllamaClient()

    post("/generate") {
        try {
            val request = call.receive<GenerateRequest>()
            val response = ollamaClient.generateResponse(request.model, request.prompt)
            call.respond(response)
        } catch (e: Exception) {
            call.respond(HttpStatusCode.InternalServerError, mapOf("error" to e.message))
        }
    }
}