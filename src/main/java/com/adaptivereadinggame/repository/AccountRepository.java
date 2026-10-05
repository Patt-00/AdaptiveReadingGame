package com.adaptivereadinggame.repository;

import com.adaptivereadinggame.model.Account;
import java.util.Optional;
import java.util.UUID;

/** Dev 3 adapter: enforce unique normalized usernames and never return password hashes here. */
public interface AccountRepository {
    Optional<Account> findAccount(UUID id);
    Optional<Account> findByUsername(String normalizedUsername);
    Account create(Account account);
}
