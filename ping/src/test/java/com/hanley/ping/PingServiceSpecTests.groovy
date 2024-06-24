package com.hanley.ping

import com.hanley.ping.service.PingService
import org.spockframework.spring.SpringBean
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import spock.lang.Specification
import spock.lang.Subject

import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.file.OpenOption
import java.nio.file.Path

@WebFluxTest(SpringExtension.class)
class PingServiceSpecTests extends Specification {

    @SpringBean
    WebClient.Builder webClientBuilder = Mock()

    @SpringBean
    WebClient webClient = Mock()

    @Subject
    PingService pingService

    String appName = "ping01"

    def setup() {
        webClientBuilder.baseUrl(_ as String) >> webClientBuilder
        webClientBuilder.build() >> webClient
        pingService = new PingService(webClientBuilder)

        pingService.metaClass.setAttribute(pingService,"appName","ping01")
    }

    def "should send hello and receive pong response"() {
        given:
        def responseSpec = Mock(WebClient.ResponseSpec)
        def uriSpec = Mock(WebClient.RequestHeadersUriSpec)
        pingService.metaClass.acquireLock = { -> true }

        when:
        def result = pingService.pingPong().block()

        then:
        1 * webClient.get() >> uriSpec
        1 * uriSpec.uri("/hello?appName=" + appName) >> uriSpec
        1 * uriSpec.retrieve() >> responseSpec
        1 * responseSpec.bodyToMono(String) >> Mono.just("World")
        result == "World"
    }

    def "should not send hello when rate limited"() {
        given:
        pingService.metaClass.acquireLock = { -> false }

        when:
        pingService.pingPong()

        then:
        0 * webClient.get()
    }

    def "acquireLock should return false when requests exceed limit"() {
        given:
        def channel = Mock(FileChannel)
        def lock = Mock(FileLock)
        Path.metaClass.static.get = { String first, String... more ->
            Mock(Path)
        }
        FileChannel.metaClass.static.open = { Path path, OpenOption... options ->
            channel
        }

        when:
        def result = pingService.acquireLock()

        then:
        0 * channel.tryLock() >> lock
        0 * channel.write(_ as byte[])
    }


}