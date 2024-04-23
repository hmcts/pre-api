DROP TABLE role_permission;
DROP TABLE permissions;
DROP TABLE courtrooms;

CREATE TABLE user_court (
    id SERIAL PRIMARY KEY,
    user_id UUID REFERENCES app_access(id) NOT NULL,
    court_id UUID REFERENCES courts(id) NOT NULL,
    default_court BOOLEAN NOT NULL DEFAULT FALSE,
    role_id UUID REFERENCES roles(id) NOT NULL
);

ALTER TABLE rooms RENAME TO virtual_conference_rooms;
ALTER TABLE virtual_conference_rooms
ADD COLUMN host_pin INT,
ADD COLUMN room_address VARCHAR(256);

ALTER TABLE portal_access
ADD COLUMN role_id UUID REFERENCES virtual_conference_rooms(id);

ALTER TABLE bookings
ADD COLUMN role_id UUID REFERENCES virtual_conference_rooms(id);


