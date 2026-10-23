package com.maplecfo.persistence;

import java.util.List;
import java.util.Optional;

/**
 * Repository/DAO pattern: services depend on this interface, never on SQL.
 * SQLite implementations are used by the app; in-memory ones by tests.
 */
public interface Repository<T, ID> {

    void save(T entity);

    Optional<T> findById(ID id);

    List<T> findAll();

    void delete(ID id);
}
