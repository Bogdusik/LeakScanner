package com.leakscanner.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leakscanner.model.OutdatedDependency;
import com.leakscanner.model.Vulnerability;
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
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class NpmRegistryClientsTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private HttpServer server;
    private String baseUrl;
    private final AtomicReference<String> lastPath = new AtomicReference<>();
    private final AtomicReference<String> lastBody = new AtomicReference<>();

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private void respond(String path, String body) {
        respond(path, body, "application/octet-stream");
    }

    private void respond(String path, String body, String contentType) {
        server.createContext(path, exchange -> {
            lastPath.set(exchange.getRequestURI().getPath());
            lastBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", contentType);
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
    }

    @Test
    void latestVersionComesFromTheSmallLatestDocument() {
        List<String> versions = new ArrayList<>();
        for (int i = 0; i < 20000; i++) {
            versions.add("\"0.0." + i + "\":{\"name\":\"express\"}");
        }
        String hugePackument = "{\"dist-tags\":{\"latest\":\"9.9.9\"},\"versions\":{" + String.join(",", versions) + "}}";
        assertTrue(hugePackument.length() > 512 * 1024);

        server.createContext("/express", exchange -> {
            boolean latestDocument = exchange.getRequestURI().getPath().endsWith("/latest");
            byte[] bytes = (latestDocument ? "{\"name\":\"express\",\"version\":\"5.1.0\"}" : hugePackument)
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();

        NpmRegistryService service = new NpmRegistryService(WebClient.builder(), objectMapper);
        ReflectionTestUtils.setField(service, "npmRegistryUrl", baseUrl);

        List<OutdatedDependency> outdated = service.checkOutdatedDependencies(
                "{\"dependencies\":{\"express\":\"^4.16.0\"}}");

        assertEquals(1, outdated.size());
        assertEquals("express", outdated.get(0).getName());
        assertEquals("4.16.0", outdated.get(0).getCurrentVersion());
        assertEquals("5.1.0", outdated.get(0).getLatestVersion());
    }

    @Test
    void auditSendsExactVersionsToTheBulkEndpointAndMapsAdvisories() {
        respond("/", """
                {"lodash":[{"id":1,"url":"https://github.com/advisories/GHSA-35jh-r3h4-6jhm",
                "title":"Command Injection in lodash","severity":"high","vulnerable_versions":"<4.17.21"}],
                "minimist":[{"id":2,"url":"https://github.com/advisories/GHSA-xvch-5gv4-984h",
                "title":"Prototype Pollution in minimist","severity":"critical","vulnerable_versions":"<1.2.6"}]}
                """);

        NpmAuditService service = new NpmAuditService(objectMapper, WebClient.builder());
        ReflectionTestUtils.setField(service, "npmAuditUrl", baseUrl);

        List<Vulnerability> found = service.scanPackageJson("""
                {"name":"demo","version":"1.0.0",
                 "dependencies":{"lodash":"^4.17.15","minimist":"1.2.0","left-pad":"latest","internal":"git+https://example.com/x.git"},
                 "devDependencies":{"mocha":"~10.0.0"}}
                """);

        assertEquals(2, found.size());
        Vulnerability lodash = found.get(0);
        assertEquals("lodash", lodash.getPackageName());
        assertEquals("Command Injection in lodash", lodash.getTitle());
        assertEquals(Vulnerability.Severity.HIGH, lodash.getSeverity());
        assertEquals("https://github.com/advisories/GHSA-35jh-r3h4-6jhm", lodash.getUrl());
        assertEquals("Affects lodash <4.17.21", lodash.getDescription());
        assertEquals(Vulnerability.Severity.CRITICAL, found.get(1).getSeverity());

        String body = lastBody.get();
        assertTrue(body.contains("\"lodash\":[\"4.17.15\"]"), body);
        assertTrue(body.contains("\"mocha\":[\"10.0.0\"]"), body);
        assertFalse(body.contains("left-pad"), body);
        assertFalse(body.contains("internal"), body);
    }

    @Test
    void auditReturnsNothingWhenThereAreNoExactVersions() {
        NpmAuditService service = new NpmAuditService(objectMapper, WebClient.builder());
        ReflectionTestUtils.setField(service, "npmAuditUrl", "http://127.0.0.1:1");

        assertTrue(service.scanPackageJson("{\"name\":\"demo\",\"dependencies\":{\"left-pad\":\"latest\"}}").isEmpty());
    }

    @Test
    void auditSurvivesAnUnreachableRegistry() {
        NpmAuditService service = new NpmAuditService(objectMapper, WebClient.builder());
        ReflectionTestUtils.setField(service, "npmAuditUrl", "http://127.0.0.1:1");

        assertTrue(service.scanPackageJson("{\"dependencies\":{\"lodash\":\"4.17.15\"}}").isEmpty());
    }
}
