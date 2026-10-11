# Development

Use this when you are changing the code. You need Java 21, Maven 3.9 or newer, Node.js 20.9 or newer, and Docker for the database.

Start only the database:

```
bash scripts/dev.sh
```

Run the backend in one terminal:

```
cd apps/backend
mvn spring-boot:run
```

Run the web app in another:

```
cd apps/web
npm run dev
```

Then open http://localhost:3000. The backend listens on port 8087 and the web app finds it at `http://localhost:8087/api/v1` by default.

Don't run the containerised backend and `mvn spring-boot:run` at the same time, because they both want port 8087.

## Project layout

| Path                         | What is in it                                                                                      |
| ---------------------------- | -------------------------------------------------------------------------------------------------- |
| `apps/backend`               | Spring Boot API, database migrations (`src/main/resources/db/migration`) and tests                 |
| `apps/web`                   | Next.js web app                                                                                    |
| `docs`                       | Reference pages, and the screenshots used by the README in `docs/screenshots`                      |
| `tools`                      | Standalone verifier tools for trust and checkpoint bundles                                         |
| `tools/screenshots`          | The Playwright script behind `scripts/screenshots.sh`                                              |
| `scripts/start.sh`           | One-command Docker start. `--sign-in` turns on sign-in and `--demo` loads the demo data.           |
| `scripts/dev.sh`             | Starts only the database for development                                                          |
| `scripts/seed-demo.mjs`      | Loads the demo dataset into a running backend. The data itself is in `scripts/demo-data.mjs`.      |
| `scripts/screenshots.sh`     | Takes real screenshots of a running app                                                            |
| `scripts/verify.sh`          | Long end-to-end check: backend tests, build, schema and the verifier release tools. Needs Java, Maven and Docker. |
| `compose.yaml`               | Database, backend and web                                                                          |

## Checks

Backend tests:

```
cd apps/backend
mvn test
```

Web lint and type check:

```
cd apps/web
npm run lint
npm run typecheck
```
