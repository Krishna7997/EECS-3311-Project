package com.maplecfo.persistence;

import java.util.Optional;

/** Small key/value settings, e.g. the auto-import folder and the last finished monthly check-in. */
public interface SettingsRepository {

    Optional<String> get(String key);

    void put(String key, String value);
}
