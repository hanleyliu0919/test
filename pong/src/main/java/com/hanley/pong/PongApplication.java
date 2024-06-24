package com.hanley.pong;

import com.google.common.util.concurrent.RateLimiter;
import io.netty.util.internal.StringUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.reactive.function.server.*;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

@Slf4j
@SpringBootApplication
public class PongApplication {

    // 每秒只创建一个元素
    private final RateLimiter rateLimiter = RateLimiter.create(1.0);

    public static void main(String[] args) {
        SpringApplication.run(PongApplication.class, args);
    }

    @Bean
    public RouterFunction<ServerResponse> helloFlux(){
        return RouterFunctions.route().GET("/flux/hello", request -> {
            String appName = request.queryParam("appName").get();
            if(StringUtil.isNullOrEmpty(appName)){
                appName = "unknown server";
            }
            log.info("appName:{}", appName);
            if (rateLimiter.tryAcquire()) {
                log.info("request successful, current appName:{} , time:{}", appName, LocalDateTime.now());
                return ServerResponse.ok().body(Mono.just("World“"), String.class);
            }else {
                log.info("request failed, current appName:{} , time:{}", appName, LocalDateTime.now());
                return ServerResponse.status(429).bodyValue("Too Many Requests");
            }
        }).build();
    }
}
