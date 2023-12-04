INSERT INTO public.courts (id, court_type, name, location_code)
VALUES ('7983a646-7168-43cf-81fc-14d5c35297c2', 'CROWN', 'Example Court', '54321');

INSERT INTO public.courts (id, court_type, name, location_code)
VALUES ('47d75f66-a1aa-4deb-b527-e199ecc6cf98', 'FAMILY', 'Family Court', 'abc123');

INSERT INTO public.cases (id, created_at, reference, court_id, test, deleted_at)
VALUES ('d44b6109-65d2-46a7-ab94-bee374f8b780', NOW(), 'CASE123', '7983a646-7168-43cf-81fc-14d5c35297c2', FALSE, NULL);

INSERT INTO public.cases (id, created_at, reference, court_id, test, deleted_at)
VALUES ('c6f3e040-3086-4942-910d-4fe8b38b49ca', NOW(), 'DEL123', '7983a646-7168-43cf-81fc-14d5c35297c2', FALSE, NOW());
