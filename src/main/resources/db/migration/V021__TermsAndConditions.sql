CREATE TYPE public.TERMS_AND_CONDITIONS_TYPE AS ENUM  (
  'APP',
  'PORTAL'
);

CREATE TABLE public.terms_and_conditions (
  id UUID PRIMARY KEY,
  content TEXT NOT NULL,
  type public.TERMS_AND_CONDITIONS_TYPE NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE public.users_terms_conditions (
  id UUID PRIMARY KEY,
  user_id UUID REFERENCES users(id) NOT NULL,
  terms_and_conditions_id UUID REFERENCES terms_and_conditions(id) NOT NULL,
  accepted_at TIMESTAMPTZ NOT NULL
);
