package com.example.inventory.core.domain.model;

/** 拠点(倉庫・店舗)識別子。 */
public record LocationId(String value) {

    public LocationId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("LocationId を空にすることはできません");
        }
    }
}
