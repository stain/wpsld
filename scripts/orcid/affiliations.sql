-- ORCID affiliations of all kinds: one row per person and organisation.
-- Not yet run: check the field paths against the table's schema first (see
-- README.md). For an institution's own researchers, add to normalised's
-- WHERE:  AND ror IN ('https://ror.org/xxxxxxxxx')
WITH records AS (
  SELECT orcid_identifier.path AS orcid, activities
  FROM `ds-open-datasets.orcid.summaries_2026`
  WHERE history.deactivation_date IS NULL
),
affiliations AS (
  SELECT orcid, 'employment' AS kind,
         a.organization.name AS organization, a.organization.address.country AS country,
         a.organization.disambiguated_organization AS org_id,
         SAFE_CAST(a.start_date.year AS INT64) AS start_year,
         SAFE_CAST(a.end_date.year AS INT64) AS end_year, a.visibility AS visibility
  FROM records, UNNEST(activities.employments.groups) g, UNNEST(g.records) a
  UNION ALL
  SELECT orcid, 'education', a.organization.name, a.organization.address.country,
         a.organization.disambiguated_organization,
         SAFE_CAST(a.start_date.year AS INT64), SAFE_CAST(a.end_date.year AS INT64), a.visibility
  FROM records, UNNEST(activities.educations.groups) g, UNNEST(g.records) a
  UNION ALL
  SELECT orcid, 'invited-position', a.organization.name, a.organization.address.country,
         a.organization.disambiguated_organization,
         SAFE_CAST(a.start_date.year AS INT64), SAFE_CAST(a.end_date.year AS INT64), a.visibility
  FROM records, UNNEST(activities.invited_positions.groups) g, UNNEST(g.records) a
  UNION ALL
  SELECT orcid, 'membership', a.organization.name, a.organization.address.country,
         a.organization.disambiguated_organization,
         SAFE_CAST(a.start_date.year AS INT64), SAFE_CAST(a.end_date.year AS INT64), a.visibility
  FROM records, UNNEST(activities.memberships.groups) g, UNNEST(g.records) a
  UNION ALL
  SELECT orcid, 'service', a.organization.name, a.organization.address.country,
         a.organization.disambiguated_organization,
         SAFE_CAST(a.start_date.year AS INT64), SAFE_CAST(a.end_date.year AS INT64), a.visibility
  FROM records, UNNEST(activities.services.groups) g, UNNEST(g.records) a
),
normalised AS (
  SELECT orcid, kind, TRIM(organization) AS organization, country, start_year, end_year,
         -- ROR ids as https://ror.org/<id>, whichever form the record has
         IF(org_id.source = 'ROR',
            CONCAT('https://ror.org/', REGEXP_EXTRACT(LOWER(org_id.identifier), r'([0-9a-z]{9})/?$')),
            NULL) AS ror
  FROM affiliations
  WHERE visibility = 'public' AND NULLIF(TRIM(organization), '') IS NOT NULL
)
SELECT
  orcid,
  ror,
  ANY_VALUE(organization)                        AS organization,  -- as the person wrote it
  ANY_VALUE(country)                             AS country,
  STRING_AGG(DISTINCT kind, ';' ORDER BY kind)   AS kinds,         -- e.g. education;employment
  MIN(start_year)                                AS first_year,
  IF(LOGICAL_OR(end_year IS NULL), NULL, MAX(end_year)) AS last_year,
  LOGICAL_OR(end_year IS NULL)                   AS current
FROM normalised
GROUP BY orcid, ror,
         -- without a ROR id, the same name in the same country is the same place
         IF(ror IS NULL, CONCAT(LOWER(organization), '|', IFNULL(country, '')), NULL)
