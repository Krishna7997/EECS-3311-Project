package com.maplecfo.persistence.memory;

import com.maplecfo.domain.UserProfile;
import com.maplecfo.persistence.ProfileRepository;

public class InMemoryProfileRepository implements ProfileRepository {

    private UserProfile profile = new UserProfile();

    @Override
    public UserProfile load() {
        return profile;
    }

    @Override
    public void save(UserProfile profile) {
        this.profile = profile;
    }
}
