package com.adaptivereadinggame.service;

import com.adaptivereadinggame.backend.BackendContext;
import com.adaptivereadinggame.model.PlayerAppearance;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PlayerAppearanceServiceTest {
    @Test void selectionIsAccountScopedAndCanBeChanged() {
        var backend = BackendContext.inMemory();
        var accounts = backend.accountGameService();
        var appearances = backend.playerAppearanceService();
        assertThrows(IllegalStateException.class, appearances::current);
        accounts.signup("appearance-one", "First", "StrongPass1!".toCharArray());
        accounts.signup("appearance-two", "Second", "StrongPass1!".toCharArray());
        accounts.login("appearance-one", "StrongPass1!".toCharArray());
        assertTrue(appearances.current().isEmpty());
        assertEquals(PlayerAppearance.B, appearances.save(PlayerAppearance.B));
        accounts.logout(true);
        accounts.login("appearance-two", "StrongPass1!".toCharArray());
        assertTrue(appearances.current().isEmpty());
        appearances.save(PlayerAppearance.A);
        accounts.logout(true);
        accounts.login("appearance-one", "StrongPass1!".toCharArray());
        assertEquals(PlayerAppearance.B, appearances.current().orElseThrow());
        appearances.save(PlayerAppearance.A);
        assertEquals(PlayerAppearance.A, appearances.current().orElseThrow());
    }
}
