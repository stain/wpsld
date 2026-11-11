# Workflow Run Crate profiles: essentials (version 0.6)

Source: https://www.researchobject.org/workflow-run-crate/profiles/ (Process Run Crate, Workflow Run Crate, Provenance Run Crate pages: each has a requirements table and a conforming example). Re-check the pages for newer versions.

## Process Run Crate
```json
{"@context": ["https://w3id.org/ro/crate/1.3/context", "https://w3id.org/ro/terms/workflow-run/context"], "@graph": [
 {"@id": "ro-crate-metadata.json", "@type": "CreativeWork", "about": {"@id": "./"},
  "conformsTo": {"@id": "https://w3id.org/ro/crate/1.3"}},
 {"@id": "./", "@type": "Dataset", "name": "Pictures", "description": "...", "datePublished": "2025-01-31",
  "license": {"@id": "https://creativecommons.org/licenses/by/4.0/"},
  "conformsTo": {"@id": "https://w3id.org/ro/wfrun/process/0.6"},
  "hasPart": [{"@id": "in.jpg"}, {"@id": "out.jpg"}], "mentions": {"@id": "#run1"}},
 {"@id": "https://w3id.org/ro/wfrun/process/0.6", "@type": ["CreativeWork", "Profile"], "name": "Process Run Crate", "version": "0.6"},
 {"@id": "https://www.imagemagick.org/", "@type": "SoftwareApplication", "name": "ImageMagick",
  "url": "https://www.imagemagick.org/", "version": "6.9.7-4"},
 {"@id": "#run1", "@type": "CreateAction", "name": "Convert to sepia",
  "description": "convert -sepia-tone 80% in.jpg out.jpg", "endTime": "2025-01-31T17:01:07+10:00",
  "instrument": {"@id": "https://www.imagemagick.org/"}, "object": {"@id": "in.jpg"}, "result": {"@id": "out.jpg"},
  "agent": {"@id": "https://orcid.org/0000-0002-1825-0097"}}
]}
```
(`in.jpg`, `out.jpg` `File` entities and the agent `Person` omitted.) Two points differ from the profile page's own example, which `rocrate-validator -p ro-crate-1.3` (0.12.2) rejects: the profile entity needs `Profile` in `@type` (RO-Crate 1.2+ requires it for every profile in the root `conformsTo`), and the software entity needs `version` (the profile accepts `version` or the more specific `softwareVersion`, not both; the validator wants `version`). Requirements in short:
- Root `conformsTo` references a `CreativeWork` entity for the versioned profile permalink.
- Tool: `SoftwareApplication`/`SoftwareSourceCode`/`ComputationalWorkflow`; `@id` absolute URI (or relative to a data entity, or `#local`); SHOULD have `name`, `url`, version (`softwareVersion` preferred for applications, not both).
- Execution: `CreateAction` (or `ActivateAction` with no result, `UpdateAction` for in-place changes); unique `@id` (random UUID recommended), listed in the root's `mentions`; SHOULD have `name`, `description` (informational command line), `endTime`; MAY have `startTime`; `instrument` MUST name the tool; `object`/`result` the input/output data entities; `agent` the person or organisation.
- Prospective (the tool/plan) vs retrospective (the action) provenance are distinct entities.

## Workflow Run Crate (adds)
Root `conformsTo` array: the Workflow Run Crate, Process Run Crate and Workflow RO-Crate permalinks. The workflow is a `ComputationalWorkflow` data entity (also `mainEntity`), the execution a `CreateAction` whose `instrument` is the workflow; input/output values are `PropertyValue` or data entities with `exampleOfWork` → the `FormalParameter` they realise.

## Provenance Run Crate (adds)
Workflow `hasPart` lists tools/subworkflows; steps as `HowToStep` via `step` (workflow also typed `HowTo`); each step execution a `ControlAction` (`instrument` the `HowToStep`, `object` the tool-execution `CreateAction`s, `actionStatus` Completed/Failed); one `OrganizeAction` for the engine run (`instrument` the workflow engine, `object` the `ControlAction`s, `result` the workflow-execution `CreateAction`).
