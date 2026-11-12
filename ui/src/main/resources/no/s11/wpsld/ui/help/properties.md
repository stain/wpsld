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

## Places

To say where something is, or which area it covers, link it to a
**Place** with **Content location** (where it was made or what it shows)
or **Spatial coverage** (the area it is about). Give the place a name. If
it has a [GeoNames](https://www.geonames.org/) page, use its identifier as
the place's `@id`, for example `http://sws.geonames.org/8152662/`.

For the exact point or shape, choose **New** beside the place's **Geo**
and select **Geometry**, as RO-Crate recommends. In the geometry's
**As WKT**, write the point, line or shape as Well-Known Text, longitude
before latitude, for example:

    <http://www.opengis.net/def/crs/OGC/1.3/CRS84> POINT (150.301195 -33.7152)

`LINESTRING (...)` and `POLYGON ((...))` describe lines and areas. Older
crates may instead use GeoCoordinates with Latitude and Longitude; wpsld
reads those too.

The preview of a place, geometry or coordinates has a link to show it on
OpenStreetMap, which opens in your browser.
