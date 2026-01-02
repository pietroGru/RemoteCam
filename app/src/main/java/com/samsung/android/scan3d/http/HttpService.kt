package com.samsung.android.scan3d.http

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import kotlinx.coroutines.channels.BroadcastChannel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.runBlocking
import java.io.OutputStream

class HttpService {
    lateinit var engine: NettyApplicationEngine
    val channel = BroadcastChannel<ByteArray>(2)
    lateinit var actionChannel: Channel<Pair<String, Channel<ByteArray?>>>

    fun producer(): suspend OutputStream.() -> Unit = {
        val o = this
        val clientChannel = channel.openSubscription()
        try {
            clientChannel.consumeEach {
                o.write("--FRAME\r\nContent-Type: image/jpeg\r\n\r\n".toByteArray())
                o.write(it)
                o.flush()
            }
        } finally {
            clientChannel.cancel()
        }
    }

    public fun main() {
        actionChannel = Channel()
        engine = embeddedServer(Netty, port = 8080) {
            routing {
                get("/cam") {
                    val action = call.request.queryParameters["action"]
                    if (action != null) {
                        val responseChannel = Channel<ByteArray?>()
                        actionChannel.send(Pair(action, responseChannel))
                        val response = responseChannel.receive()
                        if (response != null) {
                            call.respondBytes(response, ContentType.Image.JPEG)
                        } else {
                            call.respond(HttpStatusCode.OK)
                        }
                    } else {
                        call.respondText("Ok")
                    }
                }
                get("/cam.mjpeg") {
                    call.respondOutputStream(
                        ContentType.parse("multipart/x-mixed-replace;boundary=FRAME"),
                        HttpStatusCode.OK, producer()
                    )
                }
            }
        }
        engine.start(wait = false)
    }

}