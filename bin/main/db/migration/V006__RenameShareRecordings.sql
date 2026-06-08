ALTER TABLE public.share_recordings RENAME TO share_bookings;
ALTER TABLE public.share_bookings RENAME COLUMN capture_session_id TO booking_id;
ALTER TABLE public.share_bookings DROP CONSTRAINT share_recordings_capture_session_id_fkey;
ALTER TABLE public.share_bookings ADD CONSTRAINT share_bookings_booking_id_fkey FOREIGN KEY (booking_id) REFERENCES public.bookings(id);
