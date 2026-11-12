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
on any file whose licence differs from the crate's. The licence should be
a description of its own, a CreativeWork, whose `@id` is the licence's web
address. For the common licences, use their address in the
[SPDX License List](https://spdx.org/licenses/), which gives each licence
a short identifier, for example:

- `http://spdx.org/licenses/CC-BY-4.0` for Creative Commons Attribution 4.0
- `http://spdx.org/licenses/CC0-1.0` for the CC0 public domain dedication
- `http://spdx.org/licenses/Apache-2.0` for the Apache License 2.0
- `http://spdx.org/licenses/MIT` for the MIT License

Look up other identifiers on the SPDX License List's page, and put the
identifier after `http://spdx.org/licenses/`.

wpsld does not look licences up for you yet, so add one like this:

1. On the crate (or file), find **License** and choose **New >
   CreativeWork**.
2. Select the new CreativeWork in the entity list, and enter the licence's
   full name as its **Name**, for example `Creative Commons Attribution
   4.0 International`.
3. Set its `@id` to the SPDX address, for example
   `http://spdx.org/licenses/CC-BY-4.0`, and press Enter. The crate's
   link to it follows the new `@id`.

To give another file the same licence, don't make a second description:
open the file's **License** drop-down and choose the licence the crate
already has. For a licence that isn't on the SPDX list, use its own web
address as the `@id`, and add a **Description** summarising it.

[Getting started](getting-started.md) walks through adding a licence, with
screenshots.

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
