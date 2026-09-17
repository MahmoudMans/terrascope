"use client";

import {
  useEffect,
  useRef,
  useState,
} from "react";

import type {
  LayerGroup,
  Map as LeafletMap,
} from "leaflet";

import type {
  EarthquakeEvent,
} from "@/types/earthquake";

import type {
  VolcanoEvent,
} from "@/types/volcano";

type EarthquakeMapProps = {
  events: EarthquakeEvent[];
  volcanoEvents: VolcanoEvent[];

  earthquakesVisible: boolean;
  volcanoesVisible: boolean;

  selectedEarthquake:
    EarthquakeEvent | null;

  selectedVolcano:
    VolcanoEvent | null;

  onSelectEarthquake:
    (event: EarthquakeEvent) => void;

  onSelectVolcano:
    (event: VolcanoEvent) => void;
};

function markerColor(
  magnitude: number,
): string {
  if (magnitude >= 7) {
    return "#ff3d71";
  }

  if (magnitude >= 6) {
    return "#ff7357";
  }

  if (magnitude >= 5) {
    return "#f4b860";
  }

  if (magnitude >= 4) {
    return "#79d4a7";
  }

  return "#45b7d1";
}

function escapeHtml(
  value: string,
): string {
  return value
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#039;");
}

export function EarthquakeMap({
  events,
  volcanoEvents,
  earthquakesVisible,
  volcanoesVisible,
  selectedEarthquake,
  selectedVolcano,
  onSelectEarthquake,
  onSelectVolcano,
}: EarthquakeMapProps) {
  const containerRef =
    useRef<HTMLDivElement | null>(
      null,
    );

  const mapRef =
    useRef<LeafletMap | null>(
      null,
    );

  const earthquakeLayerRef =
    useRef<LayerGroup | null>(
      null,
    );

  const volcanoLayerRef =
    useRef<LayerGroup | null>(
      null,
    );

  const leafletRef =
    useRef<
      typeof import("leaflet") | null
    >(null);

  const [ready, setReady] =
    useState(false);

  useEffect(() => {
    if (
      containerRef.current === null
      || mapRef.current !== null
    ) {
      return;
    }

    let cancelled = false;

    void import("leaflet").then((L) => {
      if (
        cancelled
        || containerRef.current === null
        || mapRef.current !== null
      ) {
        return;
      }

      const map = L.map(
        containerRef.current,
        {
          center: [20, 0],
          zoom: 2,
          minZoom: 2,
          maxZoom: 12,
          zoomControl: false,
          worldCopyJump: true,
          attributionControl: true,
        },
      );

      L.tileLayer(
        "https://tile.openstreetmap.org/{z}/{x}/{y}.png",
        {
          maxZoom: 19,
          attribution:
            "&copy; OpenStreetMap contributors",
        },
      ).addTo(map);

      L.control
        .zoom({
          position: "bottomright",
        })
        .addTo(map);

      earthquakeLayerRef.current =
        L.layerGroup().addTo(map);

      volcanoLayerRef.current =
        L.layerGroup().addTo(map);

      leafletRef.current = L;
      mapRef.current = map;

      window.setTimeout(() => {
        map.invalidateSize();
      }, 0);

      setReady(true);
    });

    return () => {
      cancelled = true;

      if (mapRef.current !== null) {
        mapRef.current.remove();
      }

      mapRef.current = null;
      earthquakeLayerRef.current = null;
      volcanoLayerRef.current = null;
      leafletRef.current = null;
    };
  }, []);

  useEffect(() => {
    const L = leafletRef.current;

    const markerLayer =
      earthquakeLayerRef.current;

    if (
      !ready
      || L === null
      || markerLayer === null
    ) {
      return;
    }

    markerLayer.clearLayers();

    if (!earthquakesVisible) {
      return;
    }

    events.forEach((event) => {
      const color =
        markerColor(event.magnitude);

      const marker = L.circleMarker(
        [
          event.latitude,
          event.longitude,
        ],
        {
          radius: Math.min(
            14,
            Math.max(
              4.5,
              3
                + event.magnitude
                * 1.25,
            ),
          ),
          color,
          fillColor: color,
          fillOpacity: 0.72,
          opacity: 0.95,
          weight:
            selectedEarthquake?.id
              === event.id
              ? 3
              : 1.5,
        },
      );

      marker.bindTooltip(
        `
          <div class="map-tooltip">
            <strong>
              M${event.magnitude.toFixed(1)}
            </strong>
            <span>
              ${escapeHtml(event.place)}
            </span>
            <small>
              ${event.depthKm.toFixed(1)} km deep
            </small>
          </div>
        `,
        {
          direction: "top",
          opacity: 0.96,
        },
      );

      marker.on("click", () => {
        onSelectEarthquake(event);
      });

      marker.addTo(markerLayer);
    });
  }, [
    earthquakesVisible,
    events,
    onSelectEarthquake,
    ready,
    selectedEarthquake?.id,
  ]);

  useEffect(() => {
    const L = leafletRef.current;

    const markerLayer =
      volcanoLayerRef.current;

    if (
      !ready
      || L === null
      || markerLayer === null
    ) {
      return;
    }

    markerLayer.clearLayers();

    if (!volcanoesVisible) {
      return;
    }

    volcanoEvents.forEach((event) => {
      const selected =
        selectedVolcano?.id === event.id;

      const icon = L.divIcon({
        className: "volcano-div-icon",
        html: `
          <span class="
            volcano-map-marker
            ${
              selected
                ? "volcano-map-marker--selected"
                : ""
            }
          ">
            ▲
          </span>
        `,
        iconSize: [28, 28],
        iconAnchor: [14, 14],
      });

      const marker = L.marker(
        [
          event.latitude,
          event.longitude,
        ],
        {
          icon,
          keyboard: true,
        },
      );

      marker.bindTooltip(
        `
          <div class="map-tooltip">
            <strong>
              VOLCANO
            </strong>
            <span>
              ${escapeHtml(event.title)}
            </span>
            <small>
              ${escapeHtml(event.primarySource)}
            </small>
          </div>
        `,
        {
          direction: "top",
          opacity: 0.96,
        },
      );

      marker.on("click", () => {
        onSelectVolcano(event);
      });

      marker.addTo(markerLayer);
    });
  }, [
    onSelectVolcano,
    ready,
    selectedVolcano?.id,
    volcanoEvents,
    volcanoesVisible,
  ]);

  useEffect(() => {
    const target =
      selectedVolcano
      ?? selectedEarthquake;

    if (
      target === null
      || mapRef.current === null
    ) {
      return;
    }

    mapRef.current.flyTo(
      [
        target.latitude,
        target.longitude,
      ],
      Math.max(
        mapRef.current.getZoom(),
        4,
      ),
      {
        duration: 0.75,
      },
    );
  }, [
    selectedEarthquake,
    selectedVolcano,
  ]);

  const visibleCount =
    (
      earthquakesVisible
        ? events.length
        : 0
    )
    +
    (
      volcanoesVisible
        ? volcanoEvents.length
        : 0
    );

  return (
    <div className="map-shell">
      <div
        ref={containerRef}
        className="earthquake-map"
        aria-label="Global multi-hazard map"
      />

      {!ready && (
        <div className="map-loading">
          <div className="loading-orbit" />

          <span>
            Initializing Earth view
          </span>
        </div>
      )}

      <div className="map-overlay map-overlay--top">
        <span className="map-overlay__dot" />

        <span>
          {visibleCount} visible events
        </span>
      </div>

      <div className="map-legend">
        <span className="map-legend__title">
          Layers
        </span>

        <span>
          <i className="legend-dot legend-dot--minor" />
          Earthquake
        </span>

        <span>
          <i className="volcano-legend-symbol">
            ▲
          </i>
          Volcano
        </span>

        <span>
          <i className="legend-dot legend-dot--major" />
          M7+
        </span>
      </div>
    </div>
  );
}
