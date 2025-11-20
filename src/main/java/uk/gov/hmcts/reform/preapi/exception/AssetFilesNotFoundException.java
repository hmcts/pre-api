package uk.gov.hmcts.reform.preapi.exception;

import java.io.Serial;
import java.util.UUID;

public class AssetFilesNotFoundException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = 6579141837456533851L;

    public AssetFilesNotFoundException(UUID id) {
        super("Asset for capture session: " + id + " found with no .ism file");
    }

    public AssetFilesNotFoundException(String name) {
        super("Asset with name: " + name + " not found");
    }
}
