
import unittest
from unittest.mock import MagicMock
from ..tables.usercourt import UserCourtManager
from .test_helpers import assert_executemany_query

class TestUserCourtManager(unittest.TestCase):
    def setUp(self):
        self.destination_cursor_mock = MagicMock()
        self.logger_mock = MagicMock()
        self.user_court_manager = UserCourtManager(self.destination_cursor_mock, self.logger_mock)
        self.app_access_data = [
            ('76294e04-9d8a-4da3-a18d-837d432d1b18', 'bb864559-3aad-4d7c-aa12-d4c0a504a4ef', 'c920307f-5198-48c1-8954-d5277b415853'), 
            ('8aec82a3-9616-488e-8f4b-6b4439d3743b', 'bb864559-3aad-4d7c-aa12-d4c0a504a4ef', 'c920307f-5198-48c1-8954-d5277b415853'), 
            ('2dd35d79-7d6e-4a93-8a97-36bb4362b2ea', 'a93519ce-7aca-44e1-bc52-f93fb3327238', 'fd1f4509-2583-466d-b0b9-ff138d22541a')
        ]

    def mock_get_app_access_data(self, app_access_data=None):
        if app_access_data is None:
            app_access_data = self.app_access_data
        self.user_court_manager.get_app_access_data = MagicMock(return_value=app_access_data)

    def test_get_app_access_data(self):
        self.destination_cursor_mock.fetchall.return_value = self.app_access_data
        result = self.user_court_manager.get_app_access_data(self.destination_cursor_mock)
        self.assertEqual(result, self.app_access_data, "Returned data does not match expected data")

    def test_migrate_data_existing_record(self):
        self.mock_get_app_access_data()
        with unittest.mock.patch('migration.tables.usercourt.check_existing_record', return_value=True):
            self.user_court_manager.migrate_data(self.destination_cursor_mock)
            self.destination_cursor_mock.executemany.assert_not_called()
    
    def test_migrate_data_non_existing_record(self):
        self.mock_get_app_access_data()
        with unittest.mock.patch('migration.tables.usercourt.check_existing_record', return_value=False):
            self.user_court_manager.migrate_data(self.destination_cursor_mock)
            expected_query = (
                "INSERT INTO public.user_court "
                "(user_id, court_id, default_court, role_id) "
                "VALUES (%s, %s, %s, %s)"
            )
            assert_executemany_query(self.destination_cursor_mock, expected_query)

    def test_migrate_data_empty_app_access_data(self):
        self.mock_get_app_access_data(app_access_data=[])
        with unittest.mock.patch('migration.tables.usercourt.check_existing_record', return_value=False):
            self.user_court_manager.migrate_data(self.destination_cursor_mock)
            self.destination_cursor_mock.executemany.assert_not_called()

if __name__ == '__main__':
    unittest.main()

