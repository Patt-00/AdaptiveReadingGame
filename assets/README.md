# Story content and art

The JavaFX application loads the provisional investigation pack from `assets/content/hailey-investigation-draft.json` when `ARG_CONTENT_PACK` is not set. The pack includes the available narrative and authored choices from the group storyboard through both investigations. The player is an unnamed servant, and Prince Owen is the working name. The culprit and final ending remain undecided. The source hard Study question names a revoked knighthood decree that the storyboard does not describe, so the pack's version uses the described clues instead. Its answer keys are draft interpretations of the supplied clues, not an approved teacher answer key.

## Run the draft pack

Run from the project root. Java uses in-memory storage when no `ARG_DB_*` variables are set.

```sh
mvn javafx:run
```

To load a different local pack, set `ARG_CONTENT_PACK` before launching.

Linux or macOS:

```sh
export ARG_CONTENT_PACK="/path/to/story-pack.json"
mvn javafx:run
```

Windows PowerShell:

```powershell
$env:ARG_CONTENT_PACK = "C:\path\to\story-pack.json"
mvn javafx:run
```

With MySQL, apply the migrations in [the database guide](../database/README.md) first. The importer keeps stable chapter, checkpoint, and question IDs. Reimporting identical content is safe. Content referenced by learner records is immutable. Give revised content a new chapter ID.

## Pack format version 1

Version 1 remains supported for chapters with one reading check. The top-level object has `formatVersion` set to `1` and a `chapters` array. Each chapter has `id`, `title`, `difficulty`, `passage`, `startNodeId`, `nodes`, and `questions`.

Each node has `id`, `speakerName`, `dialogueText`, `thought`, `readingCheck`, `nextNodeId`, and `choices`. `speakerAssetKey` is optional. A node uses either `nextNodeId`, `choices`, or a reading check. Each question has `id`, `prompt`, `choices`, and a zero-based `correctChoiceIndex`; `explanation` is optional.

The loader requires one reading-check node. Every node must be reachable, the graph must not contain loops, and every path must reach the reading check. A chapter can contain 1 to 100 questions.

## Pack format version 2

Version 2 supports several scored checkpoints in one story chapter. The top-level shape is still `formatVersion` plus `chapters`, with `formatVersion` set to `2`. Each chapter has `id`, `title`, `difficulty`, `passage`, `startNodeId`, `nodes`, and `checkpoints`.

Version 2 nodes keep the version 1 fields. They can also set `checkpointId`, `storyComplete`, `speakerAssetKey`, `sceneAssetKey`, and `cgAssetKey`. A story choice can set an optional `routeId`. Use `storyComplete: true` only for a clearly identified draft or story endpoint. Leave `nextNodeId` empty when a node has choices, marks a checkpoint, or marks story completion.

Each checkpoint has these fields:

- `id`: a stable checkpoint identifier.
- `nodeId`: the reading-check node whose `checkpointId` matches this value.
- `passage`: the text shown with the checkpoint questions.
- `correctNextNodeId` and `incorrectNextNodeId`: the story destinations after the scored answers.
- `questionsByDifficulty`: exactly `EASY`, `MEDIUM`, and `HARD`, each containing 1 to 100 questions.

Every question keeps the version 1 question fields. The loader names each stored question from the chapter, checkpoint, difficulty, and source question IDs. Keep those source IDs stable after a learner result refers to them.

The version 2 loader rejects unknown fields, missing difficulty variants, invalid answer keys, broken destinations, unreachable nodes, loops, and paths that do not end at a story-completion node. Every checkpoint outcome must lead to another checkpoint or story completion. A chapter can contain at most 600 questions across all checkpoint variants.

Story choices change the narrative route and do not affect the score. The first submitted answer is stored and scored. Java chooses the next checkpoint level after the saved result. The optional local model can suggest a level; Java validates the suggestion and keeps its fallback. It does not choose the correct answer.

## Art folders and asset keys

Keep only the key in the story pack. Do not store absolute paths. Keys use letters, numbers, dots, underscores, and hyphens. The resolvers check only the matching project subfolder and treat missing or invalid files as optional.

| Asset key field | Folder | Supported files |
| --- | --- | --- |
| `speakerAssetKey` | `assets/characters/` | `.png`, `.jpg`, `.jpeg`, `.gif` |
| `sceneAssetKey` | `assets/scenes/` | `.png`, `.jpg`, `.jpeg` |
| `cgAssetKey` | `assets/cg/` | `.png`, `.jpg`, `.jpeg` |

The game displays written dialogue, questions, and images. Missing images leave the text available.

## Music and sound effects

The title, welcome, login, and main menu use [Comfortable Mystery by Kevin MacLeod](https://incompetech.com/music/royalty-free/index.html?Search=Search&isrc=USUAN1100287), licensed under [Creative Commons Attribution 4.0](https://creativecommons.org/licenses/by/4.0/). The track was converted from MP3 to PCM WAV for JavaFX playback and is bundled at `src/main/resources/com/adaptivereadinggame/audio/comfortable-mystery.wav`. The WAV is tracked with Git LFS. The game uses an original generated click tone at `src/main/resources/com/adaptivereadinggame/audio/click.wav`.

Audio Settings is available on the title screen, welcome screen, and main menu. Music and effects have separate session-only volume sliders; setting a slider to zero mutes that sound. Music pauses during story and reading screens. The game remains usable if audio playback is unavailable.

Set `ARG_ASSET_DIR` only when the `characters`, `scenes`, and `cg` folders are inside another trusted asset root. The default root is `assets/` in the project directory.

The artwork paths have Git LFS rules in `.gitattributes`. Git LFS handles those files when they are added and committed. Keep the source files from the shared Drive folder unchanged. `Emrys_Angry.png` and the second file with the same source name are kept under distinct keys. The `Robert_Serious` source file is saved with a `.png` extension.

## Optional supporting characters

An optional stage-directions file can show one supporting character beside the named speaker. The app looks for `assets/content/stage-directions.json` at startup. Set `ARG_STAGE_DIRECTIONS` to another local JSON file to use a different set of cues. Restart the app after changing the file.

Each cue identifies the story pack's chapter `id` and node `id`, then names the supporting character and an asset key from `assets/characters/`. The example below uses placeholder IDs and does not describe the current story.

```json
{
  "formatVersion": 1,
  "directions": [
    {
      "chapter": "chapter-source-id",
      "node": "story-node-id",
      "name": "Companion",
      "assetKey": "Companion_Neutral"
    }
  ]
}
```

Use at most 1,000 cues in a file no larger than 1 MiB. Each chapter and node pair must appear once. Chapter IDs, node IDs, and asset keys start with a letter or number and may also contain dots, underscores, and hyphens. Chapter and node IDs are limited to 100 characters; asset keys are limited to 80 characters. The supporting name must be nonempty, at most 80 characters, and contain no control characters. The loader rejects unknown or missing fields.

A supporting image appears only when the node has a named speaker with available character artwork, no CG illustration, and character artwork is enabled in Settings. If the cue file is absent, the app uses the existing single-speaker layout. If the file is invalid or an image is missing, dialogue remains available; an invalid file also produces a notice in Settings. Cues affect presentation only and are not stored in saves or used for scoring.

## Draft content gaps

The source snapshot and the mapping to playable node IDs are in [the source guide](content/source/README.md). The current chapter ID is `hailey-investigation-reading-draft`. The opening notice, checkpoint passages, and answer explanations identify the provisional answer keys.

The shared storyboard leaves the final player identity, culprit, and conclusion open. It also alternates between Prince Owen and Prince Ellis, and its Study hard question names a Revoked Knighthood Decree without describing that clue. The draft pack preserves the open ending and flags these content issues instead of choosing an answer for the group.

## Add the conclusion

Write approved conclusion scenes as ordinary nodes after both investigation outcomes. Keep every path finite and reachable. The terminal node uses `storyComplete: true`; its `dialogueText` appears on the section results screen. Replace the draft-boundary text with an approved completion message and add the killer-reveal image only when its outcome is confirmed.

Publish the concluded story with a new chapter ID if learner records already reference the draft. Keep existing node and question IDs within the old chapter unchanged so its saves remain readable. Reimport the new pack and verify both route orders before using it in a review.
