-- One stable, cosmetic player appearance per account.
CREATE TABLE IF NOT EXISTS player_appearances (
    account_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
    appearance ENUM('A','B') NOT NULL,
    CONSTRAINT fk_player_appearance_account FOREIGN KEY (account_id)
        REFERENCES accounts(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4;
