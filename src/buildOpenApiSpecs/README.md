# Open API Specs Publisher

This directory contains a task to generates and save the PRE service's Open API specs in the `specs` directory.

It is used with Github Actions workflows: see `.github/workflows`

How to run:

`./gradlew buildOpenApiSpecs --tests 'uk.gov.hmcts.reform.preapi.openapi.OpenAPIPublisher'`
