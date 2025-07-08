-- Add indexes on foreign key columns for better performance

-- booking_participant
CREATE INDEX IF NOT EXISTS idx_booking_participant_participant_id ON public.booking_participant (participant_id);
CREATE INDEX IF NOT EXISTS idx_booking_participant_booking_id ON public.booking_participant (booking_id);

-- capture_sessions
CREATE INDEX IF NOT EXISTS idx_capture_sessions_booking_id ON public.capture_sessions (booking_id);
CREATE INDEX IF NOT EXISTS idx_capture_sessions_started_by_user_id ON public.capture_sessions (started_by_user_id);
CREATE INDEX IF NOT EXISTS idx_capture_sessions_finished_by_user_id ON public.capture_sessions (finished_by_user_id);

-- cases
CREATE INDEX IF NOT EXISTS idx_cases_court_id ON public.cases (court_id);

-- court_region
CREATE INDEX IF NOT EXISTS idx_court_region_court_id ON public.court_region (court_id);
CREATE INDEX IF NOT EXISTS idx_court_region_region_id ON public.court_region (region_id);

-- participants
CREATE INDEX IF NOT EXISTS idx_participants_case_id ON public.participants (case_id);

-- portal_access
CREATE INDEX IF NOT EXISTS idx_portal_access_user_id ON public.portal_access (user_id);

-- share_bookings
CREATE INDEX IF NOT EXISTS idx_share_bookings_booking_id ON public.share_bookings (booking_id);
CREATE INDEX IF NOT EXISTS idx_share_bookings_shared_with_user_id ON public.share_bookings (shared_with_user_id);
CREATE INDEX IF NOT EXISTS idx_share_bookings_shared_by_user_id ON public.share_bookings (shared_by_user_id);

-- app_access
CREATE INDEX IF NOT EXISTS idx_app_access_user_id ON public.app_access (user_id);
CREATE INDEX IF NOT EXISTS idx_app_access_court_id ON public.app_access (court_id);
CREATE INDEX IF NOT EXISTS idx_app_access_role_id ON public.app_access (role_id);

-- users_terms_conditions
CREATE INDEX IF NOT EXISTS idx_users_terms_conditions_user_id ON public.users_terms_conditions (user_id);
CREATE INDEX IF NOT EXISTS idx_users_terms_conditions_terms_and_conditions_id ON public.users_terms_conditions (terms_and_conditions_id);

-- bookings
CREATE INDEX IF NOT EXISTS idx_bookings_case_id ON public.bookings (case_id);

-- bookings_temp
CREATE INDEX IF NOT EXISTS idx_bookings_temp_case_id ON public.bookings_temp (case_id);

-- recordings
CREATE INDEX IF NOT EXISTS idx_recordings_capture_session_id ON public.recordings (capture_session_id);
CREATE INDEX IF NOT EXISTS idx_recordings_parent_recording_id ON public.recordings (parent_recording_id);

-- edit_requests
CREATE INDEX IF NOT EXISTS idx_edit_requests_source_recording_id ON public.edit_requests (source_recording_id);
CREATE INDEX IF NOT EXISTS idx_edit_requests_created_by ON public.edit_requests (created_by);
