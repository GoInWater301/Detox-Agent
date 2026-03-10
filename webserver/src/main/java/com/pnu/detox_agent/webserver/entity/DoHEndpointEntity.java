package com.pnu.detox_agent.webserver.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Table("doh_endpoints")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DoHEndpointEntity {

    @Id
    private Long id;

    private String name;
    private String baseUrl;
    private String region;
    private int currentUsers;
    private int maxUsers;
    private boolean active;
}
