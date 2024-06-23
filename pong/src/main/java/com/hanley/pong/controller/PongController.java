package com.hanley.pong.controller;

import com.google.common.util.concurrent.RateLimiter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@Slf4j
@RestController
public class PongController {

    // 每秒只创建一个元素
    private final RateLimiter rateLimiter = RateLimiter.create(1.0);

    /**
     * pong 服务控制每秒只允许一个ping服务请求该接口，多余请求响应429
     * @param appName 服务名
     * @return
     */
    @GetMapping("/hello")
    public Mono<ResponseEntity<String>> hello(@RequestParam String appName) {
        log.info("has new request appName:{}", appName);

        //尝试获取rateLimiter元素
        if (rateLimiter.tryAcquire()) {
            log.info("request successful, current appName:{} , time:{}", appName, System.currentTimeMillis());
            return Mono.just(ResponseEntity.ok().body("world"));
        } else {
            log.info("request failed, current appName:{} , time:{}", appName, System.currentTimeMillis());
            return Mono.just(ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body("Too many requests."));
        }
    }
}
