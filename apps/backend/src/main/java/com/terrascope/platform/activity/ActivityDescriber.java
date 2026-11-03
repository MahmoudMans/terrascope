package com.terrascope.platform.activity;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class ActivityDescriber {

    private static final String API_PREFIX =
            "/api/v1/";

    private static final int MAX_SEGMENTS = 2;

    private static final Set<String> NAMESPACES =
            Set.of(
                    "admin",
                    "resilience-portfolio",
                    "governance-closure"
            );

    private static final Map<String, String> ACTIONS =
            Map.of(
                    "verify", "Verified",
                    "cancel", "Cancelled",
                    "assess", "Assessed",
                    "complete", "Completed",
                    "retire", "Retired",
                    "rotate", "Rotated",
                    "run", "Ran",
                    "analyze", "Analyzed",
                    "evaluate", "Evaluated"
            );

    private static final Map<String, String> ATTRIBUTES =
            Map.ofEntries(
                    Map.entry("status", "status"),
                    Map.entry("severity", "severity"),
                    Map.entry("owner", "owner"),
                    Map.entry("priority", "priority"),
                    Map.entry("progress", "progress"),
                    Map.entry("due-at", "due date"),
                    Map.entry("recovery-eta", "recovery ETA"),
                    Map.entry("restoration-eta", "restoration ETA"),
                    Map.entry("impact", "impact"),
                    Map.entry("occupancy", "occupancy"),
                    Map.entry("allocation", "allocation"),
                    Map.entry("schedule", "schedule"),
                    Map.entry("target", "target")
            );

    private static final Map<String, String> NOUNS =
            Map.of(
                    "damage", "damage assessment",
                    "lifeline", "lifeline asset",
                    "improvement", "improvement action",
                    "watchlist", "watchlist entry",
                    "sitrep report", "situation report",
                    "snapshot", "portfolio snapshot",
                    "objective", "resilience objective",
                    "review", "portfolio review",
                    "certificate", "closure certificate",
                    "signature", "closure signature"
            );

    private static final Map<String, Description> OVERRIDES =
            Map.ofEntries(
                    Map.entry(
                            "POST auth/login",
                            new Description("Signed in", "session", null)
                    ),
                    Map.entry(
                            "POST auth/password",
                            new Description("Changed their own password", "account", null)
                    ),
                    Map.entry(
                            "POST incidents",
                            new Description("Created incident", "incident", null)
                    ),
                    Map.entry(
                            "POST alerts/evaluate",
                            new Description("Evaluated alert rules", "alert", null)
                    ),
                    Map.entry(
                            "PATCH alerts/rules/{id}/enabled",
                            new Description("Turned an alert rule on or off", "alert rule", "id")
                    ),
                    Map.entry(
                            "POST admin/users",
                            new Description("Created a user account", "user", null)
                    ),
                    Map.entry(
                            "PATCH admin/users/{id}",
                            new Description("Updated a user account", "user", "id")
                    ),
                    Map.entry(
                            "POST admin/users/{id}/password",
                            new Description("Reset a user's password", "user", "id")
                    ),
                    Map.entry(
                            "POST resilience-portfolio/governance-closure/airgap/verify-envelope",
                            new Description("Verified an air-gap envelope", "airgap", null)
                    ),
                    Map.entry(
                            "POST resilience-portfolio/governance-closure/trust-lineage/verify-envelope",
                            new Description("Verified a trust lineage envelope", "trust lineage", null)
                    )
            );

    private static final Description FALLBACK =
            new Description("Changed data", "record", null);

    private ActivityDescriber() {
    }

    public record Description(
            String action,
            String entityType,
            String idVariable
    ) {
    }

    public static Description describe(
            String method,
            String route
    ) {
        if (
                method == null
                || route == null
                || !route.startsWith(API_PREFIX)
        ) {
            return FALLBACK;
        }

        String verb =
                method.toUpperCase(Locale.ROOT);

        String path =
                route.substring(API_PREFIX.length());

        Description override =
                OVERRIDES.get(verb + " " + path);

        if (override != null) {
            return override;
        }

        List<List<String>> chains =
                new ArrayList<>();

        List<String> current =
                new ArrayList<>();

        String firstVariable =
                null;

        boolean endsWithVariable =
                false;

        for (String segment : path.split("/")) {
            if (segment.isEmpty()) {
                continue;
            }

            if (
                    segment.startsWith("{")
                    && segment.endsWith("}")
            ) {
                if (firstVariable == null) {
                    firstVariable =
                            variableName(segment);
                }

                if (!current.isEmpty()) {
                    chains.add(current);
                }

                current = new ArrayList<>();
                endsWithVariable = true;

            } else {
                current.add(segment);
                endsWithVariable = false;
            }
        }

        if (!current.isEmpty()) {
            chains.add(current);
        }

        if (chains.isEmpty()) {
            return FALLBACK;
        }

        List<String> last =
                chains.get(chains.size() - 1);

        String tail =
                last.get(last.size() - 1);

        boolean nested =
                chains.size() > 1;

        String past =
                pastTense(verb);

        String preposition =
                preposition(verb);

        List<String> entityChain =
                chains.get(0);

        String action;

        if (
                !endsWithVariable
                && ACTIONS.containsKey(tail)
                && (last.size() > 1 || nested)
        ) {
            List<String> subject =
                    last.size() > 1
                            ? last.subList(0, last.size() - 1)
                            : chains.get(chains.size() - 2);

            action =
                    ACTIONS.get(tail)
                            + " "
                            + noun(subject);

            if (!nested && entityChain.size() > 1) {
                entityChain =
                        entityChain.subList(0, entityChain.size() - 1);
            }

        } else if (
                !endsWithVariable
                && ATTRIBUTES.containsKey(tail)
                && last.size() == 1
                && nested
        ) {
            action =
                    "Updated "
                            + noun(chains.get(chains.size() - 2))
                            + " "
                            + ATTRIBUTES.get(tail);

        } else if (nested) {
            action =
                    past
                            + " "
                            + noun(last)
                            + " "
                            + preposition
                            + " "
                            + noun(chains.get(chains.size() - 2));

        } else {
            action =
                    past
                            + " "
                            + noun(last);
        }

        return new Description(
                action,
                noun(entityChain),
                firstVariable
        );
    }

    private static String variableName(
            String segment
    ) {
        String inner =
                segment.substring(1, segment.length() - 1);

        int colon =
                inner.indexOf(':');

        return colon < 0
                ? inner
                : inner.substring(0, colon);
    }

    private static String pastTense(
            String verb
    ) {
        return switch (verb) {
            case "POST" -> "Added";
            case "PUT", "PATCH" -> "Updated";
            case "DELETE" -> "Removed";
            default -> "Changed";
        };
    }

    private static String preposition(
            String verb
    ) {
        return switch (verb) {
            case "POST" -> "to";
            case "DELETE" -> "from";
            default -> "on";
        };
    }

    private static String noun(
            List<String> chain
    ) {
        List<String> kept =
                new ArrayList<>();

        for (String segment : chain) {
            if (!NAMESPACES.contains(segment)) {
                kept.add(segment);
            }
        }

        if (kept.isEmpty()) {
            kept = chain;
        }

        if (kept.size() > MAX_SEGMENTS) {
            kept =
                    kept.subList(
                            kept.size() - MAX_SEGMENTS,
                            kept.size()
                    );
        }

        List<String> words =
                new ArrayList<>();

        for (String segment : kept) {
            words.add(
                    singular(
                            segment.replace('-', ' ')
                    )
            );
        }

        String phrase =
                String.join(" ", words)
                        .toLowerCase(Locale.ROOT)
                        .trim();

        if (phrase.isEmpty()) {
            return "record";
        }

        return NOUNS.getOrDefault(
                phrase,
                phrase
        );
    }

    private static String singular(
            String phrase
    ) {
        int space =
                phrase.lastIndexOf(' ');

        String head =
                space < 0
                        ? ""
                        : phrase.substring(0, space + 1);

        String word =
                phrase.substring(space + 1);

        if (
                word.length() > 4
                && word.endsWith("ies")
        ) {
            word =
                    word.substring(0, word.length() - 3)
                            + "y";

        } else if (word.endsWith("sses")) {
            word =
                    word.substring(0, word.length() - 2);

        } else if (
                word.length() > 3
                && word.endsWith("s")
                && !word.endsWith("ss")
                && !word.endsWith("us")
                && !word.endsWith("is")
        ) {
            word =
                    word.substring(0, word.length() - 1);
        }

        return head + word;
    }
}
