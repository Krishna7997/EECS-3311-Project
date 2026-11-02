package com.maplecfo.persistence.memory;

import com.maplecfo.importer.ColumnMapping;
import com.maplecfo.persistence.MappingProfileRepository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class InMemoryMappingProfileRepository implements MappingProfileRepository {

    private final Map<String, ColumnMapping> profiles = new LinkedHashMap<>();

    @Override
    public void save(ColumnMapping m) {
        profiles.put(m.profileName().toLowerCase(), m);
    }

    @Override
    public Optional<ColumnMapping> findByName(String name) {
        return Optional.ofNullable(profiles.get(name.toLowerCase()));
    }

    @Override
    public List<ColumnMapping> findAll() {
        return new ArrayList<>(profiles.values());
    }
}
