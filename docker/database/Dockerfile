FROM postgres:14-alpine

HEALTHCHECK --interval=10s --timeout=10s --retries=10 CMD psql -c 'select 1' -d pre -U pre

EXPOSE 5432
