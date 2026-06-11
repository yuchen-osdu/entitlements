# GC Entitlements Bootstrap

This directory contains the bootstrap script for setting up the Entitlements Service on Google Cloud.

## Required Environment Variables

The following environment variables must be set before running the bootstrap script:

```bash
DATA_PARTITION_ID          # The data partition identifier
ENTITLEMENTS_HOST          # The entitlements service host URL
PROJECT_ID                 # The Google Cloud project ID
AIRFLOW_COMPOSER_EMAIL     # The Airflow Composer service account email
```

## Usage

1. Set all required environment variables
2. Run the bootstrap script:

   ```bash
   ./bootstrap.sh
   ```

The script will:

- Validate all required environment variables are set
- Bootstrap the system partition with service principal mappings
- Bootstrap the specified data partition with the same mappings
- Create the necessary configuration files and make API calls to provision the entitlements
