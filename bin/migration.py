import psycopg2
import pytz
from datetime import datetime
import uuid
from collections import Counter

# region  --------------------------------------- Helper functions

# parse date string to timestamp UTC value
def parse_to_timestamp(input_text):
    if input_text:
        try:
            parsed_datetime = None
            # try parsing with different formats
            formats_to_try = ["%d/%m/%Y %H:%M:%S", "%d-%m-%Y %H:%M:%S"]
            for date_format in formats_to_try:
                try:
                    parsed_datetime = datetime.strptime(input_text, date_format)
                    break 
                except ValueError:
                    pass

            if parsed_datetime:
                uk_timezone = pytz.timezone('Europe/London')
                parsed_datetime = uk_timezone.localize(parsed_datetime)
                return parsed_datetime.strftime('%Y-%m-%d %H:%M:%S')
        except (ValueError, TypeError):
            pass
    # if input is invalid or empty, returning the current time in UK timezone
    return datetime.now(tz=pytz.timezone('Europe/London')).strftime('%Y-%m-%d %H:%M:%S')

# checks if a record has already been imported
def check_existing_record(table_name, field, record):
    query = f"SELECT EXISTS (SELECT 1 FROM public.{table_name} WHERE {field} = %s)"
    cur_destination.execute(query, (record,))
    return cur_destination.fetchone()[0]

# audit entry into database
def audit_entry_creation(table_name, record_id, record, created_at=None, created_by="Data Entry"):
    created_at = created_at or datetime.now()

    audit_entry = {
        "id": str(uuid.uuid4()),
        "table_name": table_name,
        "table_record_id": record_id,
        "source": "auto",
        "type": "create",
        "category": "data_migration",
        "activity": f"{table_name}_record_creation",
        "functional_area": "data_processing",
        "audit_details": f"Created {table_name}_record for: {record}",
        "created_by": created_by,
        "created_at": created_at,
    }
    cur_destination.execute(
        """
        INSERT INTO public.audits 
            (id, table_name, table_record_id, source, type, category, activity, functional_area, audit_details, created_by, created_at) 
        VALUES 
            (%(id)s, %(table_name)s, %(table_record_id)s, %(source)s, %(type)s, %(category)s, %(activity)s, %(functional_area)s, %(audit_details)s, %(created_by)s, %(created_at)s)
        """,
        audit_entry,
    )

# endregion

# region --------------------------------------- Opening DB connections

# connect to the source database - demo database
conn_source = psycopg2.connect(
    database="pre-pdb-demo",
    user="psqladmin",
    password="0q1V04AXX5_zbGjDsRi",
    host="pre-db-demo.postgres.database.azure.com",
    port="5432",
)
cur_source = conn_source.cursor()

# connect to the destination database - copy of dev database in dev server - to be updated to the new db
conn_destination = psycopg2.connect(
    database="dev-pre-copy",
    user="psqladmin",
    password="wKcBiJSmvrH0ni0czF7M",
    host="pre-db-dev.postgres.database.azure.com",
    port="5432",
)
cur_destination = conn_destination.cursor()

# endregion 

# region --------------------------------------- Data fetching from Demo DBgit

cur_source.execute("SELECT * from public.rooms")
source_room_data = cur_source.fetchall()

cur_source.execute("SELECT * from public.users")
source_user_data = cur_source.fetchall()

cur_source.execute('SELECT * from public.grouplist')
source_roles_data = cur_source.fetchall()

cur_source.execute('SELECT * from public.courts')
source_courts_data = cur_source.fetchall()

cur_source.execute("SELECT * FROM public.users WHERE prerole = 'Level 3' AND (sendinvite ILIKE 'true' OR invited ILIKE 'true')") # ASSUMPTION - verify
source_portal_users_data = cur_source.fetchall()

cur_source.execute("SELECT * FROM public.users WHERE prerole != 'Level 3'") # ASSUMPTION - verify
source_app_users_data = cur_source.fetchall()

cur_source.execute("SELECT * FROM public.cases WHERE caseref IS NOT NULL") #  only cases without NULL values for caseref (even duplicate caserefs as discussed )
source_cases_data = cur_source.fetchall()

cur_source.execute("SELECT * FROM public.cases")
source_bookings_data = cur_source.fetchall()

cur_source.execute("SELECT * FROM public.contacts")
source_participant_data = cur_source.fetchall()

cur_source.execute("SELECT DISTINCT ON (parentrecuid) * FROM public.recordings") 
source_capture_sessions_data = cur_source.fetchall()

cur_source.execute("SELECT * FROM public.recordings")
source_recording_data = cur_source.fetchall()

# endregion

# region 'Rooms' Table

batch_rooms_data = []

for source_room in source_room_data:
    room = source_room[0]

    if not check_existing_record('rooms', 'room', room):
        id = str(uuid.uuid4())  
        created_at = parse_to_timestamp(room[2])
        created_by = source_room[1]
        batch_rooms_data.append((id, room))

if batch_rooms_data:
    cur_destination.executemany(
        "INSERT INTO public.rooms (id, room) VALUES (%s, %s)",
        batch_rooms_data
    )

    for room in batch_rooms_data:
        audit_entry_creation(
            table_name="rooms",
            record_id=room[0],
            record=room[1],
            created_at=created_at,
            created_by=created_by,
        )

# endregion

# region 'Users' Table

batch_users_data = []

for user in source_user_data:
    id = user[0]

    if not check_existing_record('users', 'id', id):
        first_name = user[12]
        last_name = user[13]
        email = user[6]
        organisation = user[8]
        phone = user[7]
        created_at = parse_to_timestamp(user[15])
        modified_at = parse_to_timestamp(user[17])
        created_by = user[14]

        batch_users_data.append((
            id, first_name, last_name, email, organisation, phone, created_at, modified_at
        ))

if batch_users_data:
    cur_destination.executemany(
        """
        INSERT INTO public.users 
            (id, first_name, last_name, email, organisation, phone, created_at, modified_at)
        VALUES (%s, %s, %s, %s, %s, %s, %s, %s)
        """,
        batch_users_data
    )

    for user in batch_users_data:
        audit_entry_creation(
            table_name="users",
            record_id=user[0],
            record=f"{user[1]} {user[2]}",  
            created_at=user[6],
            created_by=created_by,
        )

# endregion

# region 'Roles' Table

allowed_roles = ['Level 1', 'Level 2', 'Level 3', 'Level 4', 'Super User']
batch_roles_data = []

for role in source_roles_data:
    id = role[0]
    name = role[1]

    if name in allowed_roles and not check_existing_record('roles', 'id', id):
        batch_roles_data.append((id, name))

if batch_roles_data:
    cur_destination.executemany(
        'INSERT INTO public.roles (id, name) VALUES (%s, %s)',
        batch_roles_data
    )

    for role in batch_roles_data:
        audit_entry_creation(
            table_name='roles',
            record_id=role[0],
            record=role[1]
        )

# endregion

# region 'Courts' Table

# Courts data -  https://cjscommonplatform-my.sharepoint.com/:x:/r/personal/lawrie_baber-scovell2_hmcts_net/_layouts/15/Doc.aspx?sourcedoc=%7B07C83A7F-EF01-4C78-9B02-AEDD443D15A1%7D&file=Courts%20PRE%20NRO.xlsx&wdOrigin=TEAMS-WEB.undefined_ns.rwc&action=default&mobileredirect=true
court_types = {
    'Reading Crown Court': ('crown','449','UKJ-South East (England)'),
    'Nottingham Crown Court': ('crown','444','UKF-East Midlands (England)'),
    'Mold Crown Court': ('crown','438','UKL-Wales'),
    'Liverpool Crown Court': ('crown','433','UKD-North West (England)'),
    'Leeds Youth Court': ('magistrate','429','UKE-Yorkshire and The Humber'),
    'Leeds Crown Court': ('crown','429','UKE-Yorkshire and The Humber'),
    'Kingston-upon-Thames Crown Court': ('crown','427','UKI-London'),
    'Durham Crown Court': ('crown','422','UKC-North East (England)'),
    'Birmingham': ('crown','404','UKG-West Midlands (England)')
}
batch_courts_data = []

for court in source_courts_data:
    id = court[0]
    court_info = court_types.get(court[1], ('crown', 'Unknown', 'Unknown'))
    court_type, location_code, _ = court_info
    name = court[1]

    if not check_existing_record('courts', 'id', id):
        batch_courts_data.append((id, court_type, name, location_code))

if batch_courts_data:
    cur_destination.executemany(
        'INSERT INTO public.courts (id, court_type, name, location_code) VALUES (%s, %s, %s, %s)',
        batch_courts_data
    )

    for court in batch_courts_data:
        audit_entry_creation(
            table_name='courts',
            record_id=court[0],
            record=court[2]
        )

# Inserting an 'Unknown' court type for records missing this info
default_court_id = str(uuid.uuid4())

if not check_existing_record('courts', 'name', 'Default Court'):
    default_court_id =str(uuid.uuid4())
    cur_destination.execute(
        'INSERT INTO public.courts (id, court_type, name, location_code) VALUES (%s, %s, %s, %s)',
        (default_court_id, 'crown', 'Default Court', 'default'),
    )

    audit_entry_creation(
        table_name='courts',
        record_id=default_court_id,
        record='Default Court'
    ) 

# endregion

# region 'Courtrooms' Table 

# CVP room data - https://tools.hmcts.net/confluence/display/S28/CVP+Guides#CVPGuides-CVPRooms-EnvironmentandCourtAllocation
courtroom_data = {
    "PRE001": "Leeds Crown Court",
    "PRE002": "Leeds Crown Court",
    "PRE003": "Leeds Crown Court",
    "PRE004": "Mold Crown Court",
    "PRE005": "Mold Crown Court",
    "PRE006": "Leeds Crown Court",
    "PRE007": "Leeds Crown Court",
    "PRE008": "Default Court",
    "PRE009": "Default Court",
    "PRE010": "Default Court",
    "PRE011": "Durham Crown Court",
    "PRE012": "Durham Crown Court",
    "PRE013": "Kingston-upon-Thames Crown Court",
    "PRE014": "Kingston-upon-Thames Crown Court",
    "PRE015": "Liverpool Crown Court",
    "PRE016": "Liverpool Crown Court",
    "PRE017": "Nottingham Crown Court",
    "PRE018": "Nottingham Crown Court",
    "PRE019": "Reading Crown Court",
    "PRE020": "Reading Crown Court"
}

batch_courtrooms_data = []

cur_destination.execute("SELECT * FROM public.rooms")
dest_rooms_data = cur_destination.fetchall()
rooms_dict = {role[1]: role[0] for role in dest_rooms_data} 

cur_destination.execute("SELECT * FROM public.courts")
dest_courts_data = cur_destination.fetchall()
court_dict = {court[2]: court[0] for court in dest_courts_data}

for room, court in courtroom_data.items():
    if room in rooms_dict and court in court_dict:
        room_id = rooms_dict[room]
        court_id = court_dict[court]

        if not check_existing_record('courtrooms', 'room_id', room_id):
            id = str(uuid.uuid4())
            batch_courtrooms_data.append((id, court_id, room_id))

if batch_courtrooms_data:
    cur_destination.executemany(
        "INSERT INTO public.courtrooms (id, court_id, room_id) VALUES (%s, %s, %s)",
        batch_courtrooms_data
    )

    for courtroom in batch_courtrooms_data:
        audit_entry_creation(
            table_name='courtrooms',
            record_id=courtroom[0],
            record=courtroom[1]
        )
# endregion

# region 'Regions'

# Regions data -  https://cjscommonplatform-my.sharepoint.com/:x:/r/personal/lawrie_baber-scovell2_hmcts_net/_layouts/15/Doc.aspx?sourcedoc=%7B07C83A7F-EF01-4C78-9B02-AEDD443D15A1%7D&file=Courts%20PRE%20NRO.xlsx&wdOrigin=TEAMS-WEB.undefined_ns.rwc&action=default&mobileredirect=true
region_data = [
    'London',
    'West Midlands (England)',
    'West Midlands (England)',
    'East Midlands (England)',
    'Yorkshire and The Humber',
    'North East (England)',
    'North West (England)',
    'South East (England)',
    'East of England',
    'South West (England)',
    'Wales'

]

for region in region_data:
    if not check_existing_record('regions', 'name', region):
        id = str(uuid.uuid4())

        cur_destination.execute(
            "INSERT INTO public.regions (id, name) VALUES (%s, %s)",
            (id, region)
        )

        audit_entry_creation(
            table_name="regions",
            record_id=id,
            record=region,
        )

# endregion

# region 'Court Regions' Table

# Court regions data -  https://cjscommonplatform-my.sharepoint.com/:x:/r/personal/lawrie_baber-scovell2_hmcts_net/_layouts/15/Doc.aspx?sourcedoc=%7B07C83A7F-EF01-4C78-9B02-AEDD443D15A1%7D&file=Courts%20PRE%20NRO.xlsx&wdOrigin=TEAMS-WEB.undefined_ns.rwc&action=default&mobileredirect=true
court_regions = [
    {"name": "Birmingham", "region": "West Midlands (England)"},
    {"name": "Mold Crown Court", "region": "Wales"},
    {"name": "Reading Crown Court", "region": "South East (England)"},
    {"name": "Leeds Crown Court", "region": "Yorkshire and The Humber"},
    {"name": "Durham Crown Court", "region": "North East (England)"},
    {"name": "Liverpool Crown Court", "region": "North West (England)"},
    {"name": "Nottingham Crown Court", "region": "East Midlands (England)"},
    {"name": "Kingston-upon-Thames Crown Court", "region": "London"},
    {"name": "Leeds Youth Court", "region": "Yorkshire and The Humber"},
    {"name": "Default Court", "region": "London"}
]
court_regions_dict = {court["name"]: court["region"] for court in court_regions}

cur_destination.execute('SELECT id, name FROM public.courts')
courts_data = cur_destination.fetchall()

cur_destination.execute('SELECT id, name FROM public.regions')
regions_data = cur_destination.fetchall()
regions_dict = {region[1]: region[0] for region in regions_data}

for court in courts_data:
    court_id = court[0]
    court_name = court[1]
    region_name = court_regions_dict.get(court_name)
    region_id = regions_dict.get(region_name)


    if not check_existing_record('court_region', 'court_id', court_id):
        id = str(uuid.uuid4())

        cur_destination.execute(
            "INSERT INTO public.court_region (id, court_id, region_id) VALUES (%s, %s, %s)",
            (id, court_id, region_id)
        )

        audit_entry_creation(
            table_name="court_region",
            record_id=id,
            record=court_id,
        )

# endregion

# region 'Portal Access' Table

# --- assumption made that portal users are Level 3 users 
batch_portal_user_data = []

for user in source_portal_users_data:
    user_id = user[0]

    if not check_existing_record('portal_access', 'user_id', user_id):
        id=str(uuid.uuid4())
        password = 'password' # ?
        status = 'active' # ? 

        last_access = datetime.now() # ?
        invitation_datetime = datetime.now() # ?
        registered_datetime = datetime.now() # ?
        created_at = parse_to_timestamp(user[15])
        modified_at =parse_to_timestamp(user[17])
        created_by = user[14]

        batch_portal_user_data.append((
            id, user_id, password, last_access, status, invitation_datetime, registered_datetime, created_at, modified_at
        ))

        audit_entry_creation(
            table_name='portal_access',
            record_id=id,
            record=user_id,
            created_at=created_at,
            created_by=created_by,
        )

if batch_portal_user_data:
    cur_destination.executemany(
        """
        INSERT INTO public.portal_access
            (id, user_id, password, last_access, status, invitation_datetime, registered_datetime, created_at, modified_at)
        VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s)
        """,
        batch_portal_user_data,
    )

# endregion

# region 'App Access' Table

# --- assumption made that app users are all but Level 3 users
batch_app_users_data = []

for user in source_app_users_data:
    user_id = user[0]

    cur_destination.execute("SELECT id FROM public.courts WHERE name = 'Default Court'")
    default_court_id = cur_destination.fetchone()[0]

    if not check_existing_record('app_access', 'user_id', user_id):
        id=str(uuid.uuid4())
        court_id = default_court_id  

        cur_destination.execute("SELECT id FROM public.roles WHERE name = %s", (user[3],))
        role_id = cur_destination.fetchone()

        last_access = datetime.now() # where do i get this info?
        active = True # where do i get this info
        created_at = parse_to_timestamp(user[15])
        modified_at =parse_to_timestamp(user[17])
        created_by = user[14]

        batch_app_users_data.append((
            id, user_id, court_id, role_id, last_access, active, created_at, modified_at
        ))

        audit_entry_creation(
            table_name='app_access',
            record_id=id,
            record=user_id,
            created_at=created_at,
            created_by=created_by,
        )

if batch_app_users_data:
    cur_destination.executemany(
        """
        INSERT INTO public.app_access
            (id, user_id, court_id, role_id, last_access, active, created_at, modified_at)
        VALUES (%s, %s, %s, %s, %s, %s, %s, %s)
        """,
        batch_app_users_data,
    )
# endregion

# region 'Cases' Table

# creating a temporary table for the unique case_ref values
cur_destination.execute(
    """CREATE TABLE IF NOT EXISTS public.temp_cases (
                            booking_id UUID PRIMARY KEY,
                            case_id UUID,
                            reference VARCHAR(25),
                            court_name VARCHAR(250),
                            court_id UUID,
                            created_at VARCHAR(50),
                            created_by VARCHAR(100),
                            modified_at VARCHAR(50)
                            )
                        """
)
conn_destination.commit()

cur_destination.execute("SELECT id FROM public.courts WHERE name = 'Default Court'")
default_court_id = cur_destination.fetchone()

temp_cases_data = []
for case in source_cases_data:
    reference = case[1]

    if not check_existing_record('temp_cases', 'reference', reference):
        cur_destination.execute(
            "SELECT id FROM public.courts WHERE name = %s", (case[2],)
        )
        court_id = cur_destination.fetchone()
        booking_id= case[0]
        case_id = str(uuid.uuid4())
        court_name = case[2]
        created_at = parse_to_timestamp(case[5])
        created_by = case[4]
        modified_at = parse_to_timestamp(case[6])
        temp_cases_data.append((booking_id, case_id, reference, court_name, court_id, created_at, created_by, modified_at))

cur_destination.executemany(
    """INSERT INTO public.temp_cases (booking_id, case_id, reference, court_name, court_id, created_at, created_by, modified_at)
       VALUES (%s, %s, %s, %s, %s, %s, %s, %s)""",
    temp_cases_data
)   

cur_destination.execute("SELECT * FROM public.temp_cases")
source_temp_cases_data = cur_destination.fetchall()

cases_data = []
for case in source_temp_cases_data:
    reference = case[2]

    if not check_existing_record('cases', 'reference', reference):
        id = case[1]
        court_id = default_court_id if case[4] is None else case[4]
        test = False  # to verity the default should be False
        created_at = parse_to_timestamp(case[5])
        modified_at = parse_to_timestamp(case[7])
        created_by = case[6]

        cases_data.append((id, court_id, reference, test, created_at, modified_at))
        
        audit_entry_creation(
            table_name="cases",
            record_id=id,
            record=reference,
            created_at=created_at,
            created_by=created_by,
        )

if cases_data:
    cur_destination.executemany(
        """
        INSERT INTO public.cases
            (id, court_id, reference, test, created_at, modified_at)
        VALUES (%s, %s, %s, %s, %s, %s)
        """,
        cases_data,
    )

# endregion

# region 'Bookings' Table

for booking in source_bookings_data:
    id = booking[0]

    if not check_existing_record('bookings', 'id', id):
        cur_destination.execute(
            "SELECT * FROM public.temp_cases WHERE reference = %s", (booking[1],)
        )
        case_details = cur_destination.fetchone()
        case_id = case_details[1] if case_details else None

        if case_id:
            # Check if case_id exists in the cases table
            if check_existing_record('cases','id', case_id):
                scheduled_for = (datetime.today()) 
                created_at = parse_to_timestamp(case_details[1])
                modified_at = parse_to_timestamp(case_details[3])
                created_by = case_details[2]

                cur_destination.execute(
                    """
                    INSERT INTO public.bookings 
                        (id, case_id, scheduled_for, created_at, modified_at)
                    VALUES (%s, %s, %s, %s, %s )
                    """,
                    (id, case_id, scheduled_for, created_at, modified_at),
                )

                audit_entry_creation(
                    table_name="bookings",
                    record_id=id,
                    record=case_id,
                    created_at=created_at,
                    created_by=created_by,
                )

# endregion

# region 'Participants'

for participant in source_participant_data:
    id = participant[0]
    
    cur_destination.execute(
        "SELECT case_id FROM public.bookings WHERE id = %s", (participant[4],)
    )
    case_id = cur_destination.fetchone()
    p_type = participant[3]

    if case_id:
        if not check_existing_record('participants','case_id', case_id) and p_type:
            participant_type = p_type.lower()
            first_name = participant[6]
            last_name = participant[7]
            created_at = parse_to_timestamp(participant[9])
            modified_at = parse_to_timestamp(participant[11])

            cur_destination.execute(
                """
                INSERT INTO public.participants 
                    (id, case_id, participant_type, first_name, last_name, created_at, modified_at)
                VALUES (%s, %s, %s, %s, %s, %s, %s )
                """,
                (id, case_id, participant_type, first_name, last_name,  created_at, modified_at),
            )

            created_by = participant[8]
            audit_entry_creation(
                table_name="participants",
                record_id=id,
                record=case_id,
                created_at=created_at,
                created_by=created_by,
            )

# endregion

# region 'Booking Participants'

# Fetch case_id's for participants
cur_destination.execute("""
    SELECT p.id AS participant_id, b.id AS booking_id
    FROM public.participants p
    JOIN public.bookings b ON p.case_id = b.case_id
    WHERE NOT EXISTS (
        SELECT 1
        FROM public.booking_participant bp
        WHERE bp.participant_id = p.id AND bp.booking_id = b.id
    )
""")
booking_participant_query = cur_destination.fetchall()

for row in booking_participant_query:
    participant_id, booking_id = row
    id = str(uuid.uuid4())

    cur_destination.execute(
        """
        INSERT INTO public.booking_participant (id, participant_id, booking_id)
        VALUES (%s, %s, %s)
        """,
        (id, participant_id, booking_id),  
    )
            
    audit_entry_creation(
        table_name="booking_participant",
        record_id=id,
        record=booking_id,
    )

# endregion

# region 'Capture sessions'

# creating a temporary table for the unique recordings and capture session values
cur_destination.execute(
    """CREATE TABLE IF NOT EXISTS public.temp_recordings (
        capture_session_id UUID,
        recording_id UUID,
        booking_id UUID,
        parent_recording_id UUID
    )
    """
)
conn_destination.commit()

for recording in source_capture_sessions_data:
    recording_id = recording[0]

    cur_destination.execute(
        """SELECT * FROM public.temp_recordings WHERE recording_id = %s""",
        (recording_id,)
    )
    existing_record = cur_destination.fetchone()

    if not existing_record:
        capture_session_id = str(uuid.uuid4())
        booking_id = recording[1]
        parent_recording_id = recording[9]

        cur_destination.execute(
            """ INSERT INTO public.temp_recordings (capture_session_id, recording_id, booking_id, parent_recording_id) 
                VALUES (%s, %s, %s,%s)""",
            (capture_session_id, recording_id, booking_id, parent_recording_id),
        )

cur_destination.execute("SELECT * FROM public.temp_recordings WHERE recording_id = parent_recording_id")
temp_recording_data = cur_destination.fetchall()

for temp_recording in temp_recording_data:
    id = temp_recording[0]
    booking_id = temp_recording[2]

    if check_existing_record('bookings', 'id', booking_id) and not check_existing_record('capture_sessions','id', id):
        origin = 'pre'
        ingest_address = recording[8] 
        live_output_url = recording[20]
        # started_at =  ??? 
        # started_by_user_id = ???
        # finished_at = ??? 
        # finished_by_user_id = ??? 
        # status = ???

        cur_destination.execute(
            """
            INSERT INTO public.capture_sessions ( id, booking_id, origin, ingest_address, live_output_url)
            VALUES (%s, %s, %s,%s,%s)
            """,
            ( id, booking_id, origin, ingest_address, live_output_url),  
        )

        audit_entry_creation(
            table_name="capture_sessions",
            record_id=id,
            record=booking_id,
        )

# endregion

# region 'Recordings' 

#  first inserting the recordings with multiple recordings versions - this is to satisfy the parent_recording_id FK constraint
parent_recording_ids = [recording[9] for recording in source_recording_data]
id_counts = Counter(parent_recording_ids)
duplicate_parent_ids = [id for id, count in id_counts.items() if count > 1]

duplicate_records = [recording for recording in source_recording_data if recording[0] in duplicate_parent_ids]

for recording in duplicate_records:
    id = recording[0]
    parent_recording_id = recording[9]
    
    cur_destination.execute("SELECT capture_session_id FROM public.temp_recordings WHERE parent_recording_id = %s", (parent_recording_id,)) 
    result = cur_destination.fetchone()

    if result:
        capture_session_id = result[0]
        if not check_existing_record('recordings', 'id', id) and check_existing_record('capture_sessions', 'id', capture_session_id):
            version = recording[12] 
            url = recording[20] if recording[20] is not None else 'Unknown URL'
            filename = recording[14]
            created_at = parse_to_timestamp(recording[22])

            cur_destination.execute(
                """
                INSERT INTO public.recordings (id, capture_session_id, parent_recording_id, version, url, filename, created_at)
                VALUES (%s, %s, %s, %s, %s, %s, %s)
                """,
                (id, capture_session_id, parent_recording_id, version, url, filename, created_at),  
            )

            audit_entry_creation(
                table_name="recordings",
                record_id=id,
                record=capture_session_id,
            )

# inserting remaining records
for recording in source_recording_data:
    id = recording[0]
    parent_recording_id = recording[9] if recording[9] in [rec[0] for rec in source_recording_data] else None
        
    cur_destination.execute("SELECT capture_session_id from public.temp_recordings where parent_recording_id = %s",(parent_recording_id,)) 
    result = cur_destination.fetchone()

    if result:
        capture_session_id = result[0]
        if not check_existing_record('recordings', 'id', id) and check_existing_record('capture_sessions', 'id', capture_session_id):
            version = recording[12] 
            url = recording[20] if recording[20] is not None else 'Unknown URL'
            filename = recording[14]
            created_at = parse_to_timestamp(recording[22])
    #         duration =  ???  - this info is in the asset files on AMS 
    #         edit_instruction = ???

            cur_destination.execute(
                """
                INSERT INTO public.recordings (id, capture_session_id, parent_recording_id, version, url, filename, created_at)
                VALUES (%s, %s, %s, %s, %s, %s, %s)
                """,
                (id, capture_session_id, parent_recording_id, version, url, filename, created_at),  
            )

            audit_entry_creation(
                table_name="recordings",
                record_id=id,
                record=capture_session_id,
            )

# endregion



# region --------------------------------------- Closing DB connections

conn_destination.commit()
cur_destination.close()
conn_destination.close()

conn_source.commit()
cur_source.close()
conn_source.close()

# endregion 
