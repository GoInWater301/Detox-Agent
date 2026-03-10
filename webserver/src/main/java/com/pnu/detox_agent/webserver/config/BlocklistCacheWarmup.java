package com.pnu.detox_agent.webserver.config;

import com.pnu.detox_agent.webserver.service.BlocklistService;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class BlocklistCacheWarmup {

    private final BlocklistService blocklistService;

    public BlocklistCacheWarmup(BlocklistService blocklistService) {
        this.blocklistService = blocklistService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void warmup() {
        blocklistService.warmupRedisFromDatabase().subscribe();
    }
}
