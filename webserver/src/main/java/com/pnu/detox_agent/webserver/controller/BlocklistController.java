package com.pnu.detox_agent.webserver.controller;

import com.pnu.detox_agent.webserver.dto.blocklist.BlockDomainRequestDto;
import com.pnu.detox_agent.webserver.service.BlocklistService;
import java.security.Principal;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/blocklist")
public class BlocklistController {

    private final BlocklistService blocklistService;

    public BlocklistController(BlocklistService blocklistService) {
        this.blocklistService = blocklistService;
    }

    @GetMapping
    public Flux<String> listBlockedDomains(Principal principal) {
        return blocklistService.getBlockedDomains(principal.getName());
    }

    @PostMapping
    public Mono<ResponseEntity<Void>> blockDomain(
            Principal principal,
            @RequestBody BlockDomainRequestDto request) {
        return blocklistService.blockDomain(principal.getName(), request.domain())
                .map(ignored -> ResponseEntity.ok().build());
    }

    @DeleteMapping("/{domain}")
    public Mono<ResponseEntity<Void>> unblockDomain(
            Principal principal,
            @PathVariable("domain") String domain) {
        return blocklistService.unblockDomain(principal.getName(), domain)
                .map(ignored -> ResponseEntity.noContent().build());
    }
}
