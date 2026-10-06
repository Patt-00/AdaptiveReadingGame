# Adaptive Reading Game - UI/UX reference

Start by opening **index.html** in your browser. It works offline without installation.

- **Clickable preview:** follow representative routes, or choose any state in the screen dropdown.
- **All screens:** review preserved layouts and proposed additions side by side.
- **Player flow:** see how authentication, story choices, reading checks, results, and exits connect.
- **Build guide:** implementation priorities and contracts your team must agree on.

## Files for your group

| File / folder | Purpose |
| --- | --- |
| `index.html` | Self-contained clickable design reference |
| `UIUX-Reference.pdf` | Shareable flow and labeled screens with short explanations |
| `screens/` | Numbered vector SVG layouts for manual import into Figma |
| `screens-png/` | Rendered screen images for documents and discussions |
| `player-flow.svg` / `player-flow.png` | Overview flowchart |
| `BUILD-GUIDE.md` | Detailed screen purposes, actions, and build order |
| `screen-manifest.json` | Screen inventory and original ZIP checksum |
| `verification.json` | Automated reference-navigation and layout checks |

There are 41 reference states, including a guide-only application-closed endpoint. The PDF omits that endpoint and has one overview page plus 40 screen pages.

## What this does not claim

This is **not the Java application**. Forms, settings, model inference, persistence, and scoring are design examples rather than implemented features. The clickable preview deliberately skips questions 2-4 and shares a sample story branch. The real game must use distinct reviewed content and the agreed Java services.

The original ZIP and the groupmate's application source have not been modified. This folder publishes the design reference on the `UI/UX` branch; it does not implement the proposed game features or merge them into `main`.

Download or clone this branch and open `docs/UI-UX/index.html` locally. GitHub's file view displays the HTML source, not the interactive preview.

## Importing into Figma

1. Open your Figma Design file.
2. Import SVG files from `screens/` in filename order. Screen `22b` is the final-feedback variant between `22` and `23`.
3. Check typography and make each imported view a 1280 x 720 frame.
4. Convert repeated controls into native Figma components, apply auto-layout, and connect prototype actions using `BUILD-GUIDE.md`.

SVGs contain vector shapes and source text, not flattened screenshot images. **SVG import does not create native Figma components, variables, auto-layout, or prototype connections.** Font/import support may cause Figma to outline or change text; verify after import.

The online Figma file was not populated because the connected Starter plan's tool-call limit blocked direct work.

## Font disclosure

The source uses **Times New Roman** titles and **Georgia** body/menu text. Georgia was not installed locally, so the browser preview, PNG renders, and PDF use **Times New Roman as the explicit fallback**. SVGs still specify Georgia first. No font files are included; use a licensed Georgia font or agree on a substitute before final visual approval.

## Rebuilding

`build-reference.mjs` generates the SVGs, HTML, manifest, and guide from the screen definitions. It references the source ZIP on this machine for provenance.

`verify-reference.mjs` is the local verification/rendering helper; its Playwright and Chromium paths are environment-specific. The delivered HTML, SVGs, PNGs, PDF, and guides do **not** need those helpers to open.

Use this package as the team's design/build reference. Before submitting a final UI/UX assignment, confirm the screens against the approved Concept Proposal, settle the save-slot contract, and replace all illustrative story/assessment copy.
