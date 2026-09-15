ALTER TABLE app_access
  ADD constraint USER_COURT_ROLE unique (user_id, court_id, role_id)
