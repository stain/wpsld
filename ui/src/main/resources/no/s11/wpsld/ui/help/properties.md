# Properties and links

Properties describe an entity: for example, Name is text and Author can
link a dataset to a person. Select an entity to see fields for its
[types](types.md). Required or suggested fields from the chosen profile
appear first. The **?** or information button beside a field shows its
definition.

In the list-style form, type a property name or label in **Search
properties** to find it; clear the search to see the other groups again.
A group of a type's properties, such as **From CreativeWork**, opens when
the entity uses any of them, showing only those; **Show all** below them
lists the rest.
In the OS/2 Warp notebook, browse the type tabs and their pages instead.
Enter a value in the field. Use **+** to add another value and **×** or
**−** to remove a value, where available.

## Many values

A property with many values, such as the parts of a large crate, shows
its first 20 in the form, and **Show 100 more of N values** shows more;
those not shown are kept as they are when you edit the others. The
preview shows the first 100 of a property's values, and **Show all N** the
rest. The crate's own **Has part** and **Mentions**, which link the
crate's entities to it, are at the top of its form.

## Text in a language

Some crates write text in a language, as JSON-LD's
`{"@value": "Hei", "@language": "no"}`, or a value of a datatype, such as
`{"@value": "2026", "@type": "xsd:gYear"}`. The form shows the text, with
the language (here **no**) or datatype beside it, and keeps it when you
edit the text; the preview shows "Hei (no)". Values you don't edit are
saved as they were.

## Link to an entity

For a reference field such as Author, open its drop-down and choose a
matching entity, or type its `@id`. The suggestions can be filtered by
name or identifier. You can also copy one entity from the list and paste
it into a reference field: its identifier is inserted as a link. Choose
**→** beside a link to show the entity it links to.

Where a property takes one of a fixed list of schema.org values, an
*Enumeration*, its drop-down offers them too, as if they were entities of
the crate: for example Action status (`actionStatus`) offers
ActiveActionStatus, CompletedActionStatus, FailedActionStatus and
PotentialActionStatus, saved as links to them
(`{"@id": "http://schema.org/CompletedActionStatus"}`). Another value can
still be typed, as a list may be extended. **Crate > Repair Crate** turns
such values written as text into these links.

To create and link a new description, choose **New** beside the property
and select a type from its menu. Select the new entity in the list to fill
in its name and other details, then return to the original entity to
review the link. Creating a File description does not create the file.
If you remove a link, the linked description is not itself deleted.

Some properties also accept plain text, such as licence terms. Check that
the value is appropriate and that references point to the intended
entities.

## Look entities up

Some entities can be looked up in registries, rather than typed in, each
described on its own page:

- [People](people.md), by their ORCID iD.
- [Organisations](organisations.md), in the Research Organization
  Registry (ROR).
- [Publications and DOIs](publications.md), by their DOI.
- [Licences](licences.md), from the SPDX License List.
- [File formats](formats.md), from PRONOM, the UK National Archives'
  registry.
- [Places](places.md), in GeoNames.
