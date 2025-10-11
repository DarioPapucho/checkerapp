package com.dar.checker.network

import fi.iki.elonen.NanoHTTPD

class HealthServer(port: Int = 8080) : NanoHTTPD(port) {
    override fun serve(session: IHTTPSession): Response {
        return if (session.uri == "/health") {
            newFixedLengthResponse(Response.Status.OK, "application/json", "{\"status\":\"ok\"}")
        } else {
            newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not Found")
        }
    }
}


