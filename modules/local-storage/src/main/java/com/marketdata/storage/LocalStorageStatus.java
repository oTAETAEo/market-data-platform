package com.marketdata.storage;

public record LocalStorageStatus(
        String mode,
        boolean persistent
) {
}
