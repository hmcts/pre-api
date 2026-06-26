echo "Creating database schemas"
cat src/main/resources/db/migration/*.sql  | docker exec -i $(docker container ls  | grep 'pre-api-db' | awk '{print $1}') psql -U pre -d api

echo "Unzipping folder with SQL files"
tar -xzvf docker/database/local/local_db_data.tar.gz -C docker/database/local/

echo "Piping SQL commands to PostgreSQL Docker container"
cat docker/database/local/data/*/*.sql | docker exec -i $(docker container ls  | grep 'pre-api-db' | awk '{print $1}') psql -U pre -d api

echo "Removing SQL files"
rm -rf docker/database/local/data

echo "Finished"
