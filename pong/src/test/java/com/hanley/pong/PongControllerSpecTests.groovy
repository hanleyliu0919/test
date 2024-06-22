package com.hanley.pong

import com.hanley.pong.controller.PongController
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest
import org.springframework.test.context.junit.jupiter.SpringExtension
import reactor.test.StepVerifier
import spock.lang.Specification

@WebFluxTest(SpringExtension.class)
class PongControllerSpecTests extends Specification {

    PongController pongController

    def setup() {
        pongController = new PongController()
    }

    def "should return Pong when rate limit is not exceeded"() {
        when:
        def result = pongController.hello("ping01")

        then:
        StepVerifier.create(result)
                .expectNext("World")
                .verifyComplete()
    }

    def "should throw exception when rate limit is exceeded"() {
        given:
        // Consume all available permits
        2.times { pongController.hello("ping01") }

        when:
        def result = pongController.hello("ping01")

        then:
        StepVerifier.create(result)
                .expectError(RuntimeException)
                .verify()
    }
}