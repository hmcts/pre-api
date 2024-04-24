import psycopg2 
from datetime import datetime
import subprocess
import os

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

    def take_backup(self, backup_dir):
        dbname = self.connection.get_dsn_parameters()['dbname']
        user = self.connection.get_dsn_parameters()['user']
        host = self.connection.get_dsn_parameters()['host']
        port = self.connection.get_dsn_parameters()['port']

        timestamp = datetime.now().strftime("%d-%m-%Y_%H:%M")
        backup_file = f"{backup_dir}/{timestamp}_{dbname}.sql"
        
        pg_dump_cmd = [
            "pg_dump",
            "-d", dbname,
            "-U", user,
            "-h", host,
            "-p", port,
            "-W",
            "-f", backup_file,
        ]

        try:
            subprocess.run(pg_dump_cmd, check=True)
            print(f"Backup successfully created at {backup_file}")
        except Exception as e:
            print(f"Error: Backup failed - {e}")
       


