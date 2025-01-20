#!/usr/bin/env bash

set -e

psql -v ON_ERROR_STOP=1 --username pre --set USERNAME=pre <<-EOSQL
  CREATE EXTENSION IF NOT EXISTS pgcrypto;
EOSQL
