# Story content and art

The JavaFX application loads `assets/content/hailey-investigation-complete.json` by default. It preserves the original draft chapter and adds a team-authored continuation under a new chapter ID. The protagonist remains a palace servant, and gameplay uses the signed-in account's registered display name for the character's name. The pack uses Prince Owen consistently, identifies Lord Robert, and has four suspect and antidote outcomes. This is a working team version, not an author or teacher-approved conclusion. The Study hard question uses described evidence instead of the absent revoked-knighthood clue. See [completed story decisions](../docs/requirements/Completed-Story-Decisions.md) for the answer reasoning and creative decisions.

## Run the story pack

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

The opening, welcome, login, and main menu use [Comfortable Mystery by Kevin MacLeod](https://incompetech.com/music/royalty-free/index.html?Search=Search&isrc=USUAN1100287), licensed under [Creative Commons Attribution 4.0](https://creativecommons.org/licenses/by/4.0/). The track was converted from MP3 to PCM WAV for JavaFX playback and is bundled at `src/main/resources/com/adaptivereadinggame/audio/comfortable-mystery.wav`. The WAV is tracked with Git LFS. The game uses an original generated click tone at `src/main/resources/com/adaptivereadinggame/audio/click.wav`.

The provisional story cues use [Investigation Piano by leberch](https://pixabay.com/music/crime-scene-investigation-piano-611639/) and [Tense Atmosphere Cinematic Loop by Sonican](https://pixabay.com/music/crime-scene-tense-atmosphere-cinematic-loop-piano-mystery-343445/). Both are subject to the [Pixabay Content License](https://pixabay.com/service/license-summary/) and are registered with Content ID. The local audio files are excluded from Git because Pixabay restricts standalone distribution. Each developer must obtain the tracks from their source pages and place PCM WAV copies at `assets/local-music/investigation.wav` and `assets/local-music/tense.wav`. Without them, the story remains playable without the missing track. If the tense file is missing, the investigation file is used when available.

Audio Settings is available on the welcome screen and main menu; the opening also provides a music mute control. Music and effects have separate volume sliders, and Java preferences save their values on this device. The opening and game use the same values. Reading checks remain quiet, and the game remains usable if audio playback is unavailable.

`Servant_A.png` and `Servant_B.png` are provisional generated player portraits in `assets/characters/`. Both represent the same player character, whose in-game name comes from the signed-in account's registered display name. An account can change its selected appearance from Character on the main menu; the selected appearance applies to all saves for that account. The [player artwork record](../docs/requirements/Player-Character-Art.md) contains the generation prompts and replacement guidance. The group should review the designs and character presentation before treating them as final.

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

## Content review boundary

The original storyboard leaves the player identity, culprit, and ending open. The completed chapter is a team-authored continuation that presents the protagonist under the registered display name in the servant role, identifies Lord Robert, and provides four outcomes. Review these choices and the six answer keys with the group and teacher before treating them as approved. The original draft remains available for existing learner records.

## Replacing the team-authored conclusion

Replace the team-authored conclusion with approved scenes as ordinary nodes after both investigation outcomes. Keep every path finite and reachable. The terminal node uses `storyComplete: true`; its `dialogueText` appears on the section results screen. Replace the current completion messages and retain the killer-reveal image only if the author confirms its use.

Publish revised story content with a new chapter ID if learner records already reference the current completed chapter. Keep the original draft and its IDs unchanged so existing saves remain readable. Reimport the pack and verify both route orders before using revised content in a review.
