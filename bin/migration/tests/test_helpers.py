import re

def assert_executemany_query(destination_cursor_mock, expected_query):
    actual_query = destination_cursor_mock.executemany.call_args[0][0]

    expected_query_stripped = re.sub(r"\s+", "", expected_query)
    actual_query_stripped = re.sub(r"\s+", "", actual_query)

    assert expected_query_stripped == actual_query_stripped

