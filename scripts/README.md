# MediaKind Asset Copy Script

This script migrates assets from an old MediaKind system to a new MediaKind system within the same environment.

## Prerequisites

- Python 3.7 or higher
- MediaKind API Bearer Token

## Setup Instructions

### 1. Create a Python Virtual Environment

Navigate to the scripts directory and create a virtual environment:

```bash
cd scripts
python3 -m venv venv
```

### 2. Activate the Virtual Environment

**On macOS/Linux:**
```bash
source venv/bin/activate
```

**On Windows:**
```bash
venv\Scripts\activate
```

### 3. Install Dependencies

With the virtual environment activated, install the required packages:

```bash
pip install -r requirements.txt
```

### 4. Configure Bearer Token

Open `mk-asset-copy-script.py` and populate the `BEARER_TOKEN` variable with your MediaKind API bearer token:

```python
BEARER_TOKEN = "your-mediakind-bearer-token-here"
```

**⚠️ Important:** Never commit your bearer token to version control. Keep it secure and private.

## Usage

### Basic Usage

```bash
python mk-asset-copy-script.py ENVIRONMENT [IGNORE_MIGRATED]
```

### Arguments

- `ENVIRONMENT` - Target environment (dev, test, stg, demo, prod)
  - Assets will be copied from old system to new system within the same environment
  - This prevents accidental cross-environment copying
- `IGNORE_MIGRATED` - Optional: 'true' to skip already migrated assets (default: false)

### Examples

Migrate all assets in the dev environment:
```bash
python mk-asset-copy-script.py dev
```

Migrate only non-migrated assets in the test environment:
```bash
python mk-asset-copy-script.py test true
```

## What the Script Does

The script performs the following operations within the specified environment:

1. **Assets Migration**: Copies all media assets from the old MediaKind system to the new system
   - Adds labels to track migration status (`isMigrated`, `created`, `lastModified`)
   - Updates source assets with `hasBeenMigrated` label after successful migration
   - Supports pagination for large asset collections (>1000 assets)

2. **Streaming Policies**: Copies all streaming policies

3. **Content Key Policies**: Copies all content key policies

## Environment Mappings

The script maps environments to the following MediaKind projects:

| Environment | Old System          | New System    |
|-------------|---------------------|---------------|
| dev         | PRE-MEDIAKIND-DEV   | pre-mkio-dev  |
| test        | pre-mediakind-test  | pre-mkio-test |
| stg         | pre-mediakind-stg   | pre-mkio-stg  |
| demo        | pre-mediakind-demo  | pre-mkio-demo |
| prod        | pre-mediakind-prod  | pre-mkio-prod |

## Error Logging

Failed operations are logged to `error_log.txt` in the same directory.

## Safety Features

- **Environment Isolation**: Single environment parameter prevents accidental cross-environment copying
- **Process Limit**: Built-in safety limit of 20,000 assets per run
- **Migration Tracking**: Labels assets to avoid duplicate processing
- **Error Logging**: Comprehensive error logging for troubleshooting

## Deactivating the Virtual Environment

When you're done, deactivate the virtual environment:

```bash
deactivate
```

## Troubleshooting

### Import Errors
If you see `ModuleNotFoundError: No module named 'requests'`, ensure:
1. Your virtual environment is activated
2. You've run `pip install -r requirements.txt`

### Authentication Errors
If you receive 401/403 errors:
1. Verify your bearer token is valid
2. Check that your token has appropriate permissions for the target environment

### No Assets Migrated
If assets aren't being migrated:
1. Check that assets exist in the source environment
2. If using `IGNORE_MIGRATED=true`, verify there are unmigrated assets
3. Review `error_log.txt` for specific error messages

