package com.maplecfo.planning;

import com.maplecfo.domain.Account;
import com.maplecfo.domain.AccountGroup;
import com.maplecfo.domain.AccountType;
import com.maplecfo.domain.Money;
import com.maplecfo.events.EventBus;
import com.maplecfo.events.EventType;
import com.maplecfo.events.FinanceEvent;
import com.maplecfo.persistence.AccountRepository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;

/** F03 accounts: add, update and arrange them into the Composite tree used for net worth. */
public class AccountService {

    /** Group names in the order they are shown. */
    static final Map<String, List<AccountType>> GROUPS = new LinkedHashMap<>();

    static {
        GROUPS.put("Cash", List.of(AccountType.CHEQUING, AccountType.SAVINGS));
        GROUPS.put("Registered", List.of(AccountType.TFSA, AccountType.RRSP, AccountType.FHSA));
        GROUPS.put("Investments", List.of(AccountType.NON_REGISTERED));
        GROUPS.put("Debts", List.of(AccountType.CREDIT_CARD, AccountType.LOAN));
    }

    private final AccountRepository accountRepo;
    private final EventBus eventBus;

    public AccountService(AccountRepository accountRepo, EventBus eventBus) {
        this.accountRepo = accountRepo;
        this.eventBus = eventBus;
    }

    /**
     * Adds an account.
     *
     * @return warnings to show (for example a duplicate name); empty when all is fine
     */
    public List<String> addAccount(Account a) {
        validate(a.getType(), a.getBalance());
        if (accountRepo.findById(a.getId()).isPresent()) {
            throw new IllegalArgumentException("An account with id '" + a.getId() + "' already exists");
        }
        List<String> warnings = new ArrayList<>();
        if (accountRepo.findAll().stream().anyMatch(x -> x.getName().equalsIgnoreCase(a.getName()))) {
            warnings.add("You already have an account named '" + a.getName() + "'.");
        }
        accountRepo.save(a);
        eventBus.publish(FinanceEvent.of(EventType.ACCOUNT_BALANCE_UPDATED, a));
        return warnings;
    }

    public void updateBalance(String id, Money balance) {
        Account a = accountRepo.findById(id).orElseThrow(() -> new NoSuchElementException("No account " + id));
        validate(a.getType(), balance);
        a.updateBalance(balance);
        accountRepo.save(a);
        eventBus.publish(FinanceEvent.of(EventType.ACCOUNT_BALANCE_UPDATED, a));
    }

    public void removeAccount(String id) {
        accountRepo.delete(id);
        eventBus.publish(FinanceEvent.of(EventType.ACCOUNT_BALANCE_UPDATED, null));
    }

    public List<Account> getAccounts() {
        return accountRepo.findAll();
    }

    public Optional<Account> find(String id) {
        return accountRepo.findById(id);
    }

    /** Builds the Composite tree: Net worth -> Cash / Registered / Investments / Debts -> accounts. */
    public AccountGroup buildAccountTree() {
        AccountGroup root = new AccountGroup("Net worth");
        List<Account> all = accountRepo.findAll();
        for (Map.Entry<String, List<AccountType>> g : GROUPS.entrySet()) {
            AccountGroup group = new AccountGroup(g.getKey());
            all.stream().filter(a -> g.getValue().contains(a.getType())).forEach(group::add);
            if (!group.getChildren().isEmpty()) {
                root.add(group);
            }
        }
        return root;
    }

    private static void validate(AccountType type, Money balance) {
        if (balance.isNegative()) {
            throw new IllegalArgumentException(type.isLiability()
                    ? "Enter the amount owed as a positive number"
                    : "An asset account cannot have a negative balance");
        }
    }
}
