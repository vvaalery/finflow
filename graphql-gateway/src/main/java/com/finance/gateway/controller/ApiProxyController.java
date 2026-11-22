package com.finance.gateway.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Locale;

@RestController
public class ApiProxyController {
    private static final List<String> AUTH_PREFIXES = List.of("/api/auth/", "/api/admin/", "/api/users/");
    private static final List<String> FORWARDED_REQUEST_HEADERS = List.of("Authorization", "Content-Type", "Accept");
    private static final List<String> FORWARDED_RESPONSE_HEADERS = List.of(HttpHeaders.CONTENT_TYPE, HttpHeaders.CONTENT_DISPOSITION);

    private final RestClient http;
    private final String authUrl;
    private final String financeUrl;

    public ApiProxyController(RestClient.Builder builder,
                              @Value("${services.auth-url}") String authUrl,
                              @Value("${services.finance-url}") String financeUrl) {
        this.http = builder.build();
        this.authUrl = authUrl;
        this.financeUrl = financeUrl;
    }

    @RequestMapping("/api/**")
    public ResponseEntity<byte[]> proxy(HttpServletRequest request) throws IOException {
        String path = request.getRequestURI();
        if (path.contains("..") || path.toLowerCase(Locale.ROOT).contains("%2e")) {
            return ResponseEntity.badRequest().build();
        }
        String base = AUTH_PREFIXES.stream().anyMatch(path::startsWith) ? authUrl : financeUrl;
        String url = base + path + (request.getQueryString() == null ? "" : "?" + request.getQueryString());
        byte[] body = request.getInputStream().readAllBytes();

        RestClient.RequestBodySpec spec = http.method(HttpMethod.valueOf(request.getMethod()))
                .uri(URI.create(url))
                .headers(h -> FORWARDED_REQUEST_HEADERS.forEach(name -> {
                    String value = request.getHeader(name);
                    if (value != null) h.set(name, value);
                }));
        if (body.length > 0) spec.body(body);

        return spec.exchange((req, res) -> {
            HttpHeaders out = new HttpHeaders();
            FORWARDED_RESPONSE_HEADERS.forEach(name -> {
                String value = res.getHeaders().getFirst(name);
                if (value != null) out.set(name, value);
            });
            return ResponseEntity.status(res.getStatusCode()).headers(out).body(StreamUtils.copyToByteArray(res.getBody()));
        });
    }
}
