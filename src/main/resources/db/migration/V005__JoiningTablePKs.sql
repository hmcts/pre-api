DROP TABLE public.booking_participant;
CREATE TABLE public.booking_participant (
  id SERIAL PRIMARY KEY,
  participant_id UUID REFERENCES participants(id) NOT NULL,
  booking_id UUID REFERENCES bookings(id) NOT NULL
);

DROP TABLE public.court_region;
CREATE TABLE public.court_region (
  id SERIAL PRIMARY KEY,
  court_id UUID REFERENCES courts(id) NOT NULL,
  region_id UUID REFERENCES regions(id) NOT NULL
);

DROP TABLE public.courtrooms;
CREATE TABLE public.courtrooms (
  id SERIAL PRIMARY KEY,
  court_id UUID REFERENCES courts(id) NOT NULL,
  room_id UUID REFERENCES rooms(id) NOT NULL
);

DROP TABLE public.booking_participant;
CREATE TABLE public.booking_participant (
  id SERIAL PRIMARY KEY,
  participant_id UUID REFERENCES participants(id) NOT NULL,
  booking_id UUID REFERENCES bookings(id) NOT NULL
);

DROP TABLE public.role_permission;
CREATE TABLE public.role_permission (
  id SERIAL PRIMARY KEY,
  role_id UUID REFERENCES roles(id),
  permission_id UUID REFERENCES permissions(id)
);
