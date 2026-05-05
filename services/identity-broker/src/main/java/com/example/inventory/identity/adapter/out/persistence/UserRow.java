package com.example.inventory.identity.adapter.out.persistence;

public record UserRow(
        long id, String email, String passwordHash, String displayName, long version) {}
