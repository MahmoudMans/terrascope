#!/usr/bin/env node
import { existsSync, readFileSync } from "node:fs";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";

import { buildDemoData } from "./demo-data.mjs";

const ROOT = resolve(dirname(fileURLToPath(import.meta.url)), "..");

const USAGE = `Usage: node scripts/seed-demo.mjs [options]

Fills a running TerraScope with a small demo dataset: alert rules and alerts,
saved events, impact simulations, regional analyses and five incidents with
their action, resource, evacuation, lifeline, damage and report boards.

Running it again is safe. Anything that already exists is left alone.

Options:
  --api <url>         backend address (default: http://127.0.0.1:<BACKEND_PORT>)
  --username <name>   administrator or operator name when sign-in is on
  --password <text>   password (or set TERRASCOPE_PASSWORD)
  --wait <seconds>    how long to wait for the backend (default: 60)
  --dry-run           print what would be created and exit
  -h, --help          show this text

Without --username and --password the script reads
TERRASCOPE_SECURITY_ADMIN_USERNAME and TERRASCOPE_SECURITY_ADMIN_PASSWORD
from .env, which is where scripts/start.sh --sign-in saves them.

Exit codes: 0 done, 1 could not run, 2 bad options, 3 done with warnings.`;

class ApiError extends Error {
  constructor(status, message) {
    super(message);
    this.status = status;
  }
}

function reason(error) {
  const message = error instanceof Error ? error.message : String(error);

  const cause = error instanceof Error ? error.cause : undefined;

  if (cause && typeof cause === "object" && (cause.code || cause.message)) {
    return `${message} (${cause.code ?? cause.message})`;
  }

  return message;
}

function stop(message, code = 1) {
  console.error(message);
  process.exit(code);
}

function parseArguments(argv) {
  const options = {
    api: null,
    username: null,
    password: null,
    wait: 60,
    dryRun: false,
    help: false,
  };

  const valued = new Set(["--api", "--username", "--password", "--wait"]);

  for (let index = 0; index < argv.length; index += 1) {
    const argument = argv[index];

    if (argument === "-h" || argument === "--help") {
      options.help = true;
    } else if (argument === "--dry-run") {
      options.dryRun = true;
    } else if (valued.has(argument)) {
      const value = argv[index + 1];

      if (value === undefined || value.startsWith("--")) {
        stop(`${argument} needs a value.\n\n${USAGE}`, 2);
      }

      index += 1;

      if (argument === "--wait") {
        const seconds = Number(value);

        if (!Number.isFinite(seconds) || seconds < 0) {
          stop("--wait needs a number of seconds.", 2);
        }

        options.wait = seconds;
      } else {
        options[argument.slice(2)] = value;
      }
    } else {
      stop(`Unknown option: ${argument}\n\n${USAGE}`, 2);
    }
  }

  return options;
}

function readDotEnv() {
  const file = join(ROOT, ".env");
  const values = {};

  if (!existsSync(file)) {
    return values;
  }

  for (const line of readFileSync(file, "utf8").split(/\r?\n/)) {
    const match = /^([A-Za-z_][A-Za-z0-9_]*)=(.*)$/.exec(line);

    if (match) {
      values[match[1]] = match[2].trim().replace(/^(["'])(.*)\1$/, "$2");
    }
  }

  return values;
}

function resolveBase(options, env) {
  const raw =
    options.api
    ?? process.env.TERRASCOPE_API
    ?? `http://127.0.0.1:${env.BACKEND_PORT || "8087"}`;

  const withScheme = /^https?:\/\//i.test(raw) ? raw : `http://${raw}`;

  const trimmed = withScheme.replace(/\/+$/, "");

  return trimmed.endsWith("/api/v1") ? trimmed : `${trimmed}/api/v1`;
}

function describeFailure(data, status) {
  if (data && typeof data === "object") {
    if (typeof data.message === "string" && data.message !== "") {
      return data.message;
    }

    if (typeof data.error === "string" && data.error !== "") {
      return data.error;
    }
  }

  if (typeof data === "string" && data !== "") {
    return data.slice(0, 200);
  }

  return `HTTP ${status}`;
}

function createClient(base) {
  let token = null;

  async function call(method, path, body) {
    const headers = { Accept: "application/json" };

    if (body !== undefined) {
      headers["Content-Type"] = "application/json";
    }

    if (token !== null) {
      headers.Authorization = `Bearer ${token}`;
    }

    const response = await fetch(`${base}${path}`, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
      signal: AbortSignal.timeout(30000),
    });

    const text = await response.text();

    let data = null;

    if (text !== "") {
      try {
        data = JSON.parse(text);
      } catch {
        data = text;
      }
    }

    if (!response.ok) {
      throw new ApiError(
        response.status,
        `${method} ${path} failed: ${describeFailure(data, response.status)}`,
      );
    }

    return data;
  }

  return {
    get: (path) => call("GET", path),
    post: (path, body) => call("POST", path, body === undefined ? {} : body),
    patch: (path, body) => call("PATCH", path, body),
    setToken: (value) => {
      token = value;
    },
  };
}

function pause(milliseconds) {
  return new Promise((done) => setTimeout(done, milliseconds));
}

async function waitForBackend(api, seconds) {
  const deadline = Date.now() + seconds * 1000;

  let lastError = null;

  for (;;) {
    try {
      return await api.get("/auth/config");
    } catch (error) {
      lastError = error;
    }

    if (Date.now() >= deadline) {
      throw lastError;
    }

    await pause(2000);
  }
}

const counts = new Map();

let warnings = 0;

function count(kind, state, amount = 1) {
  if (amount <= 0) {
    return;
  }

  const entry = counts.get(kind) ?? { created: 0, existing: 0 };

  entry[state] += amount;

  counts.set(kind, entry);
}

function warn(label, error) {
  warnings += 1;

  const message = error instanceof Error ? error.message : String(error);

  console.log(`  ! ${label}: ${message}`);
}

async function attempt(label, task) {
  try {
    return await task();
  } catch (error) {
    warn(label, error);

    return undefined;
  }
}

function heading(text) {
  console.log(`\n${text}`);
}

async function seedAlertRules(api, data) {
  heading("Alert rules");

  const dashboard = await api.get("/alerts");

  const known = new Set(dashboard.rules.map((rule) => rule.name));

  for (const rule of data.alertRules) {
    if (known.has(rule.name)) {
      count("alert rules", "existing");
      console.log(`  = ${rule.name}`);
      continue;
    }

    await attempt(`rule "${rule.name}"`, async () => {
      await api.post("/alerts/rules", rule);
      count("alert rules", "created");
      console.log(`  + ${rule.name}`);
    });
  }
}

async function seedAlerts(api, data) {
  heading("Alerts");

  const result = await api.post("/alerts/evaluate", {
    points: data.alertPoints,
  });

  count("alerts", "created", result.newlyTriggeredCount);

  console.log(
    `  ${result.newlyTriggeredCount} new alert(s) from ${result.evaluatedPointCount} demo events`,
  );

  if (result.newlyTriggeredCount === 0) {
    return;
  }

  for (const alert of result.dashboard.alerts) {
    const wanted = data.alertStatuses[alert.sourceEventId];

    if (wanted === undefined || alert.status !== "NEW") {
      continue;
    }

    await attempt(`alert "${alert.title}"`, async () => {
      await api.patch(`/alerts/events/${alert.id}/status`, {
        status: wanted,
      });
      console.log(`  ~ ${alert.title}: ${wanted.toLowerCase()}`);
    });
  }
}

async function seedWatchlist(api, data) {
  heading("Saved events");

  const current = await api.get("/watchlist");

  const known = new Set(
    current.entries.map((entry) => `${entry.hazardType}|${entry.sourceEventId}`),
  );

  for (const entry of data.watchlist) {
    if (known.has(`${entry.hazardType}|${entry.sourceEventId}`)) {
      count("saved events", "existing");
      console.log(`  = ${entry.title}`);
      continue;
    }

    await attempt(`saved event "${entry.title}"`, async () => {
      await api.post("/watchlist", entry);
      count("saved events", "created");
      console.log(`  + ${entry.title}`);
    });
  }
}

async function seedSimulations(api, data) {
  heading("Impact simulations");

  const history = await api.get("/simulations");

  const known = new Set(history.runs.map((run) => run.scenario.name));

  for (const scenario of data.simulations) {
    if (known.has(scenario.name)) {
      count("simulations", "existing");
      console.log(`  = ${scenario.name}`);
      continue;
    }

    await attempt(`simulation "${scenario.name}"`, async () => {
      await api.post("/simulations/run", scenario);
      count("simulations", "created");
      console.log(`  + ${scenario.name}`);
    });
  }
}

async function seedRegions(api, data) {
  heading("Regional analyses");

  const history = await api.get("/regional-intelligence/history");

  const known = new Set(history.runs.map((run) => run.regionName));

  for (const region of data.regions) {
    if (known.has(region.regionName)) {
      count("regional analyses", "existing");
      console.log(`  = ${region.regionName}`);
      continue;
    }

    await attempt(`region "${region.regionName}"`, async () => {
      await api.post("/regional-intelligence/analyze", region);
      count("regional analyses", "created");
      console.log(`  + ${region.regionName}`);
    });
  }
}

function findBy(items, key, value, what) {
  const match = items.find((item) => item[key] === value);

  if (match === undefined) {
    throw new Error(`${what} "${value}" was not found after it was created`);
  }

  return match;
}

async function seedActions(api, data, incidentId, definition) {
  const base = `/incidents/${incidentId}/actions`;

  const board = await api.get(base);

  if (board.actions.length > 0) {
    console.log("    actions: already filled, left alone");
    return 0;
  }

  let created = 0;

  for (const item of definition.actions) {
    await attempt(`action "${item.title}"`, async () => {
      const updated = await api.post(base, {
        title: item.title,
        priority: item.priority,
        owner: item.owner,
        dueAt: data.at(item.dueInHours),
      });

      created += 1;
      count("actions", "created");

      if (item.status !== "TODO") {
        const action = findBy(updated.actions, "title", item.title, "Action");

        await api.patch(`${base}/${action.id}/status`, {
          status: item.status,
        });
      }
    });
  }

  console.log(`    actions: ${created} of ${definition.actions.length}`);

  return created;
}

async function seedResources(api, incidentId, definition) {
  const base = `/incidents/${incidentId}/resources`;

  const board = await api.get(base);

  if (board.resources.length > 0) {
    console.log("    resources: already filled, left alone");
    return 0;
  }

  let created = 0;

  for (const item of definition.resources) {
    await attempt(`resource "${item.name}"`, async () => {
      const updated = await api.post(base, {
        category: item.category,
        name: item.name,
        priority: item.priority,
        requestedQuantity: item.requestedQuantity,
        unit: item.unit,
        owner: item.owner,
      });

      created += 1;
      count("resource requests", "created");

      if (item.allocatedQuantity > 0) {
        const resource = findBy(updated.resources, "name", item.name, "Resource");

        await api.patch(`${base}/${resource.id}/allocation`, {
          allocatedQuantity: item.allocatedQuantity,
        });
      }
    });
  }

  console.log(`    resources: ${created} of ${definition.resources.length}`);

  return created;
}

async function seedEvacuation(api, incidentId, definition) {
  const base = `/incidents/${incidentId}/evacuation`;

  const board = await api.get(base);

  if (board.zones.length > 0 || board.shelters.length > 0) {
    console.log("    evacuation: already filled, left alone");
    return 0;
  }

  let created = 0;

  for (const zone of definition.zones) {
    await attempt(`zone "${zone.name}"`, async () => {
      await api.post(`${base}/zones`, zone);
      created += 1;
      count("evacuation zones", "created");
    });
  }

  for (const shelter of definition.shelters) {
    await attempt(`shelter "${shelter.name}"`, async () => {
      await api.post(`${base}/shelters`, shelter);
      created += 1;
      count("shelters", "created");
    });
  }

  console.log(
    `    evacuation: ${created} of ${definition.zones.length + definition.shelters.length}`,
  );

  return created;
}

async function seedLifelines(api, data, incidentId, definition) {
  const base = `/incidents/${incidentId}/lifelines`;

  const board = await api.get(base);

  if (board.assets.length > 0) {
    console.log("    lifelines: already filled, left alone");
    return 0;
  }

  let created = 0;

  for (const item of definition.lifelines) {
    await attempt(`lifeline "${item.name}"`, async () => {
      await api.post(base, {
        category: item.category,
        name: item.name,
        description: item.description,
        priority: item.priority,
        status: item.status,
        affectedPopulation: item.affectedPopulation,
        owner: item.owner,
        restorationEta:
          item.etaInHours === undefined ? undefined : data.at(item.etaInHours),
      });

      created += 1;
      count("lifeline assets", "created");
    });
  }

  console.log(`    lifelines: ${created} of ${definition.lifelines.length}`);

  return created;
}

async function seedDamage(api, data, incidentId, definition) {
  const base = `/incidents/${incidentId}/damage`;

  const board = await api.get(base);

  if (board.assessments.length > 0) {
    console.log("    damage: already filled, left alone");
    return 0;
  }

  let created = 0;

  for (const item of definition.damage) {
    await attempt(`damage "${item.assetName}"`, async () => {
      const updated = await api.post(base, {
        category: item.category,
        assetName: item.assetName,
        description: item.description,
        severity: item.severity,
        affectedUnits: item.affectedUnits,
        estimatedLossMusd: item.estimatedLossMusd,
        owner: item.owner,
        recoveryEta: data.at(item.recoveryInDays * 24),
      });

      created += 1;
      count("damage assessments", "created");

      const assessment = findBy(
        updated.assessments,
        "assetName",
        item.assetName,
        "Damage assessment",
      );

      if (item.verify) {
        await api.post(`${base}/${assessment.id}/verify`);

        if (item.progress > 0) {
          await api.patch(`${base}/${assessment.id}/progress`, {
            repairProgressPct: item.progress,
          });
        }
      }
    });
  }

  console.log(`    damage: ${created} of ${definition.damage.length}`);

  return created;
}

async function seedReport(api, incidentId, definition) {
  if (!definition.report) {
    return 0;
  }

  const base = `/incidents/${incidentId}/sitrep/reports`;

  const history = await api.get(base);

  if (history.reports.length > 0) {
    console.log("    situation report: already there, left alone");
    return 0;
  }

  await api.post(base);
  count("situation reports", "created");
  console.log("    situation report: 1 of 1");

  return 1;
}

async function seedIncident(api, data, definition, known, alertIds) {
  console.log(`\n  ${definition.title}`);

  const existing = known.find(
    (incident) =>
      incident.hazardType === definition.hazardType
      && incident.sourceEventId === definition.sourceEventId,
  );

  let incident = existing;

  if (existing === undefined) {
    const detail = await api.post("/incidents", {
      alertEventId: alertIds.get(definition.sourceEventId),
      sourceEventId: definition.sourceEventId,
      hazardType: definition.hazardType,
      title: definition.title,
      description: definition.description,
      latitude: definition.latitude,
      longitude: definition.longitude,
      severity: definition.severity,
      owner: definition.owner,
    });

    incident = detail.incident;
    count("incidents", "created");
    console.log("    incident: created");

    for (const status of definition.statusFlow) {
      await attempt(`status ${status}`, async () => {
        await api.patch(`/incidents/${incident.id}/status`, { status });
      });
    }

    for (const message of definition.notes) {
      await attempt("note", async () => {
        await api.post(`/incidents/${incident.id}/notes`, { message });
      });
    }
  } else {
    count("incidents", "existing");
    console.log("    incident: already there");
  }

  if (incident.status === "CLOSED") {
    console.log("    boards: incident is closed, left alone");
    return;
  }

  const before = warnings;

  let seeded = 0;

  const steps = [
    () => seedActions(api, data, incident.id, definition),
    () => seedResources(api, incident.id, definition),
    () => seedEvacuation(api, incident.id, definition),
    () => seedLifelines(api, data, incident.id, definition),
    () => seedDamage(api, data, incident.id, definition),
    () => seedReport(api, incident.id, definition),
  ];

  for (const step of steps) {
    const added = await attempt("board", step);

    seeded += added ?? 0;
  }

  if (definition.closeWhenSeeded && seeded > 0 && warnings === before) {
    await attempt("closing the incident", async () => {
      await api.patch(`/incidents/${incident.id}/status`, {
        status: "CLOSED",
      });
      console.log("    incident: closed");
    });
  }
}

async function seedIncidents(api, data) {
  heading("Incidents");

  const dashboard = await api.get("/incidents");

  const alertIds = new Map();

  await attempt("alert lookup", async () => {
    const alerts = await api.get("/alerts");

    for (const alert of alerts.alerts) {
      if (!alertIds.has(alert.sourceEventId)) {
        alertIds.set(alert.sourceEventId, alert.id);
      }
    }
  });

  for (const definition of data.incidents) {
    await attempt(`incident "${definition.title}"`, () =>
      seedIncident(api, data, definition, dashboard.incidents, alertIds),
    );
  }
}

function printPlan(data) {
  console.log("Dry run. Nothing will be sent.\n");
  console.log(`  alert rules         ${data.alertRules.length}`);
  console.log(`  demo alert events   ${data.alertPoints.length}`);
  console.log(`  saved events        ${data.watchlist.length}`);
  console.log(`  simulations         ${data.simulations.length}`);
  console.log(`  regional analyses   ${data.regions.length}`);
  console.log(`  incidents           ${data.incidents.length}`);

  for (const incident of data.incidents) {
    console.log(
      `    ${incident.title}: ${incident.actions.length} actions, `
        + `${incident.resources.length} resources, `
        + `${incident.zones.length} zones, ${incident.shelters.length} shelters, `
        + `${incident.lifelines.length} lifelines, ${incident.damage.length} damage`,
    );
  }
}

async function signIn(api, options, env) {
  const username =
    options.username
    ?? process.env.TERRASCOPE_USERNAME
    ?? env.TERRASCOPE_SECURITY_ADMIN_USERNAME;

  const password =
    options.password
    ?? process.env.TERRASCOPE_PASSWORD
    ?? env.TERRASCOPE_SECURITY_ADMIN_PASSWORD;

  if (!username || !password) {
    stop(
      "Sign-in is on, but no credentials were found.\n"
        + "Pass --username and --password, set TERRASCOPE_USERNAME and TERRASCOPE_PASSWORD,\n"
        + "or put the administrator account in .env.",
    );
  }

  try {
    const session = await api.post("/auth/login", { username, password });

    api.setToken(session.token);

    console.log(`Signed in as ${session.user?.username ?? username}.`);
  } catch (error) {
    stop(`Could not sign in: ${error.message}`);
  }
}

function printSummary(env) {
  console.log("\nSummary");

  if (counts.size === 0) {
    console.log("  nothing was created");
  }

  for (const [kind, entry] of counts) {
    const parts = [];

    if (entry.created > 0) {
      parts.push(`${entry.created} created`);
    }

    if (entry.existing > 0) {
      parts.push(`${entry.existing} already there`);
    }

    console.log(`  ${kind.padEnd(20)}${parts.join(", ")}`);
  }

  console.log(
    warnings === 0
      ? "\nDemo data is ready."
      : `\nFinished with ${warnings} warning(s). The lines starting with ! say what failed.`,
  );

  console.log(`Open http://localhost:${env.WEB_PORT || "3000"}`);
}

async function main() {
  const options = parseArguments(process.argv.slice(2));

  if (options.help) {
    console.log(USAGE);
    return 0;
  }

  const data = buildDemoData(new Date());

  if (options.dryRun) {
    printPlan(data);
    return 0;
  }

  const env = readDotEnv();

  const base = resolveBase(options, env);

  const api = createClient(base);

  console.log(`Backend: ${base}`);

  let config;

  try {
    config = await waitForBackend(api, options.wait);
  } catch (error) {
    stop(
      `The backend did not answer: ${reason(error)}\n`
        + "Start TerraScope first (bash scripts/start.sh) or pass --api.",
    );
  }

  if (config.enabled) {
    await signIn(api, options, env);
  }

  const sections = [
    () => seedAlertRules(api, data),
    () => seedAlerts(api, data),
    () => seedWatchlist(api, data),
    () => seedIncidents(api, data),
    () => seedSimulations(api, data),
    () => seedRegions(api, data),
  ];

  for (const section of sections) {
    await attempt("section", section);
  }

  printSummary(env);

  return warnings === 0 ? 0 : 3;
}

process.stdout.on("error", (error) => {
  if (error.code === "EPIPE") {
    process.exit(0);
  }
});

main().then(
  (code) => {
    process.exitCode = code;
  },
  (error) => {
    console.error(error instanceof Error ? error.message : error);
    process.exitCode = 1;
  },
);
