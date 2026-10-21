-- This SQL is for The story dialogue, scenes, and other things related to story.

USE adaptive_reading;

-- One prepared reading chapter (one difficulty variant).
CREATE TABLE IF NOT EXISTS chapters (
    id          CHAR(36)     PRIMARY KEY,
    title       VARCHAR(200) NOT NULL,
    passage     TEXT         NOT NULL,
    difficulty  ENUM('EASY','MEDIUM','HARD') NOT NULL
    -- PLACEHOLDER: logical-story grouping so the same story is not copied
    -- for every difficulty variant. Decide with the group, then uncomment:
    -- , logical_chapter_id CHAR(36) NULL
);

-- A SCENE: one stretch of lines with no decision inside it.
-- node_id is a STABLE text id 'chapter-route-scene' such as '0001-0002-0003'
-- (saves store it, so never rename an id after release).
CREATE TABLE IF NOT EXISTS story_nodes (
    chapter_id        CHAR(36)     NOT NULL,
    node_id           VARCHAR(100) NOT NULL,
    scene_name        VARCHAR(100) NOT NULL,   -- label shown in the save slots
    is_start          TINYINT(1)   NOT NULL DEFAULT 0,  -- 1 = where the chapter begins
    location_label    VARCHAR(100) NULL,       -- heading card, e.g. 'SOLARIUM HALL, INT., EVENING'
    background_image  VARCHAR(255) NULL,       -- image file name (or an image_assets id later)
    -- PLACEHOLDER: more scene columns when needed, for example:
    -- music VARCHAR(255) NULL,
    PRIMARY KEY (chapter_id, node_id),
    FOREIGN KEY (chapter_id) REFERENCES chapters(id) ON DELETE CASCADE
);

-- The text of a scene, one row per line, shown in line_no order.
CREATE TABLE IF NOT EXISTS story_lines (
    chapter_id  CHAR(36)     NOT NULL,
    node_id     VARCHAR(100) NOT NULL,
    line_no     INT          NOT NULL CHECK (line_no >= 1),
    line_type   ENUM('SPEECH','THOUGHT','CENTERED','DIRECTION') NOT NULL,
    speaker     VARCHAR(100) NULL,             -- name tag; NULL = none
    expression  VARCHAR(50)  NULL,             -- sprite mood, lowercase: 'serious', 'smiling' ...
    dialogue    TEXT         NOT NULL,
    PRIMARY KEY (chapter_id, node_id, line_no),
    FOREIGN KEY (chapter_id, node_id)
        REFERENCES story_nodes(chapter_id, node_id) ON DELETE CASCADE
);

-- Links from one scene to the next.
--   choice_text NULL      = automatic "Next" (a scene has exactly ONE such link)
--   choice_text has text  = a button the player picks (2 or more links)
CREATE TABLE IF NOT EXISTS story_choices (
    chapter_id    CHAR(36)     NOT NULL,
    from_node_id  VARCHAR(100) NOT NULL,
    choice_order  INT          NOT NULL,       -- 1, 2, 3 ... order of the buttons
    choice_style  ENUM('SPEECH','ACTION','THOUGHT') NULL,  -- how the button looks
    choice_text   VARCHAR(300) NULL,
    to_node_id    VARCHAR(100) NOT NULL,
    PRIMARY KEY (chapter_id, from_node_id, choice_order),
    CHECK ((choice_text IS NULL AND choice_style IS NULL)
        OR (choice_text IS NOT NULL AND choice_style IS NOT NULL)),
    FOREIGN KEY (chapter_id, from_node_id) REFERENCES story_nodes(chapter_id, node_id) ON DELETE CASCADE,
    FOREIGN KEY (chapter_id, to_node_id)   REFERENCES story_nodes(chapter_id, node_id) ON DELETE CASCADE
);
