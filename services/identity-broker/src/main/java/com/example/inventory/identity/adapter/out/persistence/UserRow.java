package com.example.inventory.identity.adapter.out.persistence;

import java.time.Instant;

public record UserRow(
        long id,
        String email,
        String passwordHash,
        String displayName,
        long version,
        String status,
        Instant deactivatedAt) {}
