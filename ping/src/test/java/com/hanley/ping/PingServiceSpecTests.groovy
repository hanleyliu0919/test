package com.hanley.ping

import com.hanley.ping.service.PingService
import org.spockframework.spring.SpringBean
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import spock.lang.Specification
import spock.lang.Subject

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

        when:
        def result = pingService.pingPong().block()

        then:
        1 * webClient.get() >> uriSpec
        1 * uriSpec.uri("/hello?appName=" + appName) >> uriSpec
        1 * uriSpec.retrieve() >> responseSpec
        1 * responseSpec.bodyToMono(String) >> Mono.just("World")
        result == "World"
    }

    def "should send hello and receive pong response status code with 429"() {
        given:
        def responseSpec = Mock(WebClient.ResponseSpec)
        def uriSpec = Mock(WebClient.RequestHeadersUriSpec)

        when:
        def result = pingService.pingPong().block()

        then:
        1 * webClient.get() >> uriSpec
        1 * uriSpec.uri("/hello?appName=" + appName) >> uriSpec
        1 * uriSpec.retrieve() >> responseSpec
        1 * responseSpec.bodyToMono(String) >> Mono.error(new RuntimeException("Too Many Requests (429)"))
        result == "Pong throttled it."
    }

    def "should send hello and receive pong response error"() {
        given:
        def responseSpec = Mock(WebClient.ResponseSpec)
        def uriSpec = Mock(WebClient.RequestHeadersUriSpec)
        // pingService.metaClass.acquireLock = { -> true }

        when:
        def result = pingService.pingPong().block()

        then:
        1 * webClient.get() >> uriSpec
        1 * uriSpec.uri("/hello?appName=" + appName) >> uriSpec
        1 * uriSpec.retrieve() >> responseSpec
        1 * responseSpec.bodyToMono(String) >> Mono.error(new RuntimeException("server response 500"))
        result == "Request failed : server response 500"
    }

}