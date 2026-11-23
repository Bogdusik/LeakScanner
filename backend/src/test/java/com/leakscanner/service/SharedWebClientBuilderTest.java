package com.leakscanner.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leakscanner.dto.RepositoryDTO;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;

class SharedWebClientBuilderTest {

    private HttpServer server;
    private String baseUrl;
    private final List<String> authorizationHeaders = new CopyOnWriteArrayList<>();
    private final List<String> requestPaths = new CopyOnWriteArrayList<>();

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        server.createContext("/", exchange -> {
            requestPaths.add(exchange.getRequestURI().getPath());
            authorizationHeaders.add(String.valueOf(exchange.getRequestHeaders().get("Authorization")));
            byte[] bytes = "{\"content\":\"aGVsbG8=\",\"version\":\"1.0.0\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void aTokenFromOneRequestIsNotSentOnTheNext() {
        WebClient.Builder shared = WebClient.builder();
        GitHubService github = new GitHubService(shared);
        ReflectionTestUtils.setField(github, "githubBaseUrl", baseUrl);
        RepositoryDTO repo = new RepositoryDTO("octocat", "hello", "github");

        github.getFileContent(repo, "a.txt", "ghp_first_users_token", "main");
        github.getFileContent(repo, "b.txt", null, "main");

        assertEquals(2, authorizationHeaders.size());
        assertEquals("[token ghp_first_users_token]", authorizationHeaders.get(0));
        assertEquals("null", authorizationHeaders.get(1));
    }

    @Test
    void servicesSharingOneBuilderDoNotLeakHeadersOrBaseUrlsIntoEachOther() {
        WebClient.Builder shared = WebClient.builder();
        GitHubService github = new GitHubService(shared);
        ReflectionTestUtils.setField(github, "githubBaseUrl", baseUrl);
        NpmRegistryService registry = new NpmRegistryService(shared, new ObjectMapper());
        ReflectionTestUtils.setField(registry, "npmRegistryUrl", baseUrl);

        github.getFileContent(new RepositoryDTO("octocat", "hello", "github"), "a.txt", "ghp_secret_token", "main");
        List<String> paths = new ArrayList<>(requestPaths);
        registry.checkOutdatedDependencies("{\"dependencies\":{\"left-pad\":\"1.0.0\"}}");

        assertEquals("null", authorizationHeaders.get(authorizationHeaders.size() - 1),
                "a GitHub token must never reach the npm registry; requests: " + requestPaths + " first: " + paths);
    }
}
