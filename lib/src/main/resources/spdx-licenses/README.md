# SPDX License List 3.29.0

`licenses.jsonld` is from the [SPDX License List](https://spdx.org/licenses/)
3.29.0, release [v3.29.0](https://github.com/spdx/license-list-data/releases/tag/v3.29.0) of
[spdx/license-list-data](https://github.com/spdx/license-list-data), file
`jsonld/licenses.jsonld` (SHA-256 `680e8f12e1ca5dd1dccfce8717582c8ce653670f17ec59966d075635fac4f9a8`).

Changed: only the 740 listed licences are kept, each with its `@id`,
`@type`, `spdx:name`, `spdx:isOsiApproved`, `spdx:isFsfLibre`,
`spdx:isDeprecatedLicenseId`, `spdx:deprecatedVersion` and `rdfs:seeAlso`,
sorted by `@id`. The licence texts and templates, the licence exceptions
and the link checks (`spdx:CrossRef`) are left out.

The SPDX License List is licensed under the
[Creative Commons Attribution 3.0 Unported](https://creativecommons.org/licenses/by/3.0/)
licence (CC-BY-3.0), as license-list-data's
[accessingLicenses.md](https://github.com/spdx/license-list-data/blob/main/accessingLicenses.md)
gives it. SPDX is a registered trademark of The Linux Foundation.

Made by `scripts/update-spdx-licenses.sh v3.29.0`.
