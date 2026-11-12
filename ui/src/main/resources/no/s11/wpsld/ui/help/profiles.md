# Profiles

A profile describes expectations for a particular kind of RO-Crate. In wpsld
it changes which fields are shown as required or suggested in the editor.
The core RO-Crate profile is available by default; **schema.org (no
profile)** offers the vocabulary without profile-specific guidance.

## Load and choose a profile

1. Choose **Load profile...** in the toolbar or **File > Load Profile...**.
   The dialog loads RO-Crate Machine Actionable Profiles (MASP), not
   arbitrary profile documents.
2. Select an entry from the registry list, or enter a profile identifier,
   web page, local folder or path to its `ro-crate-metadata.json`. Use
   **File...** to choose a metadata file. Choosing an entry fills in its
   location; double-clicking it also starts loading.
3. Choose **Load**. The profile is loaded in the background, added to the
   toolbar's **Profile** chooser and selected. Read any error or warning
   shown by wpsld. A successful load is kept for future sessions.

The list comes from enabled online registries and may take time or be
unavailable offline. A local profile file can still be loaded without a
registry. Press F1 (Command-? on macOS) in the load dialog for this help.

Choose a different profile in the toolbar to change the guidance. For a
loaded profile with an identifier, selecting it also records that choice
on the dataset as a conformance claim; inspect that claim before sharing.
When opening a crate, wpsld tries to select or load a profile it declares.
Selecting a profile does not itself guarantee that the crate meets all of
its requirements; check the profile's documentation and validate the crate.

## Profile preferences

Choose **Edit > Preferences...**. In **Profile registries**, tick the
registries to use for listing and looking up profiles, then choose
**OK** to save those choices. Disabled registries are not queried.

In **Kept profiles**, select a previously loaded profile and choose
**Remove** to stop offering it and remove its kept copy. Removal happens
immediately, even if you later choose **Cancel**. If it was selected, wpsld
selects the default profile. This does not delete the original profile
file. The bundled core profile is not listed for removal.

Preferences has a **Help with profiles** link; F1 also opens this topic
without closing the dialog.
