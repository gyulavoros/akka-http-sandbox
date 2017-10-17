package com.sandbox.gyulavoros

import akka.http.scaladsl.Http

object SandboxApp extends App {

  private val host = "localhost"
  private val port = 8080

  private val client = new TestClient(host, port)

  private val server = new TestServer() {
    override protected def postHttpBinding(binding: Http.ServerBinding): Unit = {
      super.postHttpBinding(binding)
      client.scheduleRequests()
    }
  }

  server.startServer(host, port)
}
