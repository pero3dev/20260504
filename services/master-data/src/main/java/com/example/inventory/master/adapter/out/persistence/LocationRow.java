package com.example.inventory.master.adapter.out.persistence;

public record LocationRow(
        long id, String code, String name, String addressLine, String locationType, long version) {}
