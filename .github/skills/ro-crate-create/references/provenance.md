# Provenance patterns (RO-Crate 1.2/1.3)

Spec: https://www.researchobject.org/ro-crate/specification/1.3/provenance and `/workflows`. For full run provenance use the Workflow Run Crate profiles (see ro-crate-workflows).

- **Software used:** `SoftwareApplication` (`name`, `url`, `version` such as `tool --version` output) and a `CreateAction`: `instrument` the software, `object` inputs, `result` outputs, `agent` a Person, `endTime` ISO 8601 (SHOULD), `startTime` (MAY), `name`, `description` (the command line; double-escape backslashes). Several tools in sequence: instrument a `SoftwareSourceCode` script/workflow instead.
- **Equipment:** `IndividualProduct` (serial number, manufacturer, a web page as `@id`); a `CreateAction` with `instrument` it.
- **Changes to the crate or its files:** a curation `CreateAction`/`UpdateAction` with at least one `object` (the root or a part), `name`, `endTime`, a human `agent`, optional `instrument`, `actionStatus` (`CompletedActionStatus`, `FailedActionStatus`, ...), `error`. `UpdateAction` only for whole-dataset state changes; for a changed file keep the old version and add a `CreateAction` with `object` old, `result` new.
- **Repository exports (PCDM):** `RepositoryObject`/`RepositoryCollection` with `hasMember`, `hasFile`.
- List actions from the root with `mentions` so they are reachable.
- **Recording an AI agent's own work** (when the user wants the crate to say that an assistant made or changed it): a `CreateAction` or `UpdateAction` whose `agent` is the **responsible person** (given by the user), `instrument` a `SoftwareApplication` for the assistant, `object` what it read, `result` what it wrote, `endTime`, a short `description` of the task, and `actionStatus`. Name the assistant and its version only as you know them (for example the model name from your own system prompt); if you do not know a version, omit it (see Do not guess). Never make the assistant the `author` of a dataset on its own.
```json
{"@id": "#assistant-edit-1", "@type": "UpdateAction", "name": "Metadata drafted with an AI assistant",
 "agent": {"@id": "#alice"}, "instrument": {"@id": "#assistant"}, "object": {"@id": "./"},
 "endTime": "2026-10-16T14:00:00Z", "actionStatus": {"@id": "http://schema.org/CompletedActionStatus"}},
{"@id": "#assistant", "@type": "SoftwareApplication", "name": "<assistant name as known>"}
```
(`#alice` is a placeholder id for the responsible person; use the real entity.) Link the action from the root with `mentions`.
