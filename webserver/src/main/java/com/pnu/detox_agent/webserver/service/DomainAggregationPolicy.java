package com.pnu.detox_agent.webserver.service;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;

final class DomainAggregationPolicy {

    private static final Set<String> MULTI_PART_PUBLIC_SUFFIXES = Set.of(
            "co.kr",
            "or.kr",
            "ac.kr",
            "go.kr",
            "co.jp",
            "co.uk",
            "org.uk",
            "com.au",
            "net.au",
            "org.au");

    private static final Set<String> EXCLUDED_EXACT_DOMAINS = Set.of(
            "jsdelivr.net",
            "tailwindcss.com",
            "cloudfront.net",
            "gstatic.com",
            "doubleclick.net",
            "google-analytics.com",
            "googletagmanager.com",
            "googleadservices.com",
            "segment.io",
            "sentry.io",
            "mixpanel.com",
            "amplitude.com",
            "crashlytics.com",
            "fastly.net");

    private static final Set<String> YOUTUBE_DOMAINS = Set.of(
            "youtube.com",
            "youtu.be",
            "ytimg.com",
            "ggpht.com",
            "youtube-nocookie.com",
            "googlevideo.com");

    private DomainAggregationPolicy() {
    }

    static Optional<String> toDisplayDomain(String rawDomain) {
        String normalized = normalizeHost(rawDomain);
        if (normalized.isBlank()) {
            return Optional.empty();
        }
        if (isExcluded(normalized)) {
            return Optional.empty();
        }
        if (normalized.equals("youtubei.googleapis.com")) {
            return Optional.of("youtube.com");
        }
        if (normalized.equals("youtube-ui.l.google.com") || normalized.startsWith("youtube-ui.")) {
            return Optional.of("youtube.com");
        }

        String registrable = registrableDomain(normalized);
        if (YOUTUBE_DOMAINS.contains(registrable) || normalized.endsWith(".youtubei.googleapis.com")) {
            return Optional.of("youtube.com");
        }
        if (registrable.equals("googleapis.com") && normalized.startsWith("youtubei.")) {
            return Optional.of("youtube.com");
        }
        return Optional.of(registrable);
    }

    static String normalizeHost(String rawDomain) {
        if (rawDomain == null) {
            return "";
        }

        String normalized = rawDomain.trim().toLowerCase(Locale.ROOT);
        if (normalized.endsWith(".")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (normalized.startsWith("www.")) {
            normalized = normalized.substring(4);
        }
        return normalized;
    }

    private static boolean isExcluded(String normalized) {
        String registrable = registrableDomain(normalized);
        if (EXCLUDED_EXACT_DOMAINS.contains(registrable)) {
            return true;
        }
        if (normalized.endsWith(".cdn.mozilla.net")) {
            return true;
        }
        return normalized.contains(".telemetry.")
                || normalized.contains(".sync.")
                || normalized.contains(".update.")
                || normalized.contains(".metrics.")
                || normalized.contains(".analytics.")
                || normalized.startsWith("telemetry.")
                || normalized.startsWith("sync.")
                || normalized.startsWith("update.")
                || normalized.startsWith("metrics.")
                || normalized.startsWith("analytics.")
                || normalized.endsWith(".services.mozilla.com")
                || normalized.endsWith(".mozilla.net");
    }

    private static String registrableDomain(String normalized) {
        if (normalized.isBlank()) {
            return normalized;
        }
        if (isIpv4(normalized) || !normalized.contains(".")) {
            return normalized;
        }

        String[] labels = normalized.split("\\.");
        if (labels.length < 2) {
            return normalized;
        }

        String publicSuffix = labels[labels.length - 2] + "." + labels[labels.length - 1];
        if (labels.length >= 3 && MULTI_PART_PUBLIC_SUFFIXES.contains(publicSuffix)) {
            return labels[labels.length - 3] + "." + publicSuffix;
        }
        return publicSuffix;
    }

    private static boolean isIpv4(String candidate) {
        String[] parts = candidate.split("\\.");
        if (parts.length != 4) {
            return false;
        }
        for (String part : parts) {
            if (part.isBlank() || part.length() > 3) {
                return false;
            }
            try {
                int value = Integer.parseInt(part);
                if (value < 0 || value > 255) {
                    return false;
                }
            } catch (NumberFormatException ex) {
                return false;
            }
        }
        return true;
    }
}
