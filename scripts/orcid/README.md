# ORCID names and affiliations from BigQuery

Queries of ORCID's public data (its yearly public data file, CC0) in Google
BigQuery, for an institution that wants its own researchers available to
wpsld offline, e.g. inside a Trusted Research Environment. Not used by the
build, and wpsld does not ship their results.

- `names.sql`: one row per active ORCID record with a public name. Tried:
  it processes about 1.36 GB, and the full result is over 1 GB of CSV.
- `affiliations.sql`: one row per person and organisation, of all
  affiliation kinds, with ROR ids as `https://ror.org/<id>`. Not yet run.

The full results are too large to bundle with wpsld, and are personal data
about millions of people. Run them for one institution instead, with its
ROR ids: in `affiliations.sql`, add `AND ror IN ('https://ror.org/…')` to
the `normalised` CTE's `WHERE`; for names, keep the people with such an
affiliation (`AND orcid_identifier.path IN (SELECT orcid FROM normalised
WHERE ror IN (…))`, with the CTEs of `affiliations.sql` in front).

Before relying on them: check the field paths against the table's schema
(section names, whether names and dates are `{value: …}` structs, the
affiliations' visibility field), run with `LIMIT 20`, and look at the
bytes the query will process.

Export to a Cloud Storage bucket in the dataset's location; a result over
1 GB needs a `*` in the file name, so BigQuery splits it:

    EXPORT DATA OPTIONS (
      uri = 'gs://YOUR-BUCKET/orcid-2026/names-*.csv.gz',
      format = 'CSV', compression = 'GZIP', header = true, overwrite = true
    ) AS SELECT ...

Each part has a header line; to join them:

    zcat names-*.csv.gz | awk 'NR == 1 || !/^orcid,/' | gzip > names.csv.gz
