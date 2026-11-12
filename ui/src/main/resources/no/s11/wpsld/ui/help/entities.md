# Entities and identifiers

An entity is a description of something, such as a file, person or
dataset. Select one in the **Entities** list to edit it. The first entry
is the root dataset, describing the crate as a whole.

## Add and remove

Choose **Add entity**, then choose a type. The new entity is mentioned by
the root dataset until linked otherwise. Alternatively, use **New** beside
a property to create an entity and link it in one step; see
[Properties and links](properties.md).

Select one or more entities and choose **Remove** to remove their
descriptions. The root dataset cannot be removed. This does not delete
files from your computer. References to removed entities may remain in
other descriptions: check and remove or replace those references as needed.
**Edit > Undo** can restore an accidental removal.

**Edit > Repair Crate** can add placeholder Things for references to
entities not described in the crate, and update the root's mentions for
otherwise unlinked entities. It also tidies licences from the SPDX License
List, file formats from PRONOM and ORCID iDs, see [Properties and links](properties.md). It reports what it changed. Give any
placeholders appropriate types and names, or correct mistaken identifiers.
Use **Edit > Undo** if needed.

## Identifiers and merging

Each entity has an `@id`. For a file, use its path relative to the crate
folder; for a person, their ORCID iD (**Find in ORCID...** on their form,
see [Properties and links](properties.md)) or another appropriate
persistent identifier. A generated identifier beginning with `#` identifies
an entity within this crate.

To change an identifier, select the entity, edit `@id` and press Enter or
leave the field. Links to it are updated. If another entity already has
that identifier, wpsld asks whether to merge them. Choose **Merge** only
when both descriptions really refer to the same thing: their types and
values are combined and references point to the merged entity. **Cancel**
keeps them separate. Review the combined values; use **Edit > Undo** if
the merge was a mistake.
