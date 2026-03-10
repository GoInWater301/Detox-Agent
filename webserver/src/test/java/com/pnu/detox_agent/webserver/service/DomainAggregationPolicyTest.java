package com.pnu.detox_agent.webserver.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DomainAggregationPolicyTest {

    @Test
    void shouldNormalizeYoutubeRelatedDomainsIntoSingleService() {
        assertEquals("youtube.com", DomainAggregationPolicy.toDisplayDomain("www.youtube.com").orElseThrow());
        assertEquals("youtube.com", DomainAggregationPolicy.toDisplayDomain("i.ytimg.com").orElseThrow());
        assertEquals("youtube.com", DomainAggregationPolicy.toDisplayDomain("youtubei.googleapis.com").orElseThrow());
    }

    @Test
    void shouldExcludeInfrastructureDomainsFromDisplayAggregation() {
        assertTrue(DomainAggregationPolicy.toDisplayDomain("cdn.jsdelivr.net").isEmpty());
        assertTrue(DomainAggregationPolicy.toDisplayDomain("fonts.gstatic.com").isEmpty());
        assertTrue(DomainAggregationPolicy.toDisplayDomain("incoming.telemetry.mozilla.org").isEmpty());
        assertTrue(DomainAggregationPolicy.toDisplayDomain("firefox.settings.services.mozilla.com").isEmpty());
    }

    @Test
    void shouldKeepRegistrableDomainForNormalSites() {
        assertEquals("example.com", DomainAggregationPolicy.toDisplayDomain("api.example.com").orElseThrow());
        assertEquals("example.co.kr", DomainAggregationPolicy.toDisplayDomain("m.example.co.kr").orElseThrow());
    }
}
