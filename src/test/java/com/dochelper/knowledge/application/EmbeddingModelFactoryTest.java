package com.dochelper.knowledge.application;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import com.sun.net.httpserver.HttpServer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

/** 通过本地合成供应商检查真实兼容协议，不发送业务资料或真实密钥。 */
class EmbeddingModelFactoryTest {
    private HttpServer server;
    private String base;
    private final AtomicReference<String> path = new AtomicReference<>(), auth = new AtomicReference<>();
    private final AtomicReference<JsonNode> body = new AtomicReference<>();
    private volatile int status = 200;
    @BeforeEach void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/", exchange -> {
            path.set(exchange.getRequestURI().getPath()); auth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            body.set(new ObjectMapper().readTree(exchange.getRequestBody()));
            byte[] response = (status == 200 ? "{\"object\":\"list\",\"data\":[{\"object\":\"embedding\",\"index\":0,\"embedding\":[1,0,0]}],\"model\":\"fixture\",\"usage\":{\"prompt_tokens\":1,\"total_tokens\":1}}"
                    : "{\"error\":\"SYNTHETIC_KEY\"}").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type","application/json");
            exchange.sendResponseHeaders(status,response.length); exchange.getResponseBody().write(response); exchange.close();
        });
        server.start(); base = "http://127.0.0.1:" + server.getAddress().getPort();
    }
    @AfterEach void stop() { if (server != null) server.stop(0); }
    @Test void shouldUseVersionedPathAndOmitUnspecifiedDimensions() {
        var model = new EmbeddingModelFactory().create("API",base + "/v1","fixture",null,"SYNTHETIC_KEY");
        assertThat(model.embed("固定测试文本")).hasSize(3);
        assertThat(path.get()).isEqualTo("/v1/embeddings"); assertThat(body.get().has("dimensions")).isFalse();
        assertThat(auth.get()).isEqualTo("Bearer SYNTHETIC_KEY");
    }
    @Test void shouldAppendVersionWhenAbsentAndSendExplicitDimensions() {
        var model = new EmbeddingModelFactory().create("API",base,"fixture",3,"SYNTHETIC_KEY");
        assertThat(model.embed("固定测试文本")).hasSize(3);
        assertThat(path.get()).isEqualTo("/v1/embeddings"); assertThat(body.get().path("dimensions").asInt()).isEqualTo(3);
    }
    @Test void shouldNotLeakSupplierErrorBody() {
        status = 401;
        var model = new EmbeddingModelFactory().create("API",base,"fixture",null,"SYNTHETIC_KEY");
        assertThatThrownBy(() -> model.embed("固定测试文本")).hasMessageContaining("拒绝请求").hasMessageNotContaining("SYNTHETIC_KEY");
    }
}
