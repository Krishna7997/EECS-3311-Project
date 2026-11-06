package com.maplecfo.persistence.memory;

import com.maplecfo.persistence.SettingsRepository;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class InMemorySettingsRepository implements SettingsRepository {

    private final Map<String, String> values = new HashMap<>();

    @Override
    public Optional<String> get(String key) {
        return Optional.ofNullable(values.get(key));
    }

    @Override
    public void put(String key, String value) {
        values.put(key, value);
    }
}
