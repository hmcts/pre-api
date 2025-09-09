# Pre-Recorded Evidence API (pre-api)

The Pre-Recorded Evidence (PRE) system is a service for capturing video recorded hearings or testimony and securely sharing these recordings with advocates or playing them back in court.

## Purpose

pre-api is a Java Spring Boot application that serves as the backend API for:
- PRE PowerApps application (used by admin users)
- PRE Portal (used by judicial and professional users)

## Key Features

- Video recording capture and management
- Secure sharing of recordings to authorized users
- Court playback functionality
- User authentication via Azure B2C (Portal) and MS Teams (PowerApps)
- Integration with Media Kind for video processing
- Automated editing and processing workflows
- Batch processing for data migration and maintenance tasks

## System Integration

The API integrates with:
- Azure Blob Storage for media files
- Media Kind for video processing
- Power Platform (PowerApps, Power Flows, Dataverse)
- Azure API Management for authentication
- GovNotify for email notifications
- Slack for alerts and monitoring

## Documentation

API endpoints are documented in Swagger and available at `/swagger-ui/index.html` when running locally.