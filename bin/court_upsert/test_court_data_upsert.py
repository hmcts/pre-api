import unittest
from unittest.mock import MagicMock, call
from .main import upsert_court_info

class TestUpsertCourtInfo(unittest.TestCase):
    def setUp(self):
        self.cursor = MagicMock()

    def test_existing_court_location_changed(self):
        location_code = "123"
        court_data = {"name": "Existing Court", "type": "CROWN"}
        existing_record = ("0108149c-b4ee-4d73-97db-31e13c516102", "234", "CROWN")

        self.cursor.fetchone.return_value = existing_record

        result = upsert_court_info(self.cursor, location_code, court_data)

        self.assertEqual(result[6], f"Location code changed from {existing_record[1]} to {location_code}")
        self.cursor.execute.assert_has_calls([
            call('SELECT id, location_code, court_type FROM courts WHERE name = %s', (court_data["name"],)),
            call('UPDATE courts SET location_code = %s WHERE name = %s', (location_code, court_data["name"]))
        ])

    def test_existing_court_location_not_changed(self):
        location_code = "234"
        court_data = {"name": "Existing Court", "type": "CROWN"}
        existing_record = ("0108149c-b4ee-4d73-97db-31e13c516102", "234", "CROWN")

        self.cursor.fetchone.return_value = existing_record

        result = upsert_court_info(self.cursor, location_code, court_data)

        self.assertEqual(result[6], "No change")
        self.cursor.execute.assert_called_once_with('SELECT id, location_code, court_type FROM courts WHERE name = %s', (court_data["name"],))

    def test_new_court(self):
        location_code = "456"
        court_data = {"name": "New Court", "type": "MAGISTRATE"}

        self.cursor.fetchone.return_value = None

        result = upsert_court_info(self.cursor, location_code, court_data)

        self.assertEqual(result[6], "New court")
        self.cursor.execute.assert_has_calls([
            call('SELECT id, location_code, court_type FROM courts WHERE name = %s', (court_data["name"],)),
            call('INSERT INTO courts (id, name, court_type, location_code) VALUES (%s, %s, %s, %s)', (unittest.mock.ANY, court_data["name"], court_data["type"], location_code))
        ])

if __name__ == "__main__":
    unittest.main()
