-- V1__initial_setup.sql

CREATE TYPE "recording_status" AS ENUM (
  'created',
  'booked',
  'recording',
  'finished',
  'failure'
);

CREATE TYPE "court_type" AS ENUM (
  'crown',
  'magistrate',
  'youth',
  'supreme'
);

CREATE TYPE "account_status" AS ENUM (
  'unverified',
  'verified',
  'deactivated'
);

CREATE TABLE "users" (
  "id" uuid UNIQUE PRIMARY KEY DEFAULT (gen_random_uuid()),
  "created_at" timestamp NOT NULL DEFAULT (now()),
  "updated_at" timestamp DEFAULT null,
  "deleted_at" timestamp DEFAULT null,
  "created_by" uuid NOT NULL,
  "updated_by" uuid DEFAULT null,
  "deleted_by" uuid DEFAULT null,
  "email" varchar(255) UNIQUE NOT NULL,
  "first_name" varchar(255) NOT NULL,
  "last_name" varchar(255) NOT NULL,
  "phone" varchar(15) UNIQUE,
  "organisation_id" uuid NOT NULL
);

CREATE TABLE "streaming_manager_accounts" (
  "id" uuid UNIQUE PRIMARY KEY DEFAULT (gen_random_uuid()),
  "created_at" timestamp NOT NULL DEFAULT (now()),
  "updated_at" timestamp DEFAULT null,
  "deleted_at" timestamp DEFAULT null,
  "created_by" uuid NOT NULL,
  "updated_by" uuid DEFAULT null,
  "deleted_by" uuid DEFAULT null,
  "user_id" uuid NOT NULL,
  "status" account_status NOT NULL DEFAULT 'unverified',
  "ad_token" json DEFAULT null
);

CREATE TABLE "portal_accounts" (
  "id" uuid UNIQUE PRIMARY KEY DEFAULT (gen_random_uuid()),
  "created_at" timestamp NOT NULL DEFAULT (now()),
  "updated_at" timestamp DEFAULT null,
  "deleted_at" timestamp DEFAULT null,
  "created_by" uuid NOT NULL,
  "updated_by" uuid DEFAULT null,
  "deleted_by" uuid DEFAULT null,
  "user_id" uuid NOT NULL,
  "status" account_status NOT NULL DEFAULT 'unverified',
  "b2c_token" json DEFAULT null
);

CREATE TABLE "courts" (
  "id" uuid UNIQUE PRIMARY KEY DEFAULT (gen_random_uuid()),
  "created_at" timestamp NOT NULL DEFAULT (now()),
  "updated_at" timestamp DEFAULT null,
  "deleted_at" timestamp DEFAULT null,
  "created_by" uuid NOT NULL,
  "updated_by" uuid DEFAULT null,
  "deleted_by" uuid DEFAULT null,
  "name" varchar(255) UNIQUE NOT NULL,
  "type" court_type NOT NULL DEFAULT 'crown',
  "location" varchar(255) NOT NULL
);

CREATE TABLE "organisations" (
  "id" uuid UNIQUE PRIMARY KEY DEFAULT (gen_random_uuid()),
  "created_at" timestamp NOT NULL DEFAULT (now()),
  "updated_at" timestamp DEFAULT null,
  "deleted_at" timestamp DEFAULT null,
  "created_by" uuid NOT NULL,
  "updated_by" uuid DEFAULT null,
  "deleted_by" uuid DEFAULT null,
  "name" varchar(255) UNIQUE NOT NULL,
  "location" varchar(255) NOT NULL
);

CREATE TABLE "roles" (
  "id" uuid UNIQUE PRIMARY KEY DEFAULT (gen_random_uuid()),
  "created_at" timestamp NOT NULL DEFAULT (now()),
  "updated_at" timestamp DEFAULT null,
  "deleted_at" timestamp DEFAULT null,
  "created_by" uuid NOT NULL,
  "updated_by" uuid DEFAULT null,
  "deleted_by" uuid DEFAULT null,
  "name" varchar(16) UNIQUE NOT NULL,
  "description" text NOT NULL
);

CREATE TABLE "permissions" (
  "id" uuid UNIQUE PRIMARY KEY DEFAULT (gen_random_uuid()),
  "created_at" timestamp NOT NULL DEFAULT (now()),
  "updated_at" timestamp DEFAULT null,
  "deleted_at" timestamp DEFAULT null,
  "created_by" uuid NOT NULL,
  "updated_by" uuid DEFAULT null,
  "deleted_by" uuid DEFAULT null,
  "name" varchar(16) UNIQUE NOT NULL,
  "description" text NOT NULL
);

CREATE TABLE "cases" (
  "id" uuid UNIQUE PRIMARY KEY DEFAULT (gen_random_uuid()),
  "created_at" timestamp NOT NULL DEFAULT (now()),
  "updated_at" timestamp DEFAULT null,
  "deleted_at" timestamp DEFAULT null,
  "created_by" uuid NOT NULL,
  "updated_by" uuid DEFAULT null,
  "deleted_by" uuid DEFAULT null,
  "reference" varchar(16) UNIQUE NOT NULL,
  "court_id" uuid NOT NULL
);

CREATE TABLE "contacts" (
  "id" uuid UNIQUE PRIMARY KEY DEFAULT (gen_random_uuid()),
  "created_at" timestamp NOT NULL DEFAULT (now()),
  "updated_at" timestamp DEFAULT null,
  "deleted_at" timestamp DEFAULT null,
  "created_by" uuid NOT NULL,
  "updated_by" uuid DEFAULT null,
  "deleted_by" uuid DEFAULT null,
  "first_name" varchar(255) NOT NULL,
  "last_name" varchar(255) NOT NULL
);

CREATE TABLE "recordings" (
  "id" uuid UNIQUE PRIMARY KEY DEFAULT (gen_random_uuid()),
  "created_at" timestamp NOT NULL DEFAULT (now()),
  "updated_at" timestamp DEFAULT null,
  "deleted_at" timestamp DEFAULT null,
  "created_by" uuid NOT NULL,
  "updated_by" uuid DEFAULT null,
  "deleted_by" uuid DEFAULT null,
  "case_id" uuid NOT NULL,
  "room_id" uuid NOT NULL,
  "ingest_rtmp" varchar(255) DEFAULT null,
  "live_hls" varchar(255) DEFAULT null,
  "parent_id" uuid DEFAULT null,
  "status" recording_status NOT NULL DEFAULT 'created',
  "start_date" date NOT NULL,
  "duration" varchar(16) DEFAULT null
);

CREATE TABLE "rooms" (
  "id" uuid UNIQUE PRIMARY KEY DEFAULT (gen_random_uuid()),
  "created_at" timestamp NOT NULL DEFAULT (now()),
  "updated_at" timestamp DEFAULT null,
  "deleted_at" timestamp DEFAULT null,
  "created_by" uuid NOT NULL,
  "updated_by" uuid DEFAULT null,
  "deleted_by" uuid DEFAULT null,
  "name" varchar(16) UNIQUE NOT NULL
);

CREATE TABLE "edit_requests" (
  "id" uuid UNIQUE PRIMARY KEY DEFAULT (gen_random_uuid()),
  "created_at" timestamp NOT NULL DEFAULT (now()),
  "updated_at" timestamp DEFAULT null,
  "deleted_at" timestamp DEFAULT null,
  "created_by" uuid NOT NULL,
  "updated_by" uuid DEFAULT null,
  "deleted_by" uuid DEFAULT null,
  "recording_id" uuid NOT NULL,
  "instruction" json NOT NULL,
  "version" int NOT NULL,
  "reason" text NOT NULL
);

CREATE TABLE "shared_recordings" (
  "id" uuid UNIQUE PRIMARY KEY DEFAULT (gen_random_uuid()),
  "created_at" timestamp NOT NULL DEFAULT (now()),
  "updated_at" timestamp DEFAULT null,
  "deleted_at" timestamp DEFAULT null,
  "created_by" uuid NOT NULL,
  "updated_by" uuid DEFAULT null,
  "deleted_by" uuid DEFAULT null,
  "recording_id" uuid NOT NULL,
  "user_id" uuid NOT NULL
);

CREATE TABLE "sessions" (
  "id" uuid UNIQUE PRIMARY KEY DEFAULT (gen_random_uuid()),
  "created_at" timestamp NOT NULL DEFAULT (now()),
  "updated_at" timestamp DEFAULT null,
  "deleted_at" timestamp DEFAULT null,
  "created_by" uuid NOT NULL,
  "updated_by" uuid DEFAULT null,
  "deleted_by" uuid DEFAULT null,
  "user_id" uuid UNIQUE NOT NULL,
  "ip_address" varchar(255) NOT NULL,
  "user_agent" varchar(255) NOT NULL,
  "payload" json NOT NULL,
  "last_activity" int NOT NULL
);

CREATE TABLE "invitations" (
  "id" uuid UNIQUE PRIMARY KEY DEFAULT (gen_random_uuid()),
  "created_at" timestamp NOT NULL DEFAULT (now()),
  "updated_at" timestamp DEFAULT null,
  "deleted_at" timestamp DEFAULT null,
  "created_by" uuid NOT NULL,
  "updated_by" uuid DEFAULT null,
  "deleted_by" uuid DEFAULT null,
  "organisation_id" uuid NOT NULL,
  "role_id" uuid NOT NULL,
  "email" varchar(255) UNIQUE NOT NULL
);

CREATE TABLE "live_recording_events" (
  "id" uuid UNIQUE PRIMARY KEY DEFAULT (gen_random_uuid()),
  "created_at" timestamp NOT NULL DEFAULT (now()),
  "updated_at" timestamp DEFAULT null,
  "deleted_at" timestamp DEFAULT null,
  "created_by" uuid NOT NULL,
  "updated_by" uuid DEFAULT null,
  "deleted_by" uuid DEFAULT null,
  "recording_id" uuid NOT NULL,
  "event" varchar(255) NOT NULL,
  "timestamp" timestamp NOT NULL,
  "payload" json NOT NULL
);

CREATE TABLE "audits" (
  "id" uuid UNIQUE PRIMARY KEY DEFAULT (gen_random_uuid()),
  "created_at" timestamp NOT NULL DEFAULT (now()),
  "updated_at" timestamp DEFAULT null,
  "deleted_at" timestamp DEFAULT null,
  "created_by" uuid NOT NULL,
  "updated_by" uuid DEFAULT null,
  "deleted_by" uuid DEFAULT null,
  "auditable_id" uuid NOT NULL,
  "auditable_type" varchar(16) NOT NULL,
  "user_id" uuid NOT NULL,
  "action" varchar(16) NOT NULL,
  "source" varchar(16) NOT NULL,
  "timestamp" timestamp NOT NULL DEFAULT (now())
);

CREATE TABLE "users_roles" (
  "id" uuid UNIQUE PRIMARY KEY DEFAULT (gen_random_uuid()),
  "created_at" timestamp NOT NULL DEFAULT (now()),
  "updated_at" timestamp DEFAULT null,
  "deleted_at" timestamp DEFAULT null,
  "created_by" uuid NOT NULL,
  "updated_by" uuid DEFAULT null,
  "deleted_by" uuid DEFAULT null,
  "user_id" uuid NOT NULL,
  "role_id" uuid NOT NULL
);

CREATE TABLE "roles_permissions" (
  "id" uuid UNIQUE PRIMARY KEY DEFAULT (gen_random_uuid()),
  "created_at" timestamp NOT NULL DEFAULT (now()),
  "updated_at" timestamp DEFAULT null,
  "deleted_at" timestamp DEFAULT null,
  "created_by" uuid NOT NULL,
  "updated_by" uuid DEFAULT null,
  "deleted_by" uuid DEFAULT null,
  "role_id" uuid NOT NULL,
  "permission_id" uuid NOT NULL
);

CREATE TABLE "contacts_cases" (
  "id" uuid UNIQUE PRIMARY KEY DEFAULT (gen_random_uuid()),
  "created_at" timestamp NOT NULL DEFAULT (now()),
  "updated_at" timestamp DEFAULT null,
  "deleted_at" timestamp DEFAULT null,
  "created_by" uuid NOT NULL,
  "updated_by" uuid DEFAULT null,
  "deleted_by" uuid DEFAULT null,
  "contact_id" uuid NOT NULL,
  "case_id" uuid NOT NULL
);

CREATE TABLE "users_courts" (
  "id" uuid UNIQUE PRIMARY KEY DEFAULT (gen_random_uuid()),
  "created_at" timestamp NOT NULL DEFAULT (now()),
  "updated_at" timestamp DEFAULT null,
  "deleted_at" timestamp DEFAULT null,
  "created_by" uuid NOT NULL,
  "updated_by" uuid DEFAULT null,
  "deleted_by" uuid DEFAULT null,
  "user_id" uuid NOT NULL,
  "court_id" uuid NOT NULL
);

CREATE TABLE "courts_rooms" (
  "id" uuid UNIQUE PRIMARY KEY DEFAULT (gen_random_uuid()),
  "created_at" timestamp NOT NULL DEFAULT (now()),
  "updated_at" timestamp DEFAULT null,
  "deleted_at" timestamp DEFAULT null,
  "created_by" uuid NOT NULL,
  "updated_by" uuid DEFAULT null,
  "deleted_by" uuid DEFAULT null,
  "court_id" uuid NOT NULL,
  "room_id" uuid NOT NULL
);

CREATE INDEX "user_full_name" ON "users" ((first_name || ' ' || last_name));

CREATE INDEX "contact_full_name" ON "contacts" ((first_name || ' ' || last_name));

ALTER TABLE "users" ADD FOREIGN KEY ("organisation_id") REFERENCES "organisations" ("id");

ALTER TABLE "streaming_manager_accounts" ADD FOREIGN KEY ("user_id") REFERENCES "users" ("id");

ALTER TABLE "portal_accounts" ADD FOREIGN KEY ("user_id") REFERENCES "users" ("id");

ALTER TABLE "cases" ADD FOREIGN KEY ("court_id") REFERENCES "courts" ("id");

ALTER TABLE "recordings" ADD FOREIGN KEY ("case_id") REFERENCES "cases" ("id");

ALTER TABLE "recordings" ADD FOREIGN KEY ("room_id") REFERENCES "rooms" ("id");

ALTER TABLE "recordings" ADD FOREIGN KEY ("parent_id") REFERENCES "recordings" ("id");

ALTER TABLE "edit_requests" ADD FOREIGN KEY ("recording_id") REFERENCES "recordings" ("id");

ALTER TABLE "shared_recordings" ADD FOREIGN KEY ("recording_id") REFERENCES "recordings" ("id");

ALTER TABLE "shared_recordings" ADD FOREIGN KEY ("user_id") REFERENCES "users" ("id");

ALTER TABLE "sessions" ADD FOREIGN KEY ("user_id") REFERENCES "users" ("id");

ALTER TABLE "invitations" ADD FOREIGN KEY ("organisation_id") REFERENCES "organisations" ("id");

ALTER TABLE "invitations" ADD FOREIGN KEY ("role_id") REFERENCES "roles" ("id");

ALTER TABLE "live_recording_events" ADD FOREIGN KEY ("recording_id") REFERENCES "recordings" ("id");

ALTER TABLE "audits" ADD FOREIGN KEY ("user_id") REFERENCES "users" ("id");

ALTER TABLE "users_roles" ADD FOREIGN KEY ("user_id") REFERENCES "users" ("id");

ALTER TABLE "users_roles" ADD FOREIGN KEY ("role_id") REFERENCES "roles" ("id");

ALTER TABLE "roles_permissions" ADD FOREIGN KEY ("role_id") REFERENCES "roles" ("id");

ALTER TABLE "roles_permissions" ADD FOREIGN KEY ("permission_id") REFERENCES "permissions" ("id");

ALTER TABLE "contacts_cases" ADD FOREIGN KEY ("contact_id") REFERENCES "contacts" ("id");

ALTER TABLE "contacts_cases" ADD FOREIGN KEY ("case_id") REFERENCES "cases" ("id");

ALTER TABLE "users_courts" ADD FOREIGN KEY ("user_id") REFERENCES "users" ("id");

ALTER TABLE "users_courts" ADD FOREIGN KEY ("court_id") REFERENCES "courts" ("id");

ALTER TABLE "courts_rooms" ADD FOREIGN KEY ("court_id") REFERENCES "courts" ("id");

ALTER TABLE "courts_rooms" ADD FOREIGN KEY ("room_id") REFERENCES "rooms" ("id");
