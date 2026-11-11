# HTML preview (`ro-crate-preview.html`)

Spec: https://www.researchobject.org/ro-crate/specification/1.3/structure#ro-crate-website-ro-crate-previewhtml-and-ro-crate-preview_files-for-packages

- Optional; if present it MUST be named `ro-crate-preview.html` in the crate root (extras in `ro-crate-preview_files/`), valid HTML5, showing at least the root's metadata without scripting. It is not payload: never in `hasPart`.
- `rocrate init --gen-preview` (ro-crate-py) writes one (checked with ro-crate-py 0.16.0).
- `npm install ro-crate-html` provides `rochtml <crate>/ro-crate-metadata.json`, which wrote `ro-crate-preview.html` next to the metadata (checked with ro-crate-html 0.1.6; status "beta" in the tools list). Other packages from the same npm package: `rocstatic`, `rocsite`.
- GitHub Pages: action `ro-crate-preview` (https://github.com/marketplace/actions/ro-crate-preview), `rocrate-to-pages`.
- Regenerate the preview after every metadata change; never edit it by hand if a tool made it. Check the tools page for current options: https://www.researchobject.org/ro-crate/tools
