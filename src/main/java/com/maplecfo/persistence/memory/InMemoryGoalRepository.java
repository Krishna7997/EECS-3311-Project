package com.maplecfo.persistence.memory;

import com.maplecfo.domain.SavingsGoal;
import com.maplecfo.persistence.GoalRepository;

public class InMemoryGoalRepository extends InMemoryRepository<SavingsGoal, String> implements GoalRepository {
    public InMemoryGoalRepository() {
        super(SavingsGoal::getId);
    }
}
