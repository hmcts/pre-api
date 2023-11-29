FROM postgres:16-alpine

HEALTHCHECK --interval=10s --timeout=10s --retries=10 CMD psql -c 'select 1' -d api -U pre

EXPOSE 5432
