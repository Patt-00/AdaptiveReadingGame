# Adding the approved story and character art

The application reads story text and comprehension questions from a local JSON content pack. No story pack or character artwork is included in this checkout. Add only the group's approved content when it is available.

## Load a content pack

Set `ARG_CONTENT_PACK` to the JSON file before launching the application. The pack is validated before its chapters are imported.

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

With in-memory storage, imported content is available only until the application closes. With MySQL storage, run the migrations in [the database guide](../database/README.md) first. Reimporting identical content is safe. Keep IDs stable after content is used; use a new chapter ID for a revised chapter.

## Pack format version 1

The top-level object has `formatVersion` and `chapters`. `formatVersion` must be `1`. Each chapter has these fields:

- `id`: stable letters, numbers, dots, underscores, or hyphens.
- `title`: chapter title.
- `difficulty`: `EASY`, `MEDIUM`, or `HARD`.
- `passage`: the text shown with the reading check.
- `startNodeId`: the first story node.
- `nodes`: the dialogue, thought, choice, and reading-check nodes.
- `questions`: the comprehension questions for this chapter.

Every node has `id`, `speakerName`, `dialogueText`, `thought`, `readingCheck`, `nextNodeId`, and `choices`. `speakerAssetKey` is optional. Use an empty `nextNodeId` when the node uses choices or is the reading-check node. A choice has `id`, `text`, and `nextNodeId`. A question has `id`, `prompt`, `choices`, and `correctChoiceIndex`; `correctChoiceIndex` is zero-based, and `explanation` is optional.

The loader requires each chapter to have exactly one reading-check node. Every node must be reachable from the start, the story cannot loop, and every branch must end at that reading check. Each chapter has 1 to 100 questions. The loader rejects unknown fields, invalid answer keys, missing destinations, duplicate IDs, and packs larger than 5 MiB.

Chapter and question IDs in the database are stable UUIDs derived from the pack IDs. Keep a question ID unchanged when its answer text is edited without changing its identity. A content edit that changes its meaning should receive a new ID.

## Add character artwork

Set `speakerAssetKey` to a simple key such as `guide`, without a folder or extension. Place the matching image in `assets/characters/` using a `.png`, `.jpg`, `.jpeg`, or `.gif` extension. For example, `guide` matches `assets/characters/guide.png`. The image lookup is optional; a missing image does not invalidate the story pack.

The default image root is the project's `assets/` directory. Set `ARG_ASSET_DIR` only when the character images are stored under a different trusted local directory.

The content pack stores the asset key, not an absolute file path. This keeps file locations inside the project and prevents a pack from referencing arbitrary files on the computer.
