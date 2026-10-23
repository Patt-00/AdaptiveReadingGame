package com.adaptivereadinggame.repository;

import com.adaptivereadinggame.model.Account;
import com.adaptivereadinggame.model.Student;

/** Atomically stores a new account, its credential verifier, and its learner profile. */
public interface RegistrationRepository {
    /**
     * Store account, protected password verifier, and learner together or store none.
     * Account/student IDs must match and normalized usernames must be unique.
     * A failed write must throw; it must not return an apparent success.
     */
    Account register(Account account, String encodedVerifier, Student student);
}
