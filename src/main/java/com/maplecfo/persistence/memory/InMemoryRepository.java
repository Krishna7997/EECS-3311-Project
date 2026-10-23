package com.maplecfo.persistence.memory;

import com.maplecfo.persistence.Repository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/** Simple map-backed repository for tests and demo mode. Keeps insertion order. */
public class InMemoryRepository<T, ID> implements Repository<T, ID> {

    private final Map<ID, T> items = new LinkedHashMap<>();
    private final Function<T, ID> idOf;

    public InMemoryRepository(Function<T, ID> idOf) {
        this.idOf = idOf;
    }

    @Override
    public void save(T entity) {
        items.put(idOf.apply(entity), entity);
    }

    @Override
    public Optional<T> findById(ID id) {
        return Optional.ofNullable(items.get(id));
    }

    @Override
    public List<T> findAll() {
        return new ArrayList<>(items.values());
    }

    @Override
    public void delete(ID id) {
        items.remove(id);
    }
}
