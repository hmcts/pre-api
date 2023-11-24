-------------------------------------- Create types
CREATE TYPE public.COURT_TYPE AS ENUM ('crown', 'magistrate', 'family');
CREATE TYPE public.PARTICIPANT_TYPE AS ENUM ('witness','defendant');
CREATE TYPE public.RECORDING_STATUS AS ENUM ('standby', 'initialisation', 'recording', 'finished', 'processing', 'available', 'future');
CREATE TYPE public.RECORDING_ORIGIN AS ENUM ('pre', 'vodafone');
CREATE TYPE public.AUDIT_LOG_SOURCE AS ENUM ('application', 'portal', 'admin', 'auto');
CREATE TYPE public.AUDIT_LOG_TYPE AS ENUM ('create', 'update', 'delete', 'action');

-------------------------------------- Create tables
CREATE TABLE public.regions (
    id UUID PRIMARY KEY NOT NULL,
    name VARCHAR(100)
);

CREATE TABLE public.courts (
    id UUID PRIMARY KEY NOT NULL,
    court_type COURT_TYPE NOT NULL,
    name VARCHAR(255) NOT NULL,
    location_code VARCHAR(25)
);

CREATE TABLE public.rooms (
    id UUID PRIMARY KEY NOT NULL,
    room VARCHAR(45) NOT NULL
);

CREATE TABLE public.court_regions(
    id UUID PRIMARY KEY NOT NULL,
    court_id UUID REFERENCES courts(id) NOT NULL,
    region_id UUID REFERENCES regions(id) NOT NULL

);

CREATE TABLE public.courtrooms (
    id UUID PRIMARY KEY NOT NULL,
    court_id UUID REFERENCES courts(id) NOT NULL,
    room_id UUID REFERENCES rooms(id) NOT NULL
);

CREATE TABLE public.cases (
    id UUID PRIMARY KEY NOT NULL,
    court_id UUID REFERENCES courts(id) NOT NULL ,
    case_ref VARCHAR(25) NOT NULL, 
    test BOOLEAN DEFAULT FALSE,
    deleted BOOLEAN DEFAULT FALSE,
    created_on TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    modified_on TIMESTAMPTZ DEFAULT NOW()
);


CREATE TABLE public.participants (
    id UUID PRIMARY KEY NOT NULL,
    case_id UUID REFERENCES cases(id) NOT NULL,
    participant_type PARTICIPANT_TYPE NOT NULL,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    deleted BOOLEAN DEFAULT FALSE,
    created_on TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    modified_on TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE public.bookings (
    id UUID PRIMARY KEY NOT NULL,
    case_id UUID REFERENCES cases(id) NOT NULL,
    date DATE NOT NULL,
    deleted BOOLEAN DEFAULT FALSE,
    created_on TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    modified_on TIMESTAMPTZ DEFAULT NOW()
);


CREATE TABLE public.booking_participants (
    id UUID PRIMARY KEY NOT NULL,
    participant_id UUID REFERENCES participants(id) NOT NULL,
    booking_id UUID REFERENCES bookings(id) NOT NULL
);

CREATE TABLE public.users (
    id UUID PRIMARY KEY NOT NULL,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    email VARCHAR(100) NOT NULL,
    organisation VARCHAR(250),
    phone VARCHAR(50),
    deleted BOOLEAN DEFAULT FALSE,
    created_on TIMESTAMPTZ DEFAULT NOW(),
    modified_on TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE public.roles (
    id UUID PRIMARY KEY NOT NULL,
    name VARCHAR(45) NOT NULL
);

CREATE TABLE public.app_access (
    id UUID PRIMARY KEY NOT NULL,
    user_id UUID REFERENCES users(id) NOT NULL,
    court_id UUID REFERENCES courts(id) NOT NULL,
    role_id UUID REFERENCES roles(id) NOT NULL,
    last_access TIMESTAMPTZ,
    active BOOLEAN DEFAULT TRUE,
    deleted BOOLEAN DEFAULT FALSE,
    created_on TIMESTAMPTZ DEFAULT NOW(),
    modified_on TIMESTAMPTZ DEFAULT NOW()
);


CREATE TABLE public.capture_sessions (
    id UUID PRIMARY KEY NOT NULL,
    booking_id UUID REFERENCES bookings(id) NOT NULL,
    origin RECORDING_ORIGIN NOT NULL,
    ingest_address VARCHAR(255),
    live_output_url VARCHAR(100),
    started_on TIMESTAMPTZ,
    started_by_user_id UUID REFERENCES users(id),
    finished_on TIMESTAMPTZ,
    finished_by_user_id UUID REFERENCES users(id),
    status RECORDING_STATUS,
    deleted BOOLEAN DEFAULT FALSE
);

CREATE TABLE public.recording_versions (
    id UUID PRIMARY KEY NOT NULL,
    capture_session_id UUID REFERENCES capture_sessions(id) NOT NULL,
    version INT NOT NULL,
    url VARCHAR(255) NOT NULL,
    filename VARCHAR(255) NOT NULL,
    created_on TIMESTAMPTZ NOT NULL,
    duration TIME,
    edit_instruction JSON,
    deleted BOOLEAN DEFAULT FALSE
);

CREATE TABLE public.share_recordings (
    id UUID PRIMARY KEY NOT NULL,
    capture_session_id UUID REFERENCES capture_sessions(id) NOT NULL,
    shared_with_user_id UUID REFERENCES users(id) NOT NULL,
    shared_by_user_id UUID REFERENCES users(id) NOT NULL,
    created_on TIMESTAMPTZ DEFAULT NOW(),
    deleted BOOLEAN DEFAULT FALSE
);

CREATE TABLE public.portal_access (
    id UUID PRIMARY KEY NOT NULL,
    user_id UUID REFERENCES users(id) NOT NULL,
    password VARCHAR(45) NOT NULL,
    last_access TIMESTAMPTZ,
    invitation_sent BOOLEAN DEFAULT FALSE,
    invitation_datetime TIMESTAMPTZ,
    registered BOOLEAN DEFAULT FALSE,
    registered_datetime TIMESTAMPTZ,
    active BOOLEAN,
    deleted BOOLEAN DEFAULT FALSE,
    created_on TIMESTAMPTZ DEFAULT NOW(),
    modified_on TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE public.audits (
    id UUID PRIMARY KEY NOT NULL,
    table_name VARCHAR(25),
    table_record_id UUID,
    source AUDIT_LOG_SOURCE NOT NULL,
    type AUDIT_LOG_TYPE NOT NULL,
    category VARCHAR(100),
    activity VARCHAR(100),
    functional_area VARCHAR(100),
    audit_details TEXT,
    created_by VARCHAR(50),
    created_on TIMESTAMPTZ NOT NULL DEFAULT NOW() 
);

