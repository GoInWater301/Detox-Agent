package com.pnu.detox_agent.webserver.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Table("users")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserEntity {

    @Id
    private Long id;

    private String username;
    private String email;
    private String passwordHash;
    private String role;
    private String dohToken;
    private Long dohEndpointId;
    private boolean enabled;
    private LocalDateTime createdAt;
}
