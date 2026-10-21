-- =====================================================================
-- STORY: CHAPTER 1, PART 1 (intro)      source: ScriptPart1.docx
-- Loads once into a fresh database. After EDITING anything here, reload with:
--     bash database/setup.sh --reset
-- (Running it twice without --reset gives "Duplicate entry". That is normal.)
-- Plain INSERT is used on purpose: a typo in an id then shows an error
-- instead of silently skipping the row.
--
-- HOW THIS FILE IS BUILT (copy this pattern for the next parts):
--   1. SCENES  (story_nodes): one row per scene. A scene is one stretch of
--      lines with NO decision inside it.
--        node_id = chapter-route-scene, zero padded, kept as TEXT.
--        route 0000 = common route; 0001, 0002 ... = routes after a split.
--        scene numbers start at 0001 inside each route.
--   2. LINES   (story_lines): the text, in order (line_no 1, 2, 3 ...).
--        line_type  SPEECH     spoken line (quote marks are NOT stored)
--                   THOUGHT    italic text in the thought box (MC)
--                   CENTERED   centered text on a black screen
--                   DIRECTION  stage direction, not shown as text
--        speaker    name tag, or NULL when there is none
--        expression lowercase sprite mood (serious, smiling ...), or NULL
--   3. LINKS   (story_choices), always LAST, because they point to scenes:
--        choice_text NULL        = automatic "Next" (one link only)
--        choice_text 'some text' = a button the player picks
--        choice_style SPEECH / ACTION / THOUGHT = how the button looks
--   A scene with NO link is the end of the written story so far.
--
-- Kept exactly as in the script: spelling and punctuation.
-- Removed: outer quote marks of spoken lines, bold/italic markup, extra spaces.
-- =====================================================================

USE adaptive_reading;
SET NAMES utf8mb4;

SET @ch1 = 'cccccccc-0000-0000-0000-000000000001';

-- ---------- CHAPTER (PLACEHOLDER title, passage, difficulty) ----------
INSERT INTO chapters (id, title, passage, difficulty) VALUES
(@ch1, 'Chapter 1',
 'Lorem ipsum dolor sit amet, consectetur adipiscing elit. [CHAPTER 1 READING PASSAGE GOES HERE]',
 'EASY');

-- ---------- 1. SCENES ----------
INSERT INTO story_nodes
  (chapter_id, node_id, scene_name, is_start, location_label, background_image) VALUES
(@ch1, '0001-0000-0001', 'Prologue - Poem', 1, NULL, 'bg_black.png'),
(@ch1, '0001-0000-0002', 'Prologue - MC', 0, NULL, 'bg_black.png'),
(@ch1, '0001-0000-0003', 'Solarium Hall - Amelia''s orders', 0, 'SOLARIUM HALL, INT., EVENING', 'bg_solarium_hall_evening.png'),
(@ch1, '0001-0000-0004', 'Solarium Hall - Amelia pleased', 0, NULL, 'bg_solarium_hall_evening.png'),
(@ch1, '0001-0000-0005', 'Solarium Hall - Amelia stern', 0, NULL, 'bg_solarium_hall_evening.png'),
(@ch1, '0001-0000-0006', 'Solarium Hall - Eris arrives', 0, NULL, 'bg_solarium_hall_evening.png'),
(@ch1, '0001-0000-0007', 'Solarium Hall - Eris warm', 0, NULL, 'bg_solarium_hall_evening.png'),
(@ch1, '0001-0000-0008', 'Solarium Hall - Eris teasing', 0, NULL, 'bg_solarium_hall_evening.png'),
(@ch1, '0001-0000-0009', 'Solarium Hall - Eris annoyed', 0, NULL, 'bg_solarium_hall_evening.png'),
(@ch1, '0001-0000-0010', 'Solarium Hall - Berry tart request', 0, NULL, 'bg_solarium_hall_evening.png'),
(@ch1, '0001-0001-0001', 'Tart Route - Leaving the hall', 0, NULL, 'bg_solarium_hall_evening.png'),
(@ch1, '0001-0002-0001', 'Guests Route - Leaving the hall', 0, NULL, 'bg_solarium_hall_evening.png');
-- bg_*.png are PLACEHOLDER file names. Rename them to your real image files.

-- ---------- 2. LINES ----------

-- 0001-0000-0001  Prologue - Poem
-- Poem by Arthur Rimbaud. Black screen, centered text.
INSERT INTO story_lines
  (chapter_id, node_id, line_no, line_type, speaker, expression, dialogue) VALUES
(@ch1, '0001-0000-0001', 1, 'CENTERED', NULL, NULL, '“The Sun, the hearth of life and tenderness,'),
(@ch1, '0001-0000-0001', 2, 'CENTERED', NULL, NULL, 'Pours burning love on the delighted earth,'),
(@ch1, '0001-0000-0001', 3, 'CENTERED', NULL, NULL, 'And when you rest in the valley you know,'),
(@ch1, '0001-0000-0001', 4, 'CENTERED', NULL, NULL, 'How nubile earth is, how it overflows;'),
(@ch1, '0001-0000-0001', 5, 'CENTERED', NULL, NULL, 'How, raised up by soul, its immense breast'),
(@ch1, '0001-0000-0001', 6, 'CENTERED', NULL, NULL, 'Is love, as God is, and, like woman, flesh,'),
(@ch1, '0001-0000-0001', 7, 'CENTERED', NULL, NULL, 'And big with sap and sunlight will enclose'),
(@ch1, '0001-0000-0001', 8, 'CENTERED', NULL, NULL, 'The mighty seething of all embryos!”');

-- 0001-0000-0002  Prologue - MC
-- Black screen, centered text.
INSERT INTO story_lines
  (chapter_id, node_id, line_no, line_type, speaker, expression, dialogue) VALUES
(@ch1, '0001-0000-0002', 1, 'CENTERED', 'MC', NULL, 'Have you ever experienced something beautiful?'),
(@ch1, '0001-0000-0002', 2, 'CENTERED', 'MC', NULL, 'Something so unmistakably bright that your eyes hurt?'),
(@ch1, '0001-0000-0002', 3, 'CENTERED', 'MC', NULL, 'Something so perfect that you couldn''t help but reach for it.'),
(@ch1, '0001-0000-0002', 4, 'CENTERED', 'MC', NULL, 'Oh, Princess.'),
(@ch1, '0001-0000-0002', 5, 'CENTERED', 'MC', NULL, 'Truly your light will never waver even at the most adverse situation.');

-- 0001-0000-0003  Solarium Hall - Amelia's orders
-- Ends with CHOICE SET 1 (2 buttons).
INSERT INTO story_lines
  (chapter_id, node_id, line_no, line_type, speaker, expression, dialogue) VALUES
(@ch1, '0001-0000-0003', 1, 'THOUGHT', 'MC', NULL, 'Truly, Her Highness’ taste in parties really is peculiar.'),
(@ch1, '0001-0000-0003', 2, 'SPEECH', 'MC', NULL, 'It’s quite the intimate setting…'),
(@ch1, '0001-0000-0003', 3, 'THOUGHT', 'MC', NULL, 'It’s a rare occasion for our princess to entertain guests outside the daylight hours, even more so hosting one.'),
(@ch1, '0001-0000-0003', 4, 'THOUGHT', 'MC', NULL, 'It must be of urgent matter for her to hastily put up a somewhat modest gathering.'),
(@ch1, '0001-0000-0003', 5, 'SPEECH', 'Amelia', 'serious', 'MC, Was it?'),
(@ch1, '0001-0000-0003', 6, 'THOUGHT', 'MC', NULL, '???'),
(@ch1, '0001-0000-0003', 7, 'SPEECH', 'Amelia', 'neutral', 'Please have the initiative to finalize the refreshments for the guests before Her Highness asks.'),
(@ch1, '0001-0000-0003', 8, 'SPEECH', 'Amelia', 'neutral', 'Seeing we have special guests coming, she expects everything to operate smoothly. Is that clear?'),
(@ch1, '0001-0000-0003', 9, 'THOUGHT', 'MC', NULL, 'Ah, Lady Amelia.'),
(@ch1, '0001-0000-0003', 10, 'THOUGHT', 'MC', NULL, 'The Princess’ Head Lady-in-Waiting and overseer of us, the humble servants.'),
(@ch1, '0001-0000-0003', 11, 'THOUGHT', 'MC', NULL, 'She’s in charge of all the duties the Mistress overlooked or needed help with- not that her intellect couldn’t handle such administrative work-Lady Amelia is the first person you’d see with Her Highness.'),
(@ch1, '0001-0000-0003', 12, 'THOUGHT', 'MC', NULL, 'I heard that she was our Master’s most loyal servant after her fallen family’s debt had been cleared.'),
(@ch1, '0001-0000-0003', 13, 'SPEECH', 'Amelia', 'serious', '…'),
(@ch1, '0001-0000-0003', 14, 'SPEECH', 'Amelia', 'serious', 'I trust you’ve heard me the first time?');

-- 0001-0000-0004  Solarium Hall - Amelia pleased
-- Reaction to choice 1, option 1.
INSERT INTO story_lines
  (chapter_id, node_id, line_no, line_type, speaker, expression, dialogue) VALUES
(@ch1, '0001-0000-0004', 1, 'SPEECH', 'Amelia', 'smiling', 'Good.');

-- 0001-0000-0005  Solarium Hall - Amelia stern
-- Reaction to choice 1, option 2.
INSERT INTO story_lines
  (chapter_id, node_id, line_no, line_type, speaker, expression, dialogue) VALUES
(@ch1, '0001-0000-0005', 1, 'SPEECH', 'Amelia', 'serious', '…');

-- 0001-0000-0006  Solarium Hall - Eris arrives
-- Ends with CHOICE SET 2 (3 buttons).
INSERT INTO story_lines
  (chapter_id, node_id, line_no, line_type, speaker, expression, dialogue) VALUES
(@ch1, '0001-0000-0006', 1, 'SPEECH', 'Amelia', 'serious', 'Please accomplish this as soon as possible.'),
(@ch1, '0001-0000-0006', 2, 'SPEECH', 'Amelia', 'serious', 'I was instructed that this gathering is so private that even servants are not allowed attendance.'),
(@ch1, '0001-0000-0006', 3, 'THOUGHT', 'MC', NULL, 'Oh.'),
(@ch1, '0001-0000-0006', 4, 'THOUGHT', 'MC', NULL, 'It must be an administrative related gathering, then.'),
(@ch1, '0001-0000-0006', 5, 'THOUGHT', 'MC', NULL, 'It’s understandable.'),
(@ch1, '0001-0000-0006', 6, 'THOUGHT', 'MC', NULL, 'Servants in a kingdom have wide ears and are susceptible to spreading gossip or falsehood.'),
(@ch1, '0001-0000-0006', 7, 'THOUGHT', 'MC', NULL, 'It doesn’t help that Her Highness is a very precise and private person.'),
(@ch1, '0001-0000-0006', 8, 'THOUGHT', 'MC', NULL, 'Even I haven’t seen the princess for myself, save for the murals I’ve seen around the palace walls.'),
(@ch1, '0001-0000-0006', 9, 'THOUGHT', 'MC', NULL, 'I’ve also heard that people claimed that it wasn’t half of her beauty and grace.'),
(@ch1, '0001-0000-0006', 10, 'SPEECH', '???', NULL, 'My, Lady Amelia,still working hard even at a party?'),
(@ch1, '0001-0000-0006', 11, 'DIRECTION', NULL, NULL, 'Eris shows in frame'),
(@ch1, '0001-0000-0006', 12, 'THOUGHT', 'MC', NULL, 'Lady Eris.'),
(@ch1, '0001-0000-0006', 13, 'THOUGHT', 'MC', NULL, 'Having the continent’s most revered monarch as your closest confidante must’ve boosted her social standing,'),
(@ch1, '0001-0000-0006', 14, 'THOUGHT', 'MC', NULL, 'But it feels like she isn’t making it a big deal about it. The master''s inner court really is amazing.'),
(@ch1, '0001-0000-0006', 15, 'THOUGHT', 'MC', NULL, 'Gracious, beautiful, humble, and stylish. She is every noblewoman’s dream, second only to our Princess of course.'),
(@ch1, '0001-0000-0006', 16, 'THOUGHT', 'MC', NULL, 'The palace whispers also pertained that Lady Eris quell any negative rumors anyone might have against our great princess.'),
(@ch1, '0001-0000-0006', 17, 'SPEECH', 'Eris', 'serious', 'You.');

-- 0001-0000-0007  Solarium Hall - Eris warm
-- Reaction to choice 2, option 1.
INSERT INTO story_lines
  (chapter_id, node_id, line_no, line_type, speaker, expression, dialogue) VALUES
(@ch1, '0001-0000-0007', 1, 'SPEECH', 'Eris', 'smiling', 'Oh, lighten up, dear!'),
(@ch1, '0001-0000-0007', 2, 'SPEECH', 'Eris', 'smiling', 'It’s not like I bite.');

-- 0001-0000-0008  Solarium Hall - Eris teasing
-- Reaction to choice 2, option 2.
INSERT INTO story_lines
  (chapter_id, node_id, line_no, line_type, speaker, expression, dialogue) VALUES
(@ch1, '0001-0000-0008', 1, 'SPEECH', 'Eris', NULL, 'Yes you, silly.'),
(@ch1, '0001-0000-0008', 2, 'SPEECH', 'Eris', NULL, 'Who else would I be pertaining to?');

-- 0001-0000-0009  Solarium Hall - Eris annoyed
-- Reaction to choice 2, option 3.
INSERT INTO story_lines
  (chapter_id, node_id, line_no, line_type, speaker, expression, dialogue) VALUES
(@ch1, '0001-0000-0009', 1, 'SPEECH', 'Eris', 'annoyed', '…'),
(@ch1, '0001-0000-0009', 2, 'SPEECH', 'Eris', 'smiling', '*Ahem*');

-- 0001-0000-0010  Solarium Hall - Berry tart request
-- Ends with CHOICE SET 3 (2 buttons) = the ROUTE SPLIT.
INSERT INTO story_lines
  (chapter_id, node_id, line_no, line_type, speaker, expression, dialogue) VALUES
(@ch1, '0001-0000-0010', 1, 'SPEECH', 'Eris', 'smiling', 'Anyways, could you be so kind and bring me one of those tasty berry tarts once they’re available?');

-- 0001-0001-0001  Tart Route - Leaving the hall
-- ROUTE 1 = PICK UP TART. Lines 2-12 are the same as in route 2: keep them in sync.
INSERT INTO story_lines
  (chapter_id, node_id, line_no, line_type, speaker, expression, dialogue) VALUES
(@ch1, '0001-0001-0001', 1, 'DIRECTION', NULL, NULL, 'Eris smiles while Amelia is annoyed'),
(@ch1, '0001-0001-0001', 2, 'SPEECH', 'Amelia', NULL, 'Please refrain from distracting the staff away from their assigned tasks, Lady Eris.'),
(@ch1, '0001-0001-0001', 3, 'SPEECH', 'Eris', NULL, 'Isn’t catering to a guest one of their duties?'),
(@ch1, '0001-0001-0001', 4, 'SPEECH', 'Eris', NULL, 'Besides, you ought to liven up for a change. Halles will worry again if you keep overworking yourself.'),
(@ch1, '0001-0001-0001', 5, 'SPEECH', 'Amelia', 'distressed', '…'),
(@ch1, '0001-0001-0001', 6, 'THOUGHT', 'MC', NULL, 'For Lady Eris to address Her Highness so casually, they must be really close.'),
(@ch1, '0001-0001-0001', 7, 'THOUGHT', 'MC', NULL, 'The respect they have for each other runs so deep that I feel that the Lady could predict the master’s behaviour.'),
(@ch1, '0001-0001-0001', 8, 'SPEECH', 'Amelia', NULL, '…Very well.'),
(@ch1, '0001-0001-0001', 9, 'THOUGHT', 'MC', NULL, 'I should leave.'),
(@ch1, '0001-0001-0001', 10, 'THOUGHT', 'MC', NULL, 'It’d be rude to intrude in their conversation.'),
(@ch1, '0001-0001-0001', 11, 'SPEECH', 'MC', NULL, 'If I may, I would like to excuse myself.'),
(@ch1, '0001-0001-0001', 12, 'THOUGHT', 'MC', NULL, 'I bowed to them as a courtesy, hanging my head low before leaving.'),
(@ch1, '0001-0001-0001', 13, 'THOUGHT', 'MC', NULL, 'Lady Eris must’ve been waiting for her tart. I should check the kitchens if there are any available.');

-- 0001-0002-0001  Guests Route - Leaving the hall
-- ROUTE 2 = GREET GUESTS. Lines 2-12 are the same as in route 1: keep them in sync.
INSERT INTO story_lines
  (chapter_id, node_id, line_no, line_type, speaker, expression, dialogue) VALUES
(@ch1, '0001-0002-0001', 1, 'DIRECTION', NULL, NULL, 'Amelia smiles while Eris is annoyed'),
(@ch1, '0001-0002-0001', 2, 'SPEECH', 'Amelia', NULL, 'Please refrain from distracting the staff away from their assigned tasks, Lady Eris.'),
(@ch1, '0001-0002-0001', 3, 'SPEECH', 'Eris', NULL, 'Isn’t catering to a guest one of their duties?'),
(@ch1, '0001-0002-0001', 4, 'SPEECH', 'Eris', NULL, 'Besides, you ought to liven up for a change. Halles will worry again if you keep overworking yourself.'),
(@ch1, '0001-0002-0001', 5, 'SPEECH', 'Amelia', 'distressed', '…'),
(@ch1, '0001-0002-0001', 6, 'THOUGHT', 'MC', NULL, 'For Lady Eris to address Her Highness so casually, they must be really close.'),
(@ch1, '0001-0002-0001', 7, 'THOUGHT', 'MC', NULL, 'The respect they have for each other runs so deep that I feel that the Lady could predict the master’s behaviour.'),
(@ch1, '0001-0002-0001', 8, 'SPEECH', 'Amelia', NULL, '…Very well.'),
(@ch1, '0001-0002-0001', 9, 'THOUGHT', 'MC', NULL, 'I should leave.'),
(@ch1, '0001-0002-0001', 10, 'THOUGHT', 'MC', NULL, 'It’d be rude to intrude in their conversation.'),
(@ch1, '0001-0002-0001', 11, 'SPEECH', 'MC', NULL, 'If I may, I would like to excuse myself.'),
(@ch1, '0001-0002-0001', 12, 'THOUGHT', 'MC', NULL, 'I bowed to them as a courtesy, hanging my head low before leaving.'),
(@ch1, '0001-0002-0001', 13, 'THOUGHT', 'MC', NULL, 'Other guests are arriving. I should welcome them.');

-- ---------- 3. LINKS (always last) ----------
INSERT INTO story_choices
  (chapter_id, from_node_id, choice_order, choice_style, choice_text, to_node_id) VALUES
(@ch1, '0001-0000-0001', 1, NULL, NULL, '0001-0000-0002'),
(@ch1, '0001-0000-0002', 1, NULL, NULL, '0001-0000-0003'),
(@ch1, '0001-0000-0003', 1, 'SPEECH', 'Ah, of course, my Lady. I’ll confer with the other servants.', '0001-0000-0004'),  -- CHOICE SET 1
(@ch1, '0001-0000-0003', 2, 'ACTION', 'Nod in acknowledgement', '0001-0000-0005'),
(@ch1, '0001-0000-0004', 1, NULL, NULL, '0001-0000-0006'),
(@ch1, '0001-0000-0005', 1, NULL, NULL, '0001-0000-0006'),
(@ch1, '0001-0000-0006', 1, 'SPEECH', 'Y-yes, Lady Eris…?', '0001-0000-0007'),  -- CHOICE SET 2
(@ch1, '0001-0000-0006', 2, 'SPEECH', 'Me?', '0001-0000-0008'),
(@ch1, '0001-0000-0006', 3, 'THOUGHT', 'Maybe she was calling someone else…', '0001-0000-0009'),
(@ch1, '0001-0000-0007', 1, NULL, NULL, '0001-0000-0010'),
(@ch1, '0001-0000-0008', 1, NULL, NULL, '0001-0000-0010'),
(@ch1, '0001-0000-0009', 1, NULL, NULL, '0001-0000-0010'),
(@ch1, '0001-0000-0010', 1, 'SPEECH', 'Of course, Madam.', '0001-0001-0001'),  -- CHOICE SET 3 = ROUTE SPLIT (1 = Pick up tart, 2 = Greet guests)
(@ch1, '0001-0000-0010', 2, 'SPEECH', 'There would be a dessert cart ready soon, my Lady.', '0001-0002-0001');

-- NEXT PART: add links from 0001-0001-0001 and 0001-0002-0001 to the first
-- scene of the following part, using the same INSERT as above.

-- ---------- ASSESSMENT (PLACEHOLDER question; choice 0 is the correct one) ----------
INSERT INTO questions (id, chapter_id, question_order, prompt, correct_choice_index) VALUES
('dddddddd-0000-0000-0000-000000000001', @ch1, 1,
 'Lorem ipsum dolor sit amet? [QUESTION 1 GOES HERE]', 0);

INSERT INTO question_choices (question_id, choice_index, choice_text) VALUES
('dddddddd-0000-0000-0000-000000000001', 0, 'Lorem ipsum (correct answer goes here)'),
('dddddddd-0000-0000-0000-000000000001', 1, 'Dolor sit amet (wrong answer B)'),
('dddddddd-0000-0000-0000-000000000001', 2, 'Consectetur elit (wrong answer C)');
