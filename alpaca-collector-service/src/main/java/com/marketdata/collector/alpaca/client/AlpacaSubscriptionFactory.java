package com.marketdata.collector.alpaca.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.marketdata.collector.alpaca.config.AlpacaWebSocketProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Component
@RequiredArgsConstructor
public class AlpacaSubscriptionFactory {

    private final AlpacaWebSocketProperties properties;
    private final ObjectMapper objectMapper;

    public String authMessage() {
        return objectMapper.createObjectNode()
                .put("action", "auth")
                .put("key", properties.key())
                .put("secret", properties.secret())
                .toString();
    }

    public String subscribeMessage() {
        List<String> symbols = symbolList();
        ObjectNode request = objectMapper.createObjectNode();
        request.put("action", "subscribe");
        request.set("trades", symbolArray(symbols));
        request.set("quotes", symbolArray(symbols));
        request.set("bars", symbolArray(symbols));
        request.set("updatedBars", symbolArray(symbols));
        return request.toString();
    }

    public List<String> symbolList() {
        if (properties.symbols() == null) {
            throw new IllegalStateException("alpaca.websocket.symbols must not be empty");
        }
        List<String> symbols = Arrays.stream(properties.symbols().split(","))
                .map(String::trim)
                .filter(symbol -> !symbol.isEmpty())
                .toList();
        if (symbols.isEmpty()) {
            throw new IllegalStateException("alpaca.websocket.symbols must not be empty");
        }
        return symbols;
    }

    private ArrayNode symbolArray(List<String> symbols) {
        ArrayNode array = objectMapper.createArrayNode();
        symbols.forEach(array::add);
        return array;
    }
}
