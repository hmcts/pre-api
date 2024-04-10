import os
import sys
import pandas as pd
import argparse
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
    df = pd.read_csv("recordings.csv", names=["Recording_ID", "Duration_seconds"])
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
    df.to_csv("recordings.csv", index=False, mode="w", header=False)

def update_recordings_table(conn):
    df = pd.read_csv("recordings.csv")
    try:
        cur = conn.connection.cursor()
        for index, row in df.iterrows():
            recording_id = row["Recording_ID"]
            duration = row["Duration_seconds"]
            update_query = f"UPDATE public.recordings SET duration = MAKE_INTERVAL(secs => {duration}) WHERE id = '{recording_id}'"
            try:
                cur.execute(update_query)
                conn.connection.commit()
            except Exception as e:
                print(f"Error updating recording ID {recording_id}: {e}")
    except Exception as e:
        print(f"Error reading CSV file: {e}")

def print_message_start(step):
    print(f"Attempting to start step: {step}")

def print_message_finish(step):
    print(f"Finishing step: {step}")

def main(step):
    ############################################## OPEN Connection with VPN
    if step == "1":
        print_message_start(step)
        # connect to the database
        conn = connect_to_database()
        # Fetch recordings and save to CSV
        recordings = fetch_recordings(conn)
        # a csv with recording ids will be created
        save_recordings_to_csv(recordings)
        print_message_finish(step)
    
    ############################################## CLOSE Connection with VPN
    elif step == "2":
        print_message_start(step)
        # Update dataframe with duration values
        update_durations_in_dataframe()
        print_message_finish(step)

    # ############################################# OPEN Connection with VPN
    elif step == "3":
        print_message_start(step)
        # re-connect to the database
        conn = connect_to_database()
        # update table in db
        update_recordings_table(conn)
        print_message_finish(step)


    print("Durations appended to the DataFrame.")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(
        description="Script to fetch recording durations from AMS and perform database operations.")
    parser.add_argument("--step", dest="step", choices=["1", "2", "3"],
                        help="Specify which step to execute.")
    args = parser.parse_args()

    if args.step:
        main(args.step)
    else:
        print("""Please specify a step to execute using the '--step' argument.  e.g.:  
                    python main.py --step 1
                    python main.py --step 2
                    python main.py --step 3
                    (VPN should be open for Steps 1 & 3 and closed for Step 2)
              """)
