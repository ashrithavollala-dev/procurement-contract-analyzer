package com.company.contractanalyzer.service.impl;

import com.company.contractanalyzer.config.AppProperties;
import com.company.contractanalyzer.service.FileStorageService;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Local-disk storage. A Docker named volume is mounted at UPLOAD_DIR. Files are written to a
 * temporary name and then moved into place, so a crash never leaves a half-written PDF under
 * its final name. Keys are resolved and checked to stay inside the upload directory.
 */
@Service
public class LocalFileStorageService implements FileStorageService {

    private static final Logger log = LoggerFactory.getLogger(LocalFileStorageService.class);

    private final Path root;

    public LocalFileStorageService(AppProperties properties) {
        this.root = Path.of(properties.getUpload().getDir()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot create upload directory " + root, e);
        }
    }

    @Override
    public String store(UUID contractId, byte[] content) {
        String key = contractId + ".pdf";
        Path target = resolve(key);
        Path temp = root.resolve(contractId + ".pdf.part");
        try {
            Files.write(temp, content);
            try {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return key;
        } catch (IOException e) {
            deleteQuietly(temp);
            throw new UncheckedIOException("Failed to store uploaded file for contract " + contractId, e);
        }
    }

    @Override
    public byte[] read(String storageKey) {
        try {
            return Files.readAllBytes(resolve(storageKey));
        } catch (IOException e) {
            throw new UncheckedIOException("Stored file is missing or unreadable: " + storageKey, e);
        }
    }

    @Override
    public Path resolve(String storageKey) {
        Path resolved = root.resolve(storageKey).normalize();
        if (!resolved.startsWith(root) || resolved.equals(root)) {
            throw new IllegalArgumentException("Invalid storage key");
        }
        return resolved;
    }

    @Override
    public boolean delete(String storageKey) {
        try {
            return Files.deleteIfExists(resolve(storageKey));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to delete stored file " + storageKey, e);
        }
    }

    private void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.warn("Could not remove temporary upload file {}", path.getFileName());
        }
    }
}
