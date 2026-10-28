package com.maplecfo.persistence;

import com.maplecfo.domain.UserProfile;

/** Stores the single user profile. */
public interface ProfileRepository {

    /** The saved profile, or an empty profile when none was saved yet. */
    UserProfile load();

    void save(UserProfile profile);
}
