package com.example.inventoryservice;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RefreshScope
public class ConfigController {

    @Autowired
    private InventoryConfig inventoryConfig;

    @Value("${spring.profiles.active:default}")
    private String activeProfile;

    @GetMapping("/api/inventory/config")
    public Map<String, Object> getConfig() {
        return Map.of(
            "profile", activeProfile,
            "maxStock", inventoryConfig.getMaxStock(),
            "replenishThreshold", inventoryConfig.getReplenishThreshold()
        );
    }
}