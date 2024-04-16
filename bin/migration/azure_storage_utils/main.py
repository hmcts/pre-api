import os
import sys
import pandas as pd
from azure_storage_utils import AzureBlobStorageManager

root_folder = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
sys.path.append(root_folder)

from db_utils import DatabaseManager

storage_manager = AzureBlobStorageManager()


def connect_to_database():
    destination_db_name = os.environ.get('DESTINATION_DB_NAME')
    destination_db_user = os.environ.get('DESTINATION_DB_USER')
    destination_db_password = os.environ.get('DESTINATION_DB_PASSWORD')
    destination_db_host = os.environ.get('DESTINATION_DB_HOST')

    conn = DatabaseManager(
        database=destination_db_name,
        user=destination_db_user,
        password=destination_db_password,
        host=destination_db_host,
        port="5432",
    )
    return conn


def fetch_recordings(conn):
    fetch_query = "SELECT id FROM public.recordings"
    cur = conn.connection.cursor()
    cur.execute(fetch_query)
    recordings = cur.fetchall()
    return recordings


def save_recordings_to_csv(recordings):
    columns = ["Recording_ID", "Duration_seconds"]
    df = pd.DataFrame(columns=columns)

    for recording_id in recordings:
        df = pd.concat([df, pd.DataFrame([[recording_id[0], None]], columns=columns)], ignore_index=True)
    df.to_csv("recordings.csv", index=False, header=True)


def update_durations_in_dataframe():
    df = pd.read_csv("recordings.csv", names=["Recording_ID", "Duration_seconds"], skiprows=1) 
    formatted_durations = []
    for index, row in df.iterrows():
        recording_id = row["Recording_ID"]
        try:
            duration_str = storage_manager.get_recording_duration(recording_id)
            duration_td = pd.to_timedelta(duration_str)
            duration_seconds = duration_td.total_seconds()

            formatted_durations.append(duration_seconds)
        except Exception as e:
            formatted_durations.append(None)
            print(f"Error for recording ID {recording_id}: {str(e)}")

    df["Duration_seconds"] = formatted_durations
    df.to_csv("recordings.csv", index=False, mode="w")

def update_recordings_table(conn):
    try:
        df = pd.read_csv("recordings.csv")
        cur = conn.connection.cursor()
        for index, row in df.iterrows():
            recording_id = row["Recording_ID"]
            duration = row["Duration_seconds"]

            if pd.isna(duration):
                print(f"Skipping update for recording ID {recording_id}: Duration is NaN")
                continue

            update_query = f"UPDATE public.recordings SET duration = MAKE_INTERVAL(secs => {duration}) WHERE id = '{recording_id}'"
            try:
                cur.execute(update_query)
                conn.connection.commit()
            except Exception as e:
                print(f"Error updating recording ID {recording_id}: {e}")
                conn.connection.rollback()
    except Exception as e:
        print(f"Error reading CSV file: {e}")


def main():
    print("Fetching recording ids from database and saving to csv")
    conn = connect_to_database()
    recordings = fetch_recordings(conn)
    save_recordings_to_csv(recordings)

    print("Updating dataframe with recording duration values from storage")
    update_durations_in_dataframe()

    print("Inserting values to database")
    conn = connect_to_database()
    update_recordings_table(conn)

    print("Recording durations inserted to database.")


if __name__ == "__main__":
    main()
