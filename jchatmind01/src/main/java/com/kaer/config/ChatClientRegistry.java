package com.kaer.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class ChatClientRegistry {
    private final Map<String, ChatClient> registry;

    public ChatClientRegistry(Map<String, ChatClient> clients) {
        this.registry = clients;
    }

    public ChatClient get(String name) {
        return registry.get(name);
    }
}
