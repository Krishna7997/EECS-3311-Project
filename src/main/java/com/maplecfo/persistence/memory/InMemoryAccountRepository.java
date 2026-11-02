package com.maplecfo.persistence.memory;

import com.maplecfo.domain.Account;
import com.maplecfo.persistence.AccountRepository;

public class InMemoryAccountRepository extends InMemoryRepository<Account, String> implements AccountRepository {
    public InMemoryAccountRepository() {
        super(Account::getId);
    }
}
