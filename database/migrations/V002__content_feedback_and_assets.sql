ALTER TABLE questions
    ADD COLUMN explanation TEXT NULL;

ALTER TABLE story_nodes
    ADD COLUMN speaker_asset_key VARCHAR(80) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT '';
