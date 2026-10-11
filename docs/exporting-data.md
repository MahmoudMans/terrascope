# Exporting data

Lists you can act on have **CSV** and **JSON** buttons next to them:

- the hazard event stream on the dashboard (whichever feed is showing),
- the incident queue in the Situation Room (exactly the cases currently listed, so filters and search apply),
- an incident's response timeline,
- saved events,
- the activity log (Operators and Administrators), which follows the Person filter and collects up to 5,000 of the newest records across pages.

Press **Ctrl+K** and choose **Download current feed as CSV** or **JSON** to do the same for the event stream from the keyboard.

CSV files are UTF-8 with a byte order mark so Excel opens them correctly, use CRLF line endings, and quote any field that contains a comma, a quote or a line break. Text that starts with `=`, `+`, `-` or `@` gets a leading apostrophe so a spreadsheet cannot run it as a formula. JSON files wrap the records in `{ dataset, exportedAt, count, context, items }`, where `context` records the filters or source the data came from. File names look like `terrascope-incidents-20261011-084500Z.csv` (UTC).

Exports are built in the browser from data you can already see, so they respect your role and add no new server endpoints. The existing **Situation report** keeps its Print button; printing always uses the light theme.
