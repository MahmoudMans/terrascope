# Activity log

The activity log answers "who changed what, and when". Operators and Administrators open it with the clipboard button in the top bar.

- It records every successful change made by an Operator or Administrator, plus sign-ins and password changes.
- It does not record reading data, failed requests, or Viewer-level actions such as running a simulation.
- Each entry shows when it happened, who did it and their role, a plain-language description such as "Created incident", and what it touched.
- You can filter by person, refresh, and load older entries. Entries are grouped by day.
- With sign-in off, entries are attributed to "Local session".
- Entries older than `TERRASCOPE_ACTIVITY_RETENTION_DAYS` (180 by default) are deleted every night at 03:15 server time. In Docker that is UTC. Set it to `0` to keep everything.

The same data is available from the API to Operators and Administrators:

```
GET /api/v1/activity?limit=50&before=2026-01-01T00:00:00Z&actor=alice
```

`limit` is 1 to 200 (default 50). `before` is an ISO-8601 time and returns older entries only. `actor` is a username. The response has `entries`, newest first, and `nextBefore`, which you pass as `before` to get the next page. When `nextBefore` is null there are no more entries.
