# Entities and identifiers

An entity is a description of something, such as a file, person or
dataset. Select one in the **Entities** list to edit it. The first entry
is the root dataset, describing the crate as a whole.

## Add and remove

Choose **Add entity** (or **Insert > Entity...**), then choose a type.
The new entity is mentioned by the root dataset until linked otherwise.
Alternatively, use **New** beside a property to create an entity and link
it in one step; see [Properties and links](properties.md).

The **Insert** menu also adds entities looked up rather than typed:
**Person from ORCID...**, **Organization from ROR...**, **Publication by
DOI...**, **Licence from SPDX...**, **File format from PRONOM...** and
**Place from GeoNames...**. They open the same windows as the **New** menus
beside properties (see [Properties and links](properties.md)), but add the
entity without linking it from a property: it is mentioned by the root
dataset, and shown, so that you can link it later, e.g. by typing its
`@id` in a property. If the crate already has it, it is just shown.

Select one or more entities and choose **Remove**, below the list, in
the **Edit** menu or by right-clicking, or press **Del**, to remove their
descriptions from the crate. The root dataset cannot be removed. This does not delete
files from your computer. References to removed entities may remain in
other descriptions: check and remove or replace those references as needed.
**Edit > Undo** can restore an accidental removal.

**Edit > Repair Crate** can add placeholder Things for references to
entities not described in the crate, and update the root's mentions for
otherwise unlinked entities. It also tidies licences from the SPDX License
List, file formats from PRONOM, ORCID iDs, ROR identifiers and DOIs, see [Properties and links](properties.md). It reports what it changed. Give any
placeholders appropriate types and names, or correct mistaken identifiers.
Use **Edit > Undo** if needed.

## Identifiers and merging

Each entity has an `@id`. For a file, use its path relative to the crate
folder; for a person, their ORCID iD (**Find in ORCID...** on their form,
see [Properties and links](properties.md)) or another appropriate
persistent identifier. A generated identifier beginning with `#` identifies
an entity within this crate.

**↗** beside `@id` opens a web address in the browser, or the file or
folder beside the crate that a relative `@id` names; it is not available
for identifiers beginning with `#` or `_:`.

To change an identifier, select the entity, edit `@id` and press Enter or
leave the field. Links to it are updated. If another entity already has
that identifier, wpsld asks whether to merge them. Choose **Merge** only
when both descriptions really refer to the same thing: their types and
values are combined and references point to the merged entity. **Cancel**
keeps them separate. Review the combined values; use **Edit > Undo** if
the merge was a mistake.
