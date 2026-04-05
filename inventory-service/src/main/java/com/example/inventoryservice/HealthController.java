package com.example.inventoryservice;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@RestController
public class HealthController {

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${spring.cloud.config.uri:http://localhost:8888}")
    private String configServerUri;

    @Value("${spring.profiles.active:default}")
    private String activeProfile;

    @GetMapping("/api/inventory/health")
    public ResponseEntity<Map<String, String>> inventoryHealth() {
        String probeUrl = String.format("%s/inventory-service/%s", configServerUri, activeProfile);
        try {
            ResponseEntity<String> response = restTemplate.getForEntity(probeUrl, String.class);
            if (response.getStatusCode().is2xxSuccessful()) {
                return ResponseEntity.ok(Map.of(
                    "status", "UP",
                    "configServer", "connected"
                ));
            }
        } catch (RestClientException ignored) {
            // Return a disconnected state when config server probe fails.
        }

        return ResponseEntity.ok(Map.of(
            "status", "DOWN",
            "configServer", "disconnected"
        ));
    }
}
