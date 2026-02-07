## Problem

This is for finding capture sessions which have finished being encoded by MediaKind, but have got stuck in PROCESSING or NO_RECORDING due to an API redeployment.

Initially the capture session is in PROCESSING but the end-of-day job may change it to NO_RECORDING if it cannot complete registration of the capture session.

The faulty code was introduced on Jul 18, 2024  ([commit link](https://github.com/hmcts/pre-api/commit/93af7f81d6fcbedcd8b1beb665d2a032e14321a4#diff-b87334772d1519884330257ada24f97ed4049e3909a77152b2e3c1d1835fc188))

## What is this?

The basic idea is to check if we have un-noticed capture sessions which have not been registered in the database. This will enable us to make the recordings visible, and will reassure the service owners that there are no missing recordings of past hearings.

1. Get all capture sessions with status NO_RECORDING from the database >> write to file
2. Get a list of containers from Azure Storage Account. Loop through them and check blobs. If a blob matches *missing capture session ID without hyphens* with resolution and ".mp4", then write the container name and track file name to an aggregated list in a file
3. Check if any capture session IDs from 1 have matching tracks from 2

The Azure Storage Account check is super slow, because we have about 20,000 storage containers. I don't think there's a way to do a single query for all the blobs, filtering by file name, so we have to loop through each container. For this reason I have split the loop into "begins with".

## Pre-reqs

These apply whether running through scripts or manually.

Make sure Global Protect is turned off and VPN is turned on.

You'll need Security Clearance and to be added to the DTS PRE Readers group via azure-access Github repo.

Need to get access packages (JIT) for the database.

https://myaccess.microsoft.com/@CJSCommonPlatform.onmicrosoft.com#/overview

## Running via script

This script is intended to be run from a local laptop, *not* in production or as a cron job.

Run the scripts in order.

The script for extracting from the database will need some input (which subscription, plus logging in).

The scripts will not work unless you are connected to F5 VPN.

## Running via manual process

#### To get a list of capture sessions from the database with NO_RECORDING status:

Get a database connection string with script

https://github.com/hmcts/pre-api/blob/master/scripts/Connect-To-PRE-Production-DB.sh

SQL:

```
SELECT id FROM capture_sessions
WHERE (origin::text = 'PRE'::text)
AND ingest_address LIKE '%mediakind.com%'
AND (deleted_at IS NULL)
AND ((finished_at > '2024-07-18 00:03:59.000')
AND (status::text = 'NO_RECORDING'::text) )
```

Note: we expect to have NO_RECORDING capture sessions for any that were scheduled and started by the cron job, but not used. This can happen for all sorts of legitimate reasons, e.g. hearing was adjourned.

Note 2: it is possible (although not advised) for bookings to have multiple capture sessions associated with them. A booking may have a successful capture session *and* a NO_RECORDING capture session.

#### Get a list of recordings available on AZ Storage Account

Run this on your Terminal:

```
az storage container list --account-name prefinalsaprod --auth-mode login --include-metadata false --query "[].{name:name}" --num-results 20000 --output tsv
```

The docs say you can do --num-results * for max, but I couldn't get it working, so I got an approximate rounded up count from Azure Storage Explorer.

This will return 10,0000s of storage containers. The names of the storage containers on the *final* storage account are the recording IDs. (This is why we need to check the blobs to match via the capture session IDs.)
