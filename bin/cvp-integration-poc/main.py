import json
import os

from bs4 import BeautifulSoup
from dotenv import load_dotenv
import requests

load_dotenv()

cvp_url = "https://meet.video.justice.gov.uk"


def update_cvp_room_settings(room_name, recording_uri):
    with requests.Session() as session:
        print("Logging in to CVP...")
        response = session.get(f"{cvp_url}/accounts/login/")

        soup = BeautifulSoup(response.text, "html.parser")
        login_form = {
            "username": os.getenv("CVP_USERNAME"),
            "password": os.getenv("CVP_PASSWORD"),
            "csrfmiddlewaretoken": soup.find("input", {"name": "csrfmiddlewaretoken"})[
                "value"
            ],
        }

        response = session.post(f"{cvp_url}/accounts/auth/", data=login_form)

        logged_in = "sessionid" in session.cookies.get_dict()
        if logged_in:
            print("Logged in successfully")
        else:
            print("Failed to log in")
            exit(1)

        print("Fetching CVP room list...")
        soup = BeautifulSoup(response.text, "html.parser")
        ul_element = soup.find('ul', {'class': 'dropdown-menu', 'role': 'menu', 'aria-labelledby': 'dLabel'})
        room_id_map = {}
        if ul_element:
            for li in ul_element.find_all("li"):
                a_tag = li.find("a")
                if a_tag and "href" in a_tag.attrs and a_tag["href"].startswith("/cloudroom/"):
                    room_id_map[a_tag.text.strip()] = a_tag["href"].split("/")[-2]
        print("Fetched CVP room list successfully:")
        print(json.dumps(room_id_map, indent=4))

        if room_name not in room_id_map.keys():
            print(f"Room '{room_name}' not found")
            exit(1)
        
        room_id = room_id_map[room_name]
        print(f"Found room '{room_name}' with ID {room_id}")

        print("Updating CVP room settings...")
        room_url = f"{cvp_url}/cloudroom/{room_id}"
        response = session.get(room_url)

        soup = BeautifulSoup(response.text, "html.parser")
        headers = {
            "X-Csrftoken": soup.find("input", {"name": "csrfmiddlewaretoken"})["value"]
        }
        room_settings = {
            "recording_uri": recording_uri
        }

        response = session.post(
            f"{room_url}/save_settings/", headers=headers, json=room_settings
        )

        if "pin" in response.text:
            print("Updated CVP room settings successfully:")
            print(json.dumps(json.loads(response.text), indent=4))
        else:
            print("Failed to update CVP room settings")
            exit(1)


if __name__ == "__main__":
    update_cvp_room_settings("PRE008", "rtmps://example.com")
