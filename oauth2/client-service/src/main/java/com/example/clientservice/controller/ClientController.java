package com.example.clientservice.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

import java.util.Map;

@RestController
@RequestMapping("/client")
public class ClientController {

    private final RestClient restClient;

    public ClientController(RestClient restClient) {
        this.restClient = restClient;
    }

    @GetMapping("/call")
    public Map<?, ?> callResourceServer() {
        Map<?, ?> body = restClient.get()
                .uri("http://localhost:8081/api/message")
                .retrieve()
                .body(Map.class);
        if (body == null) {
            throw new IllegalStateException("Réponse vide du resource-server");
        }
        return body;
    }
}
