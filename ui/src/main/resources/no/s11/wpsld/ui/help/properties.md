# Properties and links

Properties describe an entity: for example, Name is text and Author can
link a dataset to a person. Select an entity to see fields for its
[types](types.md). Required or suggested fields from the chosen profile
appear first. The **?** or information button beside a field shows its
definition.

In the list-style form, type a property name or label in **Search
properties** to find it; clear the search to see the other groups again.
In the OS/2 Warp notebook, browse the type tabs and their pages instead.
Enter a value in the field. Use **+** to add another value and **×** or
**−** to remove a value, where available.

## Link to an entity

For a reference field such as Author, open its drop-down and choose a
matching entity, or type its `@id`. The suggestions can be filtered by
name or identifier. You can also copy one entity from the list and paste
it into a reference field: its identifier is inserted as a link.

To create and link a new description, choose **New** beside the property
and select a type from its menu. Select the new entity in the list to fill
in its name and other details, then return to the original entity to
review the link. Creating a File description does not create the file.
If you remove a link, the linked description is not itself deleted.

Some properties also accept plain text, such as licence terms. Check that
the value is appropriate and that references point to the intended
entities.

## Licences

RO-Crate asks for a **License** on the crate itself (the root dataset), and
on any file whose licence differs from the crate's. wpsld includes the
[SPDX License List](https://spdx.org/licenses/), which names the common
licences and gives each a short identifier, such as `CC-BY-4.0` or
`Apache-2.0`.

To add one, choose **New** beside **License**, then **Licence from
SPDX...**. Type part of the licence's identifier or name, for example
`cc-by-4.0`, `apache` or `creative commons attribution 4.0`: the list
narrows while you type, with whether a licence is OSI approved or FSF free.
The licence selected is described beside the list, with links to its
page on the SPDX License List and to the licence itself. Choose one and
**OK**.

![Choosing a licence from the SPDX License List: the Creative Commons Attribution 4.0 licences, with CC-BY-4.0 selected](images/licence-finder.png)

wpsld then adds the licence as RO-Crate recommends: a CreativeWork whose
`@id` is its SPDX address, such as `http://spdx.org/licenses/CC-BY-4.0`,
named as SPDX names it, with its SPDX identifier (`CC-BY-4.0`) as a
PropertyValue in its **Identifier**. If the crate already has that licence,
for example to give a file the same licence as the crate, it is linked
rather than added again.

SPDX has replaced some identifiers, for example `GPL-2.0` by `GPL-2.0-only`
and `GPL-2.0-or-later`. Those are hidden unless you tick **Show deprecated
identifiers**, e.g. to match what a crate already says.

Crates made elsewhere may give a licence in other ways, for example as
the text `CC-BY-4.0`, with an address copied from the browser such as
`https://spdx.org/licenses/Apache-2.0.html`, or with the licence's own web
page, such as `https://creativecommons.org/licenses/by/4.0/`. **Edit >
Repair Crate** turns these into links to the SPDX licence, merges licences
that turn out to be the same, and adds the SPDX name and identifier where
they are missing, keeping any name the licence already has; a licence's
own web page is kept as its **URL**. Some pages can't tell which licence
is meant, such as GNU's GPL page, which serves both "GPL-3.0-only" and
"GPL-3.0-or-later": those are left for you to choose.

For a licence that isn't on the SPDX list, choose **New > CreativeWork**
instead, set its `@id` to the licence's own web address, and give it a
**Name** and a **Description** summarising it.

## File formats

RO-Crate asks files for their **Encoding format**: the media type, such
as `text/csv` or `application/pdf`, and, where it can, a link to the
format's description in [PRONOM](https://pronom.nationalarchives.gov.uk/),
the UK National Archives' registry of file formats. wpsld includes PRONOM's
list of formats, so this works offline.

On a file, choose **New** beside **Encoding format**, then **Format from
PRONOM...**. The search starts with the file's media type, or the
extension of its name (for example `.csv`); you can also type a name, a
version or a PRONOM identifier (PUID) such as `fmt/19`. Choose the format
and **OK**: wpsld links the format, described as a WebPage and Standard
with its PRONOM address as `@id`, and adds its media type if the file has
none.

![Choosing a file format from PRONOM: the versions of PDF, with PDF 1.0 selected](images/format-finder.png)

Many formats have several versions, and PRONOM lists each: PDF 1.4, 1.5,
1.7 and PDF/A all have the media type `application/pdf` and the extension
`.pdf`. Choose the version the file has, if you know it, or leave just the
media type.

**Edit > Repair Crate** links the PRONOM format where a file's media type
belongs to only one format, such as `text/csv` or `text/markdown`, but not
`application/pdf`. It also turns PRONOM addresses copied from its web site,
such as `https://pronom.nationalarchives.gov.uk/fmt/19`, into the form
RO-Crate uses, `https://www.nationalarchives.gov.uk/PRONOM/fmt/19`.

## Places

To say where something is, or which area it covers, link it to a
**Place** with **Content location** (where it was made or what it shows)
or **Spatial coverage** (the area it is about).

The easiest way is **New** beside the property, then **Place from
GeoNames...**. Type the name of the place and choose **Search**; the
matches are listed with their region, country and kind (park, city,
mountain...), and the one selected is shown on a map. Choose one and
**OK**: wpsld adds the place with its GeoNames identifier, which RO-Crate
recommends, its name and web page, and where GeoNames has it. If the crate
already has that place, it is linked instead of added again.

![Find a place in GeoNames: Catalina Park in New South Wales, Australia, listed and marked on a map](images/place-finder.png)

Looking places up needs a free GeoNames account: make one at
[geonames.org](https://www.geonames.org/login), enable its free web
services on your account page, and enter your username in **Edit >
Preferences > Places**.

A place's form shows it on a map under **Location**. Drag the map to move
it, and scroll or use **+** and **−** to zoom. Click the map to put the
place where you clicked, for example a more exact spot than GeoNames has,
or a place GeoNames doesn't know. **Find place...** looks up a place you
have already added, by its name. The map is from OpenStreetMap, so it
needs an internet connection.

![The place's form: its GeoNames @id and name, and under Location its coordinates, Find place... and the map with its marker](images/place-location.png)

Behind the scenes, the location is a **Geometry** linked from the place's
**Geo**, with the point as Well-Known Text in **As WKT**, for example
`POINT (150.301195 -33.7152)` (longitude first). You can edit that
directly, e.g. for a line or an area. Places in older crates with
GeoCoordinates, Latitude and Longitude are shown and moved too.
