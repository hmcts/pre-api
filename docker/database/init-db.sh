#!/usr/bin/env bash

set -e

psql -v ON_ERROR_STOP=1 --username postgres <<-EOSQL
  CREATE EXTENSION IF NOT EXISTS pgcrypto;
EOSQL
