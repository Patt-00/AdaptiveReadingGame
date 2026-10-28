# Storyboard source and reading draft

`storyboard-draft.txt` is a text snapshot of the [supplied Google Doc](https://docs.google.com/document/d/1XCnuefpntPIm9DIGByQVLk0237jc4Sa2aV80zqCjbm8/edit). It preserves the art guide, authored script, questions, and notes. Line endings are normalized to LF. `storyboard-map.json` records its SHA-256 and the source lines used by each playable node.

The application reads the adjacent JSON story pack. The source snapshot and map support authoring review and are not loaded by JavaFX. The playable pack includes the source narrative through the Study and Hall investigations. Formatting notes, speaker headers, and art instructions become screen presentation rather than literal dialogue. The opening notice and final draft-boundary message describe the state of the supplied content.

## Editorial decisions

- The draft uses Prince Owen consistently where the source alternates between Owen and Ellis. The player remains an unnamed servant. Gender placeholders use “servant” or neutral pronouns.
- Character expressions use the supplied sprites. When the named expression has no image, the character uses the closest available expression or neutral sprite.
- The Study hard prompt uses the described evidence because the source names a revoked knighthood decree without supplying that clue. The six answer keys are inferred and visibly provisional.
- Each investigation can be visited first or second. An outcome after the second investigation leads to the draft boundary rather than directing the reader back to an already visited room.
- The source repeats the drinking and stasis-lock events. Those beats remain available for the story author to review.
- The story's time pressure and references to lost minutes remain fictional prose. The player has no countdown or time penalty, and dialogue has no voice recording.
- The poisoning CG is available during the poisoning scene. The killer-reveal CG remains outside the graph while the conclusion is undecided.

Use [the content-pack guide](../../README.md) to add approved material. A conclusion can extend the graph through content nodes. Publish a new chapter ID when existing learner records refer to the draft.
