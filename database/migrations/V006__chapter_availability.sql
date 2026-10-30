-- Preserve older chapter content for existing saves while excluding it from new-game selection.
ALTER TABLE chapters
    ADD COLUMN available_for_new_game BOOLEAN NOT NULL DEFAULT TRUE;
