package com.adaptivereadinggame.repository;

import com.adaptivereadinggame.model.Account;
import java.util.Optional;
import java.util.UUID;

/** Stores public account records with unique normalized usernames and no credential data. */
public interface AccountRepository {
    Optional<Account> findAccount(UUID id);
    Optional<Account> findByUsername(String normalizedUsername);
    Account create(Account account);
}
