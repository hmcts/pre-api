UPDATE public.bookings
SET scheduled_for = scheduled_for + INTERVAL '6 hours'
WHERE scheduled_for::time = '23:00:00';
