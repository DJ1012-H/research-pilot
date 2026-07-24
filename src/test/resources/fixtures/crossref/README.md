# Crossref fixtures

## `work-by-doi-success.json`

- Endpoint: `GET /works/10.1038%2Fs41586-021-03819-2`
- Source URL: `https://api.crossref.org/works/10.1038%2Fs41586-021-03819-2`
- Captured date: 2026-07-22T13:14:47.3605737Z
- Integrated/reused date: 2026-07-24
- Purpose: DTO deserialization, mapper replay, and offline regression tests.
- Sensitive request parameters removed: no `mailto`, token, or request headers are stored.

This is the unchanged, manually reviewed Crossref response already recorded in the
verification-dataset branch. The reviewed date is intentionally omitted because it was not
recorded separately. No additional bibliographic-search fixture is added: the reviewed snapshot
does not contain that response shape, and empty/multiple candidate handling is covered with
offline client mocks rather than an invented or unreviewed response.
