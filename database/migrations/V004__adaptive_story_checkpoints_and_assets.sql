-- Adaptive story episodes can pause at several comprehension checkpoints.
-- Existing version-1 questions and saves keep their legacy empty checkpoint ID.

ALTER TABLE questions
    ADD COLUMN checkpoint_id VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT '',
    ADD COLUMN question_difficulty ENUM('EASY','MEDIUM','HARD') NULL,
    ADD KEY ix_question_checkpoint_level (chapter_id, checkpoint_id, question_difficulty, question_order);

ALTER TABLE story_nodes
    ADD COLUMN checkpoint_id VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT '',
    ADD COLUMN scene_asset_key VARCHAR(80) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT '',
    ADD COLUMN cg_asset_key VARCHAR(80) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT '',
    ADD COLUMN voice_asset_key VARCHAR(80) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT '',
    ADD COLUMN story_complete BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE story_choices
    ADD COLUMN route_id VARCHAR(80) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT '';

CREATE TABLE story_checkpoints (
    chapter_id           CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    checkpoint_id        VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    node_id              VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    passage              MEDIUMTEXT NOT NULL,
    correct_next_node_id VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    incorrect_next_node_id VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    PRIMARY KEY (chapter_id, checkpoint_id),
    UNIQUE KEY uq_story_checkpoint_node (chapter_id, node_id),
    CONSTRAINT fk_checkpoint_chapter FOREIGN KEY (chapter_id)
        REFERENCES chapters(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4;

ALTER TABLE assessment_drafts
    ADD COLUMN checkpoint_id VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT '',
    ADD COLUMN route_id VARCHAR(80) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT '',
    ADD COLUMN last_completed_checkpoint_id VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT '',
    ADD COLUMN active_difficulty ENUM('EASY','MEDIUM','HARD') NULL DEFAULT NULL;

ALTER TABLE assessment_drafts
    DROP INDEX uq_draft_student_chapter,
    ADD UNIQUE KEY uq_draft_student_checkpoint (student_id, chapter_id, checkpoint_id);

ALTER TABLE assessments
    ADD COLUMN checkpoint_id VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT '';

ALTER TABLE assessments
    DROP INDEX uq_assessment_student_chapter,
    ADD UNIQUE KEY uq_assessment_student_checkpoint (student_id, chapter_id, checkpoint_id);

ALTER TABLE save_slots
    ADD COLUMN route_id VARCHAR(80) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT '',
    ADD COLUMN checkpoint_id VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT '';
