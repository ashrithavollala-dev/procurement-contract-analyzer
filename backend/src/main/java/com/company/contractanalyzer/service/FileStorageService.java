package com.company.contractanalyzer.service;

import java.nio.file.Path;
import java.util.UUID;

/** Stores original PDFs under UPLOAD_DIR as {contractId}.pdf. */
public interface FileStorageService {

    /** Writes the file atomically and returns the storage key (relative path) to persist. */
    String store(UUID contractId, byte[] content);

    byte[] read(String storageKey);

    /** Resolves a storage key to an absolute path inside the upload directory. */
    Path resolve(String storageKey);

    /** @return true if a file was deleted, false if it did not exist */
    boolean delete(String storageKey);
}
