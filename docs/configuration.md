# Configuration

## Docker settings (`.env`)

Docker Compose reads `.env` in the project folder. It is ignored by git, so it is safe to keep secrets there. Every value is optional.

| Variable                              | Default                          | What it does                                                                                              |
| ------------------------------------- | -------------------------------- | --------------------------------------------------------------------------------------------------------- |
| `WEB_PORT`                            | `3000`                           | Port the web app is published on.                                                                         |
| `BACKEND_PORT`                        | `8087`                           | Port the API is published on.                                                                             |
| `BIND_ADDRESS`                        | `127.0.0.1`                      | Address the web and API ports listen on. The default keeps them reachable from this computer only. Use `0.0.0.0` to share them on your network. |
| `PUBLIC_API_URL`                      | `http://localhost:8087/api/v1`   | API address the browser uses. Built into the web app, so run `docker compose up -d --build` after changing it. Follows `BACKEND_PORT` unless you set it. |
| `CORS_ALLOWED_ORIGIN`                 | `http://localhost:3000`          | Web address the API accepts requests from. It must match the address in your browser's address bar. Follows `WEB_PORT` unless you set it. |
| `TERRASCOPE_SECURITY_ENABLED`         | `false`                          | Turns sign-in on.                                                                                         |
| `TERRASCOPE_SECURITY_TOKEN_SECRET`    | empty                            | Secret that signs session tokens. At least 32 characters. Required when sign-in is on.                    |
| `TERRASCOPE_SECURITY_TOKEN_TTL`       | `PT8H`                           | How long a sign-in lasts, as an ISO-8601 duration. Limited to between 5 minutes and 30 days.              |
| `TERRASCOPE_SECURITY_ADMIN_USERNAME`  | `admin`                          | Username for the first administrator.                                                                     |
| `TERRASCOPE_SECURITY_ADMIN_PASSWORD`  | empty                            | Password for the first administrator. Only used while no users exist.                                     |
| `TERRASCOPE_ACTIVITY_RETENTION_DAYS`  | `180`                            | How long activity log entries are kept. `0` keeps them forever.                                           |
| `INGESTION_ENABLED`                   | `true`                           | Fetch USGS and NASA EONET data on a schedule.                                                             |
| `INGESTION_EVALUATE_ALERTS`           | `true`                           | Evaluate alert rules after each fetch.                                                                    |
| `INGESTION_INTERVAL`                  | `PT2M`                           | Time between fetches, as an ISO-8601 duration.                                                            |

## Backend settings

The backend reads these environment variables, whether it runs in Docker or with `mvn spring-boot:run`. The defaults suit local development. Inside Docker, Compose sets `DB_HOST` and `DB_PORT` for you.

| Variable                       | Default                  | What it does                                  |
| ------------------------------ | ------------------------ | --------------------------------------------- |
| `SERVER_PORT`                  | `8087`                   | Port the API listens on.                      |
| `DB_HOST`                      | `localhost`              | Database host.                                |
| `DB_PORT`                      | `55433`                  | Database port.                                |
| `DB_NAME`                      | `terrascope`             | Database name.                                |
| `CORS_ALLOWED_ORIGIN`          | `http://localhost:3000`  | Web address the API accepts requests from.    |
| `INGESTION_ENABLED`            | `true`                   | Fetch external data on a schedule.            |
| `INGESTION_EVALUATE_ALERTS`    | `true`                   | Evaluate alert rules after each fetch.        |
| `INGESTION_INTERVAL`           | `PT2M`                   | Time between fetches.                         |

The sign-in and activity settings from the table above use the same names for the backend. Spring maps `TERRASCOPE_SECURITY_TOKEN_SECRET` to `terrascope.security.token-secret`, and so on.

The database user and password are defined twice: in the `postgres` service of `compose.yaml` and in the `spring.datasource` section of `apps/backend/src/main/resources/application.yml`. If you change one, change the other.

## Web settings

| Variable                    | Default                        | What it does                                                                             |
| --------------------------- | ------------------------------ | ---------------------------------------------------------------------------------------- |
| `NEXT_PUBLIC_API_BASE_URL`  | `http://localhost:8087/api/v1` | API address the browser calls. It is read when the app is built, not when it starts.     |
