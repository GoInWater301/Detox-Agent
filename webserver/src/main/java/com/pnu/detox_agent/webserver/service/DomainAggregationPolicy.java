package com.pnu.detox_agent.webserver.service;

import java.util.Locale;
import java.util.Optional;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.Comparator;

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
            "leeswallow.click",
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

    private static final Set<String> SHARED_INFRASTRUCTURE_ROOTS = Set.of(
            "cloudfront.net",
            "fastly.net",
            "akamaihd.net",
            "edgekey.net",
            "edgesuite.net");

    private static final Map<String, ServiceDefinition> SERVICE_DEFINITIONS = serviceDefinitions();

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
        Optional<String> serviceDomain = findServiceDefinition(normalized)
                .map(ServiceDefinition::canonicalDomain);
        if (serviceDomain.isPresent()) {
            return serviceDomain;
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

    static String toBlockedServiceDomain(String rawDomain) {
        String normalized = normalizeHost(rawDomain);
        if (normalized.isBlank()) {
            return normalized;
        }

        return findServiceDefinition(normalized)
                .map(ServiceDefinition::canonicalDomain)
                .orElseGet(() -> {
                    String registrable = registrableDomain(normalized);
                    if (SHARED_INFRASTRUCTURE_ROOTS.contains(registrable)) {
                        return normalized;
                    }
                    return registrable;
                });
    }

    static Set<String> toBlockTargets(String rawDomain) {
        String normalized = toBlockedServiceDomain(rawDomain);
        if (normalized.isBlank()) {
            return Set.of();
        }

        ServiceDefinition definition = SERVICE_DEFINITIONS.get(normalized);
        if (definition == null || definition.blockTargets().isEmpty()) {
            return Set.of(normalized);
        }

        LinkedHashSet<String> targets = new LinkedHashSet<>();
        targets.add(normalized);
        targets.addAll(definition.blockTargets());
        return Set.copyOf(targets);
    }

    private static Map<String, ServiceDefinition> serviceDefinitions() {
        Map<String, ServiceDefinition> definitions = new LinkedHashMap<>();
        definitions.put("youtube.com", service(
                "youtube.com",
                Set.of(
                        "youtube.com",
                        "youtu.be",
                        "ytimg.com",
                        "ggpht.com",
                        "youtube-nocookie.com",
                        "googlevideo.com",
                        "youtubei.googleapis.com"),
                Set.of(
                        "youtube.com",
                        "youtu.be",
                        "ytimg.com",
                        "ggpht.com",
                        "youtube-nocookie.com",
                        "googlevideo.com",
                        "youtubei.googleapis.com")));
        definitions.put("instagram.com", service(
                "instagram.com",
                Set.of("instagram.com", "cdninstagram.com"),
                Set.of("instagram.com", "cdninstagram.com")));
        definitions.put("facebook.com", service(
                "facebook.com",
                Set.of("facebook.com", "fbcdn.net", "fbsbx.com", "messenger.com", "m.me"),
                Set.of("facebook.com", "fbcdn.net", "fbsbx.com", "messenger.com", "m.me")));
        definitions.put("tiktok.com", service(
                "tiktok.com",
                Set.of("tiktok.com", "tiktokcdn.com", "tiktokv.com", "musical.ly", "byteoversea.com", "ibytedtos.com"),
                Set.of("tiktok.com", "tiktokcdn.com", "tiktokv.com", "musical.ly", "byteoversea.com", "ibytedtos.com")));
        definitions.put("netflix.com", service(
                "netflix.com",
                Set.of("netflix.com", "nflxvideo.net", "nflximg.net", "nflximg.com", "nflxso.net"),
                Set.of("netflix.com", "nflxvideo.net", "nflximg.net", "nflximg.com", "nflxso.net")));
        definitions.put("x.com", service(
                "x.com",
                Set.of("x.com", "twitter.com", "twimg.com"),
                Set.of("x.com", "twitter.com", "twimg.com")));
        definitions.put("twitch.tv", service(
                "twitch.tv",
                Set.of("twitch.tv", "jtvnw.net", "ttvnw.net", "twitchcdn.net"),
                Set.of("twitch.tv", "jtvnw.net", "ttvnw.net", "twitchcdn.net")));
        definitions.put("discord.com", service(
                "discord.com",
                Set.of("discord.com", "discord.gg", "discordapp.com", "discordapp.net"),
                Set.of("discord.com", "discord.gg", "discordapp.com", "discordapp.net")));
        return Map.copyOf(definitions);
    }

    private static ServiceDefinition service(String canonicalDomain, Set<String> matchDomains, Set<String> blockTargets) {
        return new ServiceDefinition(canonicalDomain, Set.copyOf(matchDomains), Set.copyOf(blockTargets));
    }

    private static Optional<ServiceDefinition> findServiceDefinition(String normalized) {
        return SERVICE_DEFINITIONS.values().stream()
                .filter(definition -> matchesService(normalized, definition))
                .sorted(Comparator.comparingInt((ServiceDefinition definition) -> definition.canonicalDomain().length()).reversed())
                .findFirst();
    }

    private static boolean matchesService(String normalized, ServiceDefinition definition) {
        if (matchesAnySuffix(normalized, definition.matchDomains())) {
            return true;
        }
        return definition.canonicalDomain().equals("youtube.com")
                && (normalized.endsWith(".youtubei.googleapis.com")
                        || (registrableDomain(normalized).equals("googleapis.com") && normalized.startsWith("youtubei."))
                        || normalized.equals("youtube-ui.l.google.com")
                        || normalized.startsWith("youtube-ui."));
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

    static String registrableDomain(String normalized) {
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

    private static boolean matchesSuffix(String domain, String suffix) {
        return domain.equals(suffix) || domain.endsWith("." + suffix);
    }

    private static boolean matchesAnySuffix(String domain, Set<String> suffixes) {
        return suffixes.stream().anyMatch(suffix -> matchesSuffix(domain, suffix));
    }

    private record ServiceDefinition(
            String canonicalDomain,
            Set<String> matchDomains,
            Set<String> blockTargets) {
    }
}
