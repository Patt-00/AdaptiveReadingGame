package com.adaptivereadinggame.service;

import com.adaptivereadinggame.model.Account;
import java.util.Optional;

/** Contract only: Dev 1 implements credential checks and the current-account lifecycle. */
public interface AuthService {
    Account signup(String username, String displayName, char[] password);
    Account login(String username, char[] password);
    Optional<Account> currentAccount();
    void logout();
}
