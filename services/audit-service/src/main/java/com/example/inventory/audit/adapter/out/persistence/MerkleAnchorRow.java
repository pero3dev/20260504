package com.example.inventory.audit.adapter.out.persistence;

import java.time.Instant;
import java.time.LocalDate;

public record MerkleAnchorRow(
        String tenantId,
        LocalDate anchorDate,
        String rootHash,
        long recordCount,
        long firstSequence,
        long lastSequence,
        Instant computedAt) {}
