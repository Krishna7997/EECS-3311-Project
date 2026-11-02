package com.maplecfo.persistence;

import com.maplecfo.importer.ColumnMapping;

import java.util.List;
import java.util.Optional;

/** Saved CSV column-mapping profiles, e.g. "TD chequing" (F01). */
public interface MappingProfileRepository {

    void save(ColumnMapping m);

    Optional<ColumnMapping> findByName(String name);

    List<ColumnMapping> findAll();
}
