# Types

A type says what an entity describes, such as a Person, Dataset or File.
It determines which properties are offered in the form. Select an entity
in the list to see its types beside `@type`.

Choose **Add type...** to browse the type tree or search names and
descriptions. Select a type and choose **OK**. You can also press F1 in
the type chooser for this page.

An entity can have several types. Adding a more specific type generally
replaces its more general type: for example, Person replaces Thing.
Unrelated types remain together; a photograph in the crate can have both
File and Photograph. Keep File for a file and Dataset for the root dataset.
When adding a more general type, wpsld may ask whether to add it anyway or
replace the more specific type.

Use the **×** beside a type to remove it. Removing the last type changes
the entity to Thing; a lone Thing cannot be removed. Changing types keeps
existing values, though some may move to **Not properties of...** or
**Not of type** if the new types do not offer those fields. Review the
values and any profile requirements after a change.

See [Properties and links](properties.md) for finding and filling fields.
