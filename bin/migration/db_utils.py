import psycopg2 
import subprocess
import os
from datetime import datetime
from azure.storage.blob import BlobServiceClient, BlobClient, ContainerClient

class DatabaseManager:
    def __init__(self, database, user, password, host, port):
        self.connection = psycopg2.connect(
            database=database,
            user=user,
            password=password,
            host=host,
            port=port
        )
        self.cursor = self.connection

    def execute_query(self, query, params=None):
        self.cursor.execute(query, params)
        return self.cursor.fetchall()  
    
    def close_connection(self):
        self.cursor.close()
        self.connection.close()

    def take_backup(self, connection_str):
        dbname = self.connection.get_dsn_parameters()['dbname']
        user = self.connection.get_dsn_parameters()['user']
        host = self.connection.get_dsn_parameters()['host']
        port = self.connection.get_dsn_parameters()['port']

        container_name = "backups"
        timestamp = datetime.now().strftime("%d-%m-%Y_%H:%M")
        file_path = f"{timestamp}_{dbname}.sql"
        
        pg_dump_cmd = [
            "pg_dump",
            "-d", dbname,
            "-U", user,
            "-h", host,
            "-p", port,
            "-W",
            "-f", file_path,
        ]

        try:
            subprocess.run(pg_dump_cmd, check=True)
            self._upload_backup_to_blob_storage(connection_str, container_name, file_path)
            print(f"Backup successfully created at {container_name}/{file_path}")
        except Exception as e:
            print(f"Error: Backup failed - {e}")

    def _upload_backup_to_blob_storage(self, connection_string, container_name, backup_file_path):
        try:            
            blob_service_client = BlobServiceClient.from_connection_string(connection_string)
            container_client = blob_service_client.get_container_client(container_name)

            with open(backup_file_path, "rb") as data:
                blob_client = container_client.upload_blob(name=os.path.basename(backup_file_path), data=data)

            print(f"Backup uploaded to Azure Blob Storage successfully.")
        except Exception as e:
            print(f"Error uploading backup to Azure Blob Storage: {e}")
       


