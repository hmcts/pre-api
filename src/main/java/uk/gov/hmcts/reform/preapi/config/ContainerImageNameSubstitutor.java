package uk.gov.hmcts.reform.preapi.config;

import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.ImageNameSubstitutor;

public class ContainerImageNameSubstitutor extends ImageNameSubstitutor {

    private final DockerImageName hmctsPostgresDockerImage = DockerImageName
        // This is *not* the image used in production
        // This version is hosted on Docker hub, rather than in hmctsprod
        // Using here to avoid auth issues with testcontainers
        .parse("postgres:16-alpine")
        .asCompatibleSubstituteFor("postgres");

    @Override
    public DockerImageName apply(DockerImageName original) {

        if (original.asCanonicalNameString().contains("postgres")) {
            return hmctsPostgresDockerImage;
        }

        return original;
    }

    @Override
    protected String getDescription() {
        return "hmcts acr substitutor";
    }
}
