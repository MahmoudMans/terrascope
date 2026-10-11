const HOUR = 3600000;

function at(now, hours) {
  return new Date(now.getTime() + hours * HOUR).toISOString();
}

function syntheticPoints(now, slug, latitude, longitude, radiusKm, count, seed) {
  const kinds = [
    ["earthquakes", "Earthquake"],
    ["earthquakes", "Earthquake"],
    ["wildfires", "Wildfire"],
    ["severe-storms", "Severe storm"],
    ["volcanoes", "Volcanic activity"],
    ["earthquakes", "Earthquake"],
  ];

  let state = seed;

  const next = () => {
    state = (state * 1103515245 + 12345) % 2147483648;

    return state / 2147483648;
  };

  const spread = (radiusKm / 111) * 0.85;

  const points = [];

  for (let index = 0; index < count; index += 1) {
    const [hazardType, label] = kinds[index % kinds.length];

    const offsetLat = (next() * 2 - 1) * spread;
    const offsetLon = (next() * 2 - 1) * spread;

    const intensity =
      hazardType === "earthquakes"
        ? Math.round((2.4 + next() * 4.1) * 10) / 10
        : null;

    points.push({
      id: `demo-${slug}-${index + 1}`,
      hazardType,
      title: `${label} signal ${index + 1}`,
      latitude: Math.round((latitude + offsetLat) * 1000) / 1000,
      longitude: Math.round((longitude + offsetLon) * 1000) / 1000,
      intensity,
      observedAt: at(now, -(2 + Math.floor(next() * 70))),
    });
  }

  return points;
}

export function buildDemoData(now = new Date()) {
  const alertRules = [
    {
      name: "Aegean seismic watch",
      hazardType: "earthquakes",
      latitude: 38.2,
      longitude: 26.3,
      radiusKm: 450,
      minimumIntensity: 4.5,
    },
    {
      name: "Etna volcanic activity",
      hazardType: "volcanoes",
      latitude: 37.75,
      longitude: 14.99,
      radiusKm: 120,
      minimumIntensity: 0,
    },
    {
      name: "Peloponnese wildfire perimeter",
      hazardType: "wildfires",
      latitude: 37.5,
      longitude: 22.2,
      radiusKm: 250,
      minimumIntensity: 0,
    },
    {
      name: "Tunisian coast storm tracker",
      hazardType: "severe-storms",
      latitude: 36.9,
      longitude: 10.6,
      radiusKm: 400,
      minimumIntensity: 0,
    },
    {
      name: "Central Mediterranean, strong events",
      hazardType: "all",
      latitude: 36.0,
      longitude: 14.0,
      radiusKm: 900,
      minimumIntensity: 5.0,
    },
  ];

  const alertPoints = [
    {
      id: "demo-eq-izmir",
      hazardType: "earthquakes",
      title: "M6.7 earthquake, Izmir coast",
      latitude: 38.43,
      longitude: 27.0,
      intensity: 6.7,
      observedAt: at(now, -30),
    },
    {
      id: "demo-eq-aftershock",
      hazardType: "earthquakes",
      title: "M4.8 aftershock, Izmir Bay",
      latitude: 38.35,
      longitude: 26.7,
      intensity: 4.8,
      observedAt: at(now, -26),
    },
    {
      id: "demo-eq-crete",
      hazardType: "earthquakes",
      title: "M5.2 earthquake south of Heraklion",
      latitude: 34.98,
      longitude: 25.1,
      intensity: 5.2,
      observedAt: at(now, -140),
    },
    {
      id: "demo-eq-sicily",
      hazardType: "earthquakes",
      title: "M5.9 earthquake, Ionian Sea off eastern Sicily",
      latitude: 37.3,
      longitude: 15.4,
      intensity: 5.9,
      observedAt: at(now, -9),
    },
    {
      id: "demo-volcano-etna",
      hazardType: "volcanoes",
      title: "Mount Etna lava fountaining and ash plume",
      latitude: 37.75,
      longitude: 14.99,
      intensity: null,
      observedAt: at(now, -14),
    },
    {
      id: "demo-wildfire-laconia",
      hazardType: "wildfires",
      title: "Wildfire front in the Laconia hills",
      latitude: 36.95,
      longitude: 22.4,
      intensity: null,
      observedAt: at(now, -20),
    },
    {
      id: "demo-storm-capbon",
      hazardType: "severe-storms",
      title: "Severe storm cell over the Cap Bon coast",
      latitude: 36.85,
      longitude: 10.9,
      intensity: null,
      observedAt: at(now, -48),
    },
  ];

  const alertStatuses = {
    "demo-eq-izmir": "ACKNOWLEDGED",
    "demo-eq-crete": "DISMISSED",
  };

  const watchlist = [
    {
      sourceEventId: "demo-eq-izmir",
      hazardType: "earthquakes",
      title: "M6.7 earthquake, Izmir coast",
      description: "Strong shallow earthquake with heavy damage along the waterfront.",
      latitude: 38.43,
      longitude: 27.0,
      observedAt: at(now, -30),
      sourceProvider: "Demo dataset",
      magnitudeValue: 6.7,
      magnitudeUnit: "Mw",
      priority: "CRITICAL",
      notes: "Linked to an active incident. Review aftershock sequence every hour.",
    },
    {
      sourceEventId: "demo-eq-sicily",
      hazardType: "earthquakes",
      title: "M5.9 earthquake, Ionian Sea off eastern Sicily",
      description: "Offshore event felt along the Catania coast.",
      latitude: 37.3,
      longitude: 15.4,
      observedAt: at(now, -9),
      sourceProvider: "Demo dataset",
      magnitudeValue: 5.9,
      magnitudeUnit: "Mw",
      priority: "HIGH",
      notes: "Possible link to the ongoing Etna activity.",
    },
    {
      sourceEventId: "demo-volcano-etna",
      hazardType: "volcanoes",
      title: "Mount Etna lava fountaining and ash plume",
      description: "Paroxysmal phase with an ash plume drifting south-east.",
      latitude: 37.75,
      longitude: 14.99,
      observedAt: at(now, -14),
      sourceProvider: "Demo dataset",
      priority: "HIGH",
      notes: "Watch airport advisories for Catania.",
    },
    {
      sourceEventId: "demo-wildfire-laconia",
      hazardType: "wildfires",
      title: "Wildfire front in the Laconia hills",
      description: "Fast-moving fire driven by a strong northerly wind.",
      latitude: 36.95,
      longitude: 22.4,
      observedAt: at(now, -20),
      sourceProvider: "Demo dataset",
      priority: "MEDIUM",
    },
    {
      sourceEventId: "demo-storm-capbon",
      hazardType: "severe-storms",
      title: "Severe storm cell over the Cap Bon coast",
      description: "Heavy rainfall and coastal flooding.",
      latitude: 36.85,
      longitude: 10.9,
      observedAt: at(now, -48),
      sourceProvider: "Demo dataset",
      priority: "LOW",
    },
  ];

  const simulations = [
    {
      name: "Izmir M6.7: district impact",
      hazardType: "earthquakes",
      latitude: 38.43,
      longitude: 27.0,
      radiusKm: 25,
      intensity: 6.7,
      exposedPopulation: 420000,
      infrastructureResiliencePct: 55,
      emergencyReadinessPct: 62,
    },
    {
      name: "Laconia fire: wind shift worst case",
      hazardType: "wildfires",
      latitude: 36.95,
      longitude: 22.4,
      radiusKm: 18,
      intensity: 8.5,
      exposedPopulation: 35000,
      infrastructureResiliencePct: 40,
      emergencyReadinessPct: 58,
    },
    {
      name: "Etna paroxysm: ash fall over Catania",
      hazardType: "volcanoes",
      latitude: 37.5,
      longitude: 15.09,
      radiusKm: 40,
      intensity: 7.2,
      exposedPopulation: 310000,
      infrastructureResiliencePct: 70,
      emergencyReadinessPct: 75,
    },
    {
      name: "Cap Bon flash flood scenario",
      hazardType: "severe-storms",
      latitude: 36.85,
      longitude: 10.9,
      radiusKm: 30,
      intensity: 7.8,
      exposedPopulation: 180000,
      infrastructureResiliencePct: 48,
      emergencyReadinessPct: 52,
    },
  ];

  const regions = [
    {
      regionName: "Aegean basin",
      latitude: 38.5,
      longitude: 26.5,
      radiusKm: 600,
      points: syntheticPoints(now, "aegean", 38.5, 26.5, 600, 16, 11),
    },
    {
      regionName: "Central Mediterranean",
      latitude: 36.5,
      longitude: 14.0,
      radiusKm: 800,
      points: syntheticPoints(now, "central-med", 36.5, 14.0, 800, 14, 23),
    },
    {
      regionName: "Tunisian coast",
      latitude: 36.0,
      longitude: 10.0,
      radiusKm: 500,
      points: syntheticPoints(now, "tunisia", 36.0, 10.0, 500, 12, 37),
    },
  ];

  const incidents = [
    {
      sourceEventId: "demo-eq-izmir",
      hazardType: "earthquakes",
      title: "M6.7 earthquake, Izmir coast",
      description:
        "Strong shallow earthquake with heavy damage along the Izmir waterfront and the northern districts. Demo scenario generated by the TerraScope demo seeder.",
      latitude: 38.43,
      longitude: 27.0,
      severity: "CRITICAL",
      owner: "Amira Belhaj",
      statusFlow: ["MONITORING", "RESPONDING"],
      notes: [
        "Emergency operations centre activated. Liaison officers deployed to three districts.",
        "Preliminary reports: collapsed structures in two districts and a partial blackout around the harbour.",
        "Field hospital requested from the regional health authority.",
        "A second aftershock (M4.8) was recorded. Evacuation of damaged buildings continues.",
      ],
      actions: [
        { title: "Establish the incident command post at the harbour", priority: "CRITICAL", owner: "Amira Belhaj", dueInHours: -20, status: "DONE" },
        { title: "Activate mutual-aid agreements with neighbouring provinces", priority: "HIGH", owner: "Karim Haddad", dueInHours: -16, status: "DONE" },
        { title: "Search-and-rescue sweep of the northern districts", priority: "CRITICAL", owner: "Lena Voss", dueInHours: 6, status: "IN_PROGRESS" },
        { title: "Publish the hourly public safety bulletin", priority: "MEDIUM", owner: "Sofia Papadaki", dueInHours: 1, status: "IN_PROGRESS" },
        { title: "Restore mobile coverage on the waterfront", priority: "HIGH", owner: "Youssef Trabelsi", dueInHours: 8, status: "BLOCKED" },
        { title: "Inspect hospital structures before re-admission", priority: "HIGH", owner: "Marco Ferri", dueInHours: -2, status: "TODO" },
        { title: "Coordinate fuel deliveries for generators", priority: "MEDIUM", owner: "Karim Haddad", dueInHours: 12, status: "TODO" },
      ],
      resources: [
        { category: "PERSONNEL", name: "Urban search-and-rescue teams", priority: "CRITICAL", requestedQuantity: 12, unit: "teams", allocatedQuantity: 9, owner: "Lena Voss" },
        { category: "MEDICAL", name: "Field hospital modules", priority: "HIGH", requestedQuantity: 3, unit: "modules", allocatedQuantity: 3, owner: "Marco Ferri" },
        { category: "TRANSPORT", name: "Ambulances", priority: "CRITICAL", requestedQuantity: 40, unit: "vehicles", allocatedQuantity: 28, owner: "Karim Haddad" },
        { category: "SHELTER", name: "Winterised tents", priority: "HIGH", requestedQuantity: 600, unit: "tents", allocatedQuantity: 150, owner: "Sofia Papadaki" },
        { category: "EQUIPMENT", name: "Mobile generators (50 kVA)", priority: "HIGH", requestedQuantity: 20, unit: "units", allocatedQuantity: 0, owner: "Youssef Trabelsi" },
        { category: "COMMUNICATIONS", name: "Satellite phones", priority: "MEDIUM", requestedQuantity: 30, unit: "units", allocatedQuantity: 0, owner: "Youssef Trabelsi" },
      ],
      zones: [
        { name: "Harbour district", description: "Waterfront blocks with structural damage.", priority: "CRITICAL", populationAtRisk: 18000, evacuatedPopulation: 14200, owner: "Lena Voss" },
        { name: "Northern hills", description: "Hillside neighbourhoods cut off by landslides.", priority: "HIGH", populationAtRisk: 9500, evacuatedPopulation: 9500, owner: "Marco Ferri" },
        { name: "Old town", description: "Dense historic quarter with unreinforced masonry.", priority: "MEDIUM", populationAtRisk: 6200, evacuatedPopulation: 800, owner: "Sofia Papadaki" },
      ],
      shelters: [
        { name: "Municipal sports hall", address: "Stadium Avenue 1", capacity: 1200, occupancy: 1200, owner: "Sofia Papadaki" },
        { name: "Technical university campus", address: "University Road 8", capacity: 2500, occupancy: 1480, owner: "Sofia Papadaki" },
        { name: "Harbour community centre", address: "Quay Street 22", capacity: 600, occupancy: 410, owner: "Lena Voss" },
      ],
      lifelines: [
        { category: "POWER", name: "Harbour substation", description: "Transformer hall damaged in the main shock.", priority: "CRITICAL", status: "OUTAGE", affectedPopulation: 22000, owner: "Youssef Trabelsi", etaInHours: 10 },
        { category: "WATER", name: "Northern water main", description: "Pipe fractures on the hillside supply line.", priority: "HIGH", status: "DEGRADED", affectedPopulation: 8000, owner: "Karim Haddad", etaInHours: 18 },
        { category: "HEALTHCARE", name: "University hospital", description: "Running on backup power with reduced capacity.", priority: "CRITICAL", status: "DEGRADED", affectedPopulation: 3500, owner: "Marco Ferri", etaInHours: 6 },
        { category: "COMMUNICATIONS", name: "Waterfront cell cluster", description: "Operating on battery backup.", priority: "MEDIUM", status: "OPERATIONAL", affectedPopulation: 0, owner: "Youssef Trabelsi" },
        { category: "TRANSPORT", name: "Coastal highway", description: "Reopened after debris clearance.", priority: "HIGH", status: "RESTORED", affectedPopulation: 0, owner: "Karim Haddad" },
      ],
      damage: [
        { category: "RESIDENTIAL", assetName: "Waterfront apartment blocks", description: "Partial collapse of two blocks and cracking in six more.", severity: "MAJOR", affectedUnits: 46, estimatedLossMusd: 38.5, owner: "Lena Voss", recoveryInDays: 120, verify: true, progress: 15 },
        { category: "PUBLIC_BUILDING", assetName: "Municipal court building", description: "Cracked load-bearing walls on the second floor.", severity: "MODERATE", affectedUnits: 1, estimatedLossMusd: 6.2, owner: "Marco Ferri", recoveryInDays: 60, verify: true, progress: 40 },
        { category: "BRIDGE", assetName: "Coastal highway overpass", description: "Bearing displacement on the southern span.", severity: "MAJOR", affectedUnits: 1, estimatedLossMusd: 12.0, owner: "Karim Haddad", recoveryInDays: 90, verify: true, progress: 0 },
        { category: "HEALTHCARE", assetName: "University hospital east wing", description: "Non-structural damage to services and ceilings.", severity: "MODERATE", affectedUnits: 1, estimatedLossMusd: 9.4, owner: "Marco Ferri", recoveryInDays: 30, verify: true, progress: 65 },
        { category: "ROAD", assetName: "Northern ring road", description: "Surface cracking and minor slumping.", severity: "MINOR", affectedUnits: 3, estimatedLossMusd: 1.1, owner: "Karim Haddad", recoveryInDays: 20, verify: false, progress: 0 },
      ],
      report: true,
    },
    {
      sourceEventId: "demo-wildfire-laconia",
      hazardType: "wildfires",
      title: "Wildfire front in the Laconia hills",
      description:
        "Fast-moving fire driven by a strong northerly wind, threatening three villages. Demo scenario generated by the TerraScope demo seeder.",
      latitude: 36.95,
      longitude: 22.4,
      severity: "HIGH",
      owner: "Sofia Papadaki",
      statusFlow: ["RESPONDING"],
      notes: [
        "Air support requested. Two water-bombing aircraft are on station.",
        "Wind forecast to shift west after 18:00. Eastern villages put on standby.",
      ],
      actions: [
        { title: "Prepare a firebreak along the eastern ridge", priority: "CRITICAL", owner: "Marco Ferri", dueInHours: 3, status: "IN_PROGRESS" },
        { title: "Notify residents of the three villages", priority: "HIGH", owner: "Sofia Papadaki", dueInHours: -1, status: "DONE" },
        { title: "Stage tankers at the coastal road junction", priority: "MEDIUM", owner: "Karim Haddad", dueInHours: 5, status: "TODO" },
      ],
      resources: [
        { category: "EQUIPMENT", name: "Fire tankers", priority: "CRITICAL", requestedQuantity: 14, unit: "vehicles", allocatedQuantity: 9, owner: "Karim Haddad" },
        { category: "PERSONNEL", name: "Hand crews", priority: "HIGH", requestedQuantity: 20, unit: "crews", allocatedQuantity: 20, owner: "Marco Ferri" },
        { category: "TRANSPORT", name: "Evacuation buses", priority: "MEDIUM", requestedQuantity: 8, unit: "buses", allocatedQuantity: 2, owner: "Sofia Papadaki" },
      ],
      zones: [
        { name: "Agios Nikolaos village", description: "Closest settlement to the fire front.", priority: "CRITICAL", populationAtRisk: 1800, evacuatedPopulation: 1100, owner: "Sofia Papadaki" },
        { name: "Mountain hamlets", description: "Scattered homes along the ridge road.", priority: "HIGH", populationAtRisk: 650, evacuatedPopulation: 0, owner: "Marco Ferri" },
      ],
      shelters: [
        { name: "Sparta indoor arena", address: "Olympic Street 3", capacity: 900, occupancy: 520, owner: "Sofia Papadaki" },
      ],
      lifelines: [
        { category: "POWER", name: "Ridge transmission line", description: "Switched off as a precaution.", priority: "HIGH", status: "OUTAGE", affectedPopulation: 2400, owner: "Karim Haddad", etaInHours: 14 },
        { category: "WATER", name: "Village reservoir pumps", description: "Reduced pressure while tankers refill.", priority: "MEDIUM", status: "DEGRADED", affectedPopulation: 1800, owner: "Karim Haddad", etaInHours: 9 },
      ],
      damage: [
        { category: "RESIDENTIAL", assetName: "Outlying farmhouses", description: "Eight structures lost on the eastern slope.", severity: "MAJOR", affectedUnits: 8, estimatedLossMusd: 2.4, owner: "Sofia Papadaki", recoveryInDays: 150, verify: true, progress: 0 },
        { category: "UTILITY", assetName: "Telecom relay station", description: "Antenna mast damaged by heat.", severity: "MODERATE", affectedUnits: 1, estimatedLossMusd: 0.6, owner: "Youssef Trabelsi", recoveryInDays: 21, verify: false, progress: 0 },
      ],
      report: false,
    },
    {
      sourceEventId: "demo-volcano-etna",
      hazardType: "volcanoes",
      title: "Mount Etna lava fountaining and ash plume",
      description:
        "Paroxysmal phase with an ash plume drifting south-east. Airport operations are being monitored. Demo scenario generated by the TerraScope demo seeder.",
      latitude: 37.75,
      longitude: 14.99,
      severity: "MODERATE",
      owner: "Marco Ferri",
      statusFlow: ["MONITORING"],
      notes: [
        "Ash advisory issued for the south-east sector.",
        "Observatory reports tremor amplitude decreasing over the last two hours.",
      ],
      actions: [
        { title: "Coordinate with the airport on runway ash inspections", priority: "HIGH", owner: "Marco Ferri", dueInHours: 4, status: "IN_PROGRESS" },
        { title: "Distribute dust masks to the southern villages", priority: "MEDIUM", owner: "Sofia Papadaki", dueInHours: 10, status: "TODO" },
        { title: "Review road-clearing contracts for ash removal", priority: "LOW", owner: "Karim Haddad", dueInHours: 30, status: "TODO" },
      ],
      resources: [
        { category: "EQUIPMENT", name: "Street sweepers", priority: "MEDIUM", requestedQuantity: 10, unit: "units", allocatedQuantity: 4, owner: "Karim Haddad" },
        { category: "MEDICAL", name: "Respiratory protection kits", priority: "HIGH", requestedQuantity: 5000, unit: "kits", allocatedQuantity: 1200, owner: "Sofia Papadaki" },
      ],
      zones: [],
      shelters: [],
      lifelines: [
        { category: "TRANSPORT", name: "Catania airport", description: "Reduced departures while the runway is inspected.", priority: "HIGH", status: "DEGRADED", affectedPopulation: 4000, owner: "Marco Ferri", etaInHours: 12 },
      ],
      damage: [],
      report: false,
    },
    {
      sourceEventId: "demo-storm-capbon",
      hazardType: "severe-storms",
      title: "Severe storm cell over the Cap Bon coast",
      description:
        "Heavy rainfall and coastal flooding. Water levels are now falling. Demo scenario generated by the TerraScope demo seeder.",
      latitude: 36.85,
      longitude: 10.9,
      severity: "HIGH",
      owner: "Karim Haddad",
      statusFlow: ["RESPONDING", "STABILIZED"],
      notes: [
        "Rainfall peaked at 90 mm in three hours. Low-lying roads closed.",
        "Water levels are falling. Pumping continues in the two worst-hit districts.",
      ],
      actions: [
        { title: "Pump out the flooded underpass", priority: "HIGH", owner: "Karim Haddad", dueInHours: -10, status: "DONE" },
        { title: "Re-open the coastal road after inspection", priority: "MEDIUM", owner: "Youssef Trabelsi", dueInHours: 6, status: "IN_PROGRESS" },
        { title: "Collect damage reports from market traders", priority: "LOW", owner: "Sofia Papadaki", dueInHours: 24, status: "TODO" },
      ],
      resources: [
        { category: "EQUIPMENT", name: "Portable pumps", priority: "HIGH", requestedQuantity: 12, unit: "pumps", allocatedQuantity: 12, owner: "Karim Haddad" },
        { category: "TRANSPORT", name: "High-clearance vehicles", priority: "MEDIUM", requestedQuantity: 6, unit: "vehicles", allocatedQuantity: 4, owner: "Youssef Trabelsi" },
      ],
      zones: [
        { name: "Low-lying coastal quarter", description: "Streets below the flood line.", priority: "HIGH", populationAtRisk: 3200, evacuatedPopulation: 3200, owner: "Karim Haddad" },
      ],
      shelters: [
        { name: "Primary school gymnasium", address: "School Lane 5", capacity: 400, occupancy: 140, owner: "Sofia Papadaki" },
      ],
      lifelines: [
        { category: "WATER", name: "Sewage pumping station", description: "Back in service after flooding.", priority: "HIGH", status: "RESTORED", affectedPopulation: 0, owner: "Karim Haddad" },
      ],
      damage: [
        { category: "COMMERCIAL", assetName: "Seafront market stalls", description: "Water damage to goods and electrical fittings.", severity: "MODERATE", affectedUnits: 60, estimatedLossMusd: 1.8, owner: "Sofia Papadaki", recoveryInDays: 25, verify: true, progress: 30 },
      ],
      report: false,
    },
    {
      sourceEventId: "demo-eq-crete",
      hazardType: "earthquakes",
      title: "M5.2 earthquake south of Heraklion",
      description:
        "Moderate offshore earthquake with limited damage. Response completed and the incident closed. Demo scenario generated by the TerraScope demo seeder.",
      latitude: 34.98,
      longitude: 25.1,
      severity: "MODERATE",
      owner: "Lena Voss",
      statusFlow: ["MONITORING", "RESPONDING", "STABILIZED"],
      notes: [
        "Shaking felt across the island. No casualties reported.",
        "Inspections completed on all public buildings. Incident ready to close.",
      ],
      actions: [
        { title: "Inspect public buildings in the coastal towns", priority: "HIGH", owner: "Lena Voss", dueInHours: -100, status: "DONE" },
        { title: "Publish the all-clear notice", priority: "MEDIUM", owner: "Sofia Papadaki", dueInHours: -90, status: "DONE" },
      ],
      resources: [
        { category: "PERSONNEL", name: "Structural inspection teams", priority: "HIGH", requestedQuantity: 6, unit: "teams", allocatedQuantity: 6, owner: "Lena Voss" },
      ],
      zones: [
        { name: "Southern harbour town", description: "Precautionary evacuation of waterfront blocks.", priority: "MEDIUM", populationAtRisk: 1200, evacuatedPopulation: 1200, owner: "Lena Voss" },
      ],
      shelters: [],
      lifelines: [
        { category: "POWER", name: "Coastal distribution line", description: "Short outage, fully restored.", priority: "MEDIUM", status: "RESTORED", affectedPopulation: 0, owner: "Karim Haddad" },
      ],
      damage: [
        { category: "PUBLIC_BUILDING", assetName: "Town hall annex", description: "Plaster and ceiling damage.", severity: "MINOR", affectedUnits: 1, estimatedLossMusd: 0.3, owner: "Lena Voss", recoveryInDays: 10, verify: true, progress: 100 },
      ],
      report: false,
      closeWhenSeeded: true,
    },
  ];

  return {
    alertRules,
    alertPoints,
    alertStatuses,
    watchlist,
    simulations,
    regions,
    incidents,
    at: (hours) => at(now, hours),
  };
}
