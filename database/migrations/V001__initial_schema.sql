-- Initial MySQL schema for the Java repository contracts.
-- The migration runner records this file only after every statement succeeds.
-- Keep the account/profile UUID shared and save slots limited to 1..3.

CREATE TABLE IF NOT EXISTS accounts (
    id            CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
    username      VARCHAR(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NOT NULL UNIQUE,
    display_name  VARCHAR(100) NOT NULL,
    created_at    DATETIME(6) NOT NULL
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4;

CREATE TABLE IF NOT EXISTS credentials (
    account_id        CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
    encoded_verifier  VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    CONSTRAINT fk_credentials_account FOREIGN KEY (account_id)
        REFERENCES accounts(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4;

CREATE TABLE IF NOT EXISTS students (
    id                 CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
    display_name       VARCHAR(100) NOT NULL,
    reading_level      ENUM('BEGINNER','DEVELOPING','PROFICIENT','ADVANCED') NOT NULL,
    completed_attempts INT UNSIGNED NOT NULL DEFAULT 0,
    accuracy           DOUBLE NOT NULL DEFAULT 0,
    CONSTRAINT ck_student_accuracy CHECK (accuracy >= 0 AND accuracy <= 1),
    CONSTRAINT fk_students_account FOREIGN KEY (id)
        REFERENCES accounts(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4;

CREATE TABLE IF NOT EXISTS chapters (
    id          CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
    title       VARCHAR(200) NOT NULL,
    passage     MEDIUMTEXT NOT NULL,
    difficulty  ENUM('EASY','MEDIUM','HARD') NOT NULL
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4;

CREATE TABLE IF NOT EXISTS questions (
    id                    CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
    chapter_id            CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    question_order        SMALLINT UNSIGNED NOT NULL,
    prompt                TEXT NOT NULL,
    correct_choice_index  SMALLINT UNSIGNED NOT NULL,
    UNIQUE KEY uq_question_chapter_order (chapter_id, question_order),
    UNIQUE KEY uq_question_id_chapter (id, chapter_id),
    CONSTRAINT fk_questions_chapter FOREIGN KEY (chapter_id)
        REFERENCES chapters(id) ON DELETE CASCADE,
    CONSTRAINT ck_question_order CHECK (question_order >= 1)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4;

CREATE TABLE IF NOT EXISTS question_choices (
    question_id  CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    choice_index SMALLINT UNSIGNED NOT NULL,
    choice_text  VARCHAR(1000) NOT NULL,
    PRIMARY KEY (question_id, choice_index),
    CONSTRAINT fk_choices_question FOREIGN KEY (question_id)
        REFERENCES questions(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4;

CREATE TABLE IF NOT EXISTS story_nodes (
    chapter_id     CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    node_id        VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    speaker_name   VARCHAR(100) NOT NULL,
    dialogue_text  MEDIUMTEXT NOT NULL,
    is_thought     BOOLEAN NOT NULL DEFAULT FALSE,
    next_node_id   VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NULL,
    reading_check  BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (chapter_id, node_id),
    CONSTRAINT fk_story_nodes_chapter FOREIGN KEY (chapter_id)
        REFERENCES chapters(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4;

CREATE TABLE IF NOT EXISTS story_choices (
    chapter_id     CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    from_node_id   VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    choice_id      VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    choice_order   SMALLINT UNSIGNED NOT NULL,
    choice_text    VARCHAR(500) NOT NULL,
    to_node_id     VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    PRIMARY KEY (chapter_id, from_node_id, choice_id),
    UNIQUE KEY uq_story_choice_order (chapter_id, from_node_id, choice_order),
    CONSTRAINT fk_story_choice_source FOREIGN KEY (chapter_id, from_node_id)
        REFERENCES story_nodes(chapter_id, node_id) ON DELETE CASCADE,
    CONSTRAINT fk_story_choice_destination FOREIGN KEY (chapter_id, to_node_id)
        REFERENCES story_nodes(chapter_id, node_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4;

CREATE TABLE IF NOT EXISTS chapter_start_nodes (
    chapter_id  CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
    node_id     VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    CONSTRAINT fk_chapter_start_node FOREIGN KEY (chapter_id, node_id)
        REFERENCES story_nodes(chapter_id, node_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4;

CREATE TABLE IF NOT EXISTS assessment_drafts (
    session_id   CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
    student_id   CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    chapter_id   CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    story_node_id VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    created_at   DATETIME(6) NOT NULL,
    completed_at DATETIME(6) NULL,
    UNIQUE KEY uq_draft_student_chapter (student_id, chapter_id),
    UNIQUE KEY uq_draft_session_owner (session_id, student_id, chapter_id),
    CONSTRAINT fk_drafts_student FOREIGN KEY (student_id)
        REFERENCES students(id) ON DELETE CASCADE,
    CONSTRAINT fk_drafts_chapter FOREIGN KEY (chapter_id)
        REFERENCES chapters(id) ON DELETE CASCADE,
    CONSTRAINT fk_drafts_reading_node FOREIGN KEY (chapter_id, story_node_id)
        REFERENCES story_nodes(chapter_id, node_id)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4;

CREATE TABLE IF NOT EXISTS assessment_draft_answers (
    session_id            CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    student_id            CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    chapter_id            CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    question_id           CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    selected_choice_index SMALLINT UNSIGNED NOT NULL,
    answered_at           DATETIME(6) NOT NULL,
    PRIMARY KEY (session_id, question_id),
    CONSTRAINT fk_draft_answer_session FOREIGN KEY (session_id, student_id, chapter_id)
        REFERENCES assessment_drafts(session_id, student_id, chapter_id) ON DELETE CASCADE,
    CONSTRAINT fk_draft_answer_question FOREIGN KEY (question_id, chapter_id)
        REFERENCES questions(id, chapter_id) ON DELETE CASCADE,
    CONSTRAINT fk_draft_answer_choice FOREIGN KEY (question_id, selected_choice_index)
        REFERENCES question_choices(question_id, choice_index)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4;

CREATE TABLE IF NOT EXISTS assessments (
    id                   CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
    student_id           CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    chapter_id           CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    correct_answers      SMALLINT UNSIGNED NOT NULL,
    total_questions      SMALLINT UNSIGNED NOT NULL,
    assessed_difficulty  ENUM('EASY','MEDIUM','HARD') NOT NULL,
    next_difficulty      ENUM('EASY','MEDIUM','HARD') NOT NULL,
    completed_at         DATETIME(6) NOT NULL,
    UNIQUE KEY uq_assessment_student_chapter (student_id, chapter_id),
    CONSTRAINT fk_assessment_student FOREIGN KEY (student_id)
        REFERENCES students(id) ON DELETE CASCADE,
    CONSTRAINT fk_assessment_chapter FOREIGN KEY (chapter_id)
        REFERENCES chapters(id) ON DELETE CASCADE,
    CONSTRAINT ck_assessment_total CHECK (total_questions BETWEEN 1 AND 100),
    CONSTRAINT ck_assessment_correct CHECK (correct_answers <= total_questions)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4;

CREATE TABLE IF NOT EXISTS save_slots (
    student_id      CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    slot            TINYINT UNSIGNED NOT NULL,
    chapter_id      CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    story_node_id   VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    difficulty      ENUM('EASY','MEDIUM','HARD') NOT NULL,
    saved_at        DATETIME(6) NOT NULL,
    game_state_json JSON NULL,
    PRIMARY KEY (student_id, slot),
    CONSTRAINT fk_save_slot_student FOREIGN KEY (student_id)
        REFERENCES students(id) ON DELETE CASCADE,
    CONSTRAINT fk_save_slot_node FOREIGN KEY (chapter_id, story_node_id)
        REFERENCES story_nodes(chapter_id, node_id),
    CONSTRAINT ck_save_slot_number CHECK (slot BETWEEN 1 AND 3)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4;
