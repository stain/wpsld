-- ORCID names: one row per active record with a public name.
-- For ORCID's public data in BigQuery (here its 2026 summaries); tried in
-- 2026-11: it processes about 1.36 GB, and its full result is over 1 GB of
-- CSV, too large to bundle. Add the institution filter (see README.md) for
-- an institution's own researchers only.
SELECT
  orcid_identifier.path                         AS orcid,        -- 0000-0002-1825-0097
  NULLIF(TRIM(person.name.given_names), '')     AS given_names,
  NULLIF(TRIM(person.name.family_name), '')     AS family_name,
  NULLIF(TRIM(person.name.credit_name), '')     AS credit_name   -- "published as"
FROM `ds-open-datasets.orcid.summaries_2026`
WHERE history.deactivation_date IS NULL
  AND person.name.visibility = 'public'
  AND COALESCE(NULLIF(TRIM(person.name.given_names), ''),
               NULLIF(TRIM(person.name.family_name), ''),
               NULLIF(TRIM(person.name.credit_name), '')) IS NOT NULL
