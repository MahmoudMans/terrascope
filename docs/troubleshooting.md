# Troubleshooting

**"Docker is installed but not running."** Start Docker Desktop and run the script again.

**A port is already in use.** Set `WEB_PORT` or `BACKEND_PORT` in `.env` and run `bash scripts/start.sh` again. PostgreSQL uses 55433 and is published by the existing `postgres` service in `compose.yaml`. Change it there if it clashes.

**The page loads but shows no data, or the browser reports a CORS error.** The address in your browser has to match `CORS_ALLOWED_ORIGIN` exactly, so `localhost` and `127.0.0.1` are different. Set `CORS_ALLOWED_ORIGIN` in `.env` and run `docker compose up -d`.

**The page can't reach the API after changing ports or hosts.** The API address is built into the web app. Set `PUBLIC_API_URL` (or `BACKEND_PORT`) in `.env` and run `docker compose up -d --build`.

**The backend stops right after starting.** Run `docker compose logs backend`. With sign-in on, the usual cause is a token secret shorter than 32 characters.

**No new earthquakes or events appear.** The backend needs outbound access to `earthquake.usgs.gov` and `eonet.gsfc.nasa.gov`. Check `docker compose logs backend`, and that `INGESTION_ENABLED` is `true`.

**You lost the only administrator password.** This removes every account, then recreates the first administrator from `.env`:

```
docker compose exec postgres psql -U terrascope -d terrascope -c "DELETE FROM app_users"
docker compose restart backend
```

Make sure `TERRASCOPE_SECURITY_ADMIN_USERNAME` and `TERRASCOPE_SECURITY_ADMIN_PASSWORD` are set in `.env` first. Other users have to be added again from **Manage users**.

**`--demo` says it needs Node.js.** The demo loader is a Node.js script, so it needs Node.js 18 or newer on your computer, outside Docker. Install it, or start without `--demo` and run `node scripts/seed-demo.mjs` later.

**The demo data was not fully loaded.** The lines starting with `!` in the output say what failed. Run `node scripts/seed-demo.mjs` again: it skips what already exists and fills in the rest. If sign-in is on, it needs the administrator account from `.env`, or `--username` and `--password`.

**The screenshot script skips the map screens.** The map tiles come from OpenStreetMap, so the computer running the script needs internet access. Use `--tile-wait 60` on a slow connection. `--allow-blank-map` saves the screens anyway, but the map will be empty.

**Start completely fresh.** `docker compose down -v` deletes the database and its volume.
