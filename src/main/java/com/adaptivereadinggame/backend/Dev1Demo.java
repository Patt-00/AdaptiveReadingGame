package com.adaptivereadinggame.backend;

import java.util.Arrays;
import java.util.UUID;

/** Real Dev 1 account smoke test. Never claims story or MySQL integration. */
public final class Dev1Demo {
    public static void main(String[] args) {
        var backend = BackendContext.inMemory();
        var game = backend.accountGameService();
        char[] password = "temporary demo password".toCharArray();
        try {
            var account = game.signup(" DemoReader ", "Demo Reader", password);
            System.out.println("Signup normalized username: " + account.username());
            System.out.println("Signup does not log in: " + game.currentAccount().isEmpty());
            game.login("DEMOREADER", password);
            System.out.println("Account and learner IDs match: " + account.id().equals(backend.students().findById(account.id()).orElseThrow().id()));
            try { game.startAssessment(UUID.randomUUID()); throw new AssertionError("Reading check was not blocked"); }
            catch (IllegalStateException expected) { System.out.println("Reading check without a story is blocked: true"); }
            System.out.println("Continue available without a full save: " + game.canContinue());
            game.logout(true);
            System.out.println("Logout clears current account: " + game.currentAccount().isEmpty());
            try { game.history(); throw new IllegalStateException("Access was not blocked"); }
            catch (SecurityException expected) { System.out.println("Logged-out progress access blocked: true"); }
            System.out.println("Temporary data only. Dev 2 story/resume and Dev 3 MySQL adapters are not connected.");
        } finally { Arrays.fill(password, '\0'); }
    }
}
