package com.hanley.ping02.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Duration;

@Slf4j
@Service
public class PingService {

    private final WebClient webClient;
    private static final Path LOCK_FILE_PATH = Paths.get("D:/demo/ping-lock.txt");
    private static final long RATE_LIMIT_PERIOD_MS = 1000; // 1 second
    private static final int MAX_REQUESTS_PER_SECOND = 2;
    private static final int BUFFER_SIZE = Long.BYTES + Integer.BYTES;
    private static final ByteBuffer buffer = ByteBuffer.allocateDirect(BUFFER_SIZE);

    @Value("${app.name}")
    private String appName;

    public PingService(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.baseUrl("http://localhost:9090").build();
    }

    /**
     * 服务启动时初始化调用一次该方法
     */
    @PostConstruct
    public void init() {
        log.info("PingService {} started", appName);
        Flux.interval(Duration.ofSeconds(1))// 每秒产出一个元素
                .flatMap(i -> pingPong())
                .subscribe();
    }

    /**
     * 多个jvm进程同时发送请求将通过File Lock控制只允许最多两个Ping服务发出请求
     * @return Mono
     */
    public Mono<String> pingPong() {
        return Mono.defer(() -> {
            if (acquireLock()) {
                return sayHello()
                        .doOnNext(response -> log.info("Request sent & Pong Respond: {}", response))
                        // 使用 onErrorResume 来在错误时返回一个空的Mono
                        .onErrorResume(error -> {
                            if (error.getMessage().contains("429")) {
                                log.info("Request send & Pong throttled it.");
                                // 返回一个默认值或空的 Mono
                                return Mono.empty();
                            } else {
                                log.error("Request failed: {}", error.getMessage(), error);
                                // 或者抛出异常，让外部处理
                                return Mono.error(error);
                            }
                        });
            } else {
                log.info("Request not sent as being rate limited");
                return Mono.empty(); // 返回一个空的 Mono
            }
        });
    }


    /**
     * 通过webClient发送异步请求
     * @return Mono
     */
    public Mono<String> sayHello() {

        return webClient.get()
                .uri("/hello?appName=" + appName)
                .retrieve()
                .bodyToMono(String.class);
    }

    /**
     * Multiple Ping Services should be running as separate JVM Process with capability of Rate Limit Control across all processes with only 2 RPS
     * @return if you get lock
     */
    public boolean acquireLock() {
        FileChannel channel = null;
        FileLock lock = null;
        try {
            channel = FileChannel.open(LOCK_FILE_PATH, StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE);
            lock = channel.tryLock();
            if (lock == null) {
                return false;
            }
            long currentTime = System.currentTimeMillis();
            long startOfSecond = currentTime - (currentTime % RATE_LIMIT_PERIOD_MS);

            buffer.clear();
            channel.read(buffer, 0);
            buffer.flip();

            long lastRequestTime = buffer.getLong();
            int requestCount = buffer.getInt();

            if (lastRequestTime < startOfSecond) {
                lastRequestTime = startOfSecond;
                requestCount = 0;
            }

            if (requestCount < MAX_REQUESTS_PER_SECOND) {
                requestCount++;
                buffer.clear();
                buffer.putLong(lastRequestTime);
                buffer.putInt(requestCount);
                buffer.flip();
                channel.write(buffer, 0);
                return true;
            }
        } catch (IOException e) {
            log.error("Error acquiring file lock", e);
        } finally {
            if (lock != null) {
                try {
                    lock.release();
                } catch (IOException e) {
                    log.error("Error releasing file lock", e);
                }
            }
            if (channel != null) {
                try {
                    channel.close();
                } catch (IOException e) {
                    log.error("Error closing file channel", e);
                }
            }
        }
        return false;
    }


}
