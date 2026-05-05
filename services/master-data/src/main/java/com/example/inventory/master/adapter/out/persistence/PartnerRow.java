package com.example.inventory.master.adapter.out.persistence;

public record PartnerRow(
        long id, String code, String name, String partnerType, String contactEmail, long version) {}
