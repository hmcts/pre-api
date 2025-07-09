#!/usr/bin/env bash

set -e

psql -v ON_ERROR_STOP=1 --username postgres --dbname api <<-EOSQL
  CREATE EXTENSION IF NOT EXISTS pgcrypto;
  CREATE EXTENSION IF NOT EXISTS pg_trgm;
  SET TIME ZONE 'UTC';
EOSQL
