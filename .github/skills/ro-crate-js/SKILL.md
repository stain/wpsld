---
name: ro-crate-js
description: "Use the ro-crate JavaScript library (npm `ro-crate`) and its roccheck CLI to read, create and modify RO-Crates in Node.js or the browser. Use when the user works in JavaScript/TypeScript or asks for a Node script that builds or edits ro-crate-metadata.json, instead of writing JSON-LD handling from scratch."
---

# ro-crate-js (`npm install ro-crate`)

Source https://github.com/Language-Research-Technology/ro-crate-js, docs https://language-research-technology.github.io/ro-crate-js/ (3.7.2 tested; beta). Node 20+.

```js
const {ROCrate} = require('ro-crate');           // existing crate: new ROCrate(JSON.parse(fs.readFileSync('ro-crate-metadata.json','utf8')), {array: true, link: true})
const crate = new ROCrate();
const root = crate.rootDataset;
root.name = 'Test crate'; root.description = '...'; root.datePublished = '2026-10-16';
root.license = {'@id': 'https://creativecommons.org/licenses/by/4.0/'};
crate.addEntity({'@id': 'https://creativecommons.org/licenses/by/4.0/', '@type': 'CreativeWork', name: 'CC BY 4.0'});
crate.addEntity({'@id': 'data.csv', '@type': 'File', name: 'Data', encodingFormat: 'text/csv'});
root.hasPart = [{'@id': 'data.csv'}];
fs.writeFileSync('ro-crate-metadata.json', JSON.stringify(crate.toJSON(), null, 2));
```
The first constructor argument is **existing metadata**, not options: passing `{name: ...}` there does nothing (tested).

## Do not guess
Ask the user for facts about them and their project: authors, publisher, organisation, affiliation, ORCID/ROR/DOI, licence, funders, dates of events, and software or tool versions. Do not infer them from the user's email address, username, git config or machine, and never invent identifiers. You may derive what you can observe: file sizes and media types from the files, `tool --version` output, titles and descriptions from the content (say that you did).
If you cannot ask: leave optional properties out. For what the root data entity must have (`license`, `name`, `description`, `datePublished`) and for any other property the specification, a profile or the validator requires (for example `version` on a `SoftwareApplication` or `ComputerLanguage` you could not run), use an obviously temporary value that starts with `TODO:` (for example `"license": "TODO: choose a licence"`; today's date is fine for the `datePublished` of a draft), and list every `TODO:` value in your answer. The lint script (`ro-crate-validator`) warns about `TODO:` values. Never publish, deposit or hand over a crate that still contains one.

## Checked behaviour and cautions
- A new `ROCrate()` writes the RO-Crate **1.2** context plus `{"@vocab": "http://schema.org/"}` and a 1.2 descriptor `conformsTo`; it also adds `identifier` to the descriptor. To target 1.3 set `@context` and the descriptor's `conformsTo` yourself (the 1.2 to 1.3 step in `ro-crate-compatibility`), and check the result against `ro-crate-create`. `@vocab` is not part of the RO-Crate 1.3 context.
- It does not set `name`, `description`, `datePublished` or `license`: set them (the root must have all four).
- Modifying an array property in place is not supported: assign a new array.
- `roccheck <dir>` is a simple check, "not a full validation", and looks for the old `ro-crate-metadata.jsonld` filename (it failed with ENOENT on a `.json` crate in one run). The package's `rocval` command crashed in this install (`program.version is not a function`); use `rocrate-validator` (`ro-crate-validator`).
- Related packages from the same group: `ro-crate-html` (`rochtml`, preview), `ro-crate-excel` (`ro-crate-excel` skill).
