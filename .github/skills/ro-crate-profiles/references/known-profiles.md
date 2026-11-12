# Known RO-Crate profiles and how to choose

Source: https://www.researchobject.org/ro-crate/profiles and the registry https://profiles.ro-crate.org/ (checked 2026-10-16; versions change, open the profile page for the current permalink). `rocrate-validator` support is from roc-validator 0.12.2 (`rocrate-validator profiles list`).

| Use case | Profile | Notes |
|---|---|---|
| Package a workflow for sharing (WorkflowHub) | Workflow RO-Crate, https://w3id.org/workflowhub/workflow-ro-crate/ (1.0; the run-crate profiles reference 1.1) | needs a `ComputationalWorkflow` that is also `mainEntity`, `programmingLanguage`; ZIP named `*.crate.zip`; validator: `workflow-ro-crate-1.0` |
| Ship test suites for a workflow | Workflow Testing RO-Crate, https://lifemonitor.eu/workflow_testing_ro_crate | extends Workflow RO-Crate with `TestSuite`/`TestInstance`/`TestDefinition` (ro-terms `test` namespace, `mentions`); validator: `workflow-testing-ro-crate-0.1` |
| Record that tools were run | Process Run Crate, https://w3id.org/ro/wfrun/process/0.6 | validator knows 0.5 |
| Record a workflow execution | Workflow Run Crate, https://w3id.org/ro/wfrun/workflow/0.6 | declare also Process Run Crate and Workflow RO-Crate; validator knows 0.5 |
| Record step-level detail | Provenance Run Crate, https://w3id.org/ro/wfrun/provenance/0.6 | adds `HowToStep`, `ControlAction`, `OrganizeAction`; validator knows 0.5 |
| Distributed provenance chains (W3C PROV) | Common Provenance Model RO-Crate, https://w3id.org/cpm/ro-crate | identifies CPM provenance files |
| Plant-science experiments with ISA metadata | ISA RO-Crate (ARC collection: Administrative, Datamap, Process Core, ISA, ARC Workflow Run), https://nfdi4plants.github.io/arc-ro-crate-profile/ | validator: `isa-ro-crate` |
| Electronic lab notebooks | ELN file format (`.eln` zip with an RO-Crate), https://github.com/TheELNConsortium/TheELNFileFormat | zip with one top-level folder; ELN validator exists (beta) |
| Earth observation data cubes | RELIANCE RO-Crate, https://reliance-eosc.github.io/reliance-ro-crate/ | used by ROHub |
| Excel spreadsheets described in a crate | RO-Crates-and-Excel, https://github.com/e11938258/RO-Crates-and-Excel/ | Describo profile; not updated to JSON-LD terms |
| Editor forms (Describo) | Describo profiles, https://github.com/describo/profiles | UI profiles, not conformance claims |
| Bioimage archive submissions | BIA RO-Crate (bia-ro-crate-validator on PyPI) | listed on the tools page |

## Choosing
1. Search the registry for the domain; reuse before inventing.
2. Declare each profile on the root with its released versioned permalink (see the SKILL.md); several profiles are normal (e.g. Workflow Run Crate + Process Run Crate + Workflow RO-Crate).
3. Read the profile page for MUST/SHOULD rules and any serialisation requirement (zip name, context to add), then validate with the matching `rocrate-validator -p <id>` profile if available, noting that its profile version can lag the profile page.
4. If nothing fits, write down conventions in a short profile description first; formalise as a Profile Crate only when other crates will use it.
