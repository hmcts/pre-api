import json
import logging
import os
import sys

from bs4 import BeautifulSoup
from dotenv import load_dotenv
import requests

load_dotenv()

logger = logging.getLogger(__name__)
cvp_url = os.getenv("CVP_URL")


def update_cvp_room_settings(room_name, recording_uri):
    with requests.Session() as session:
        logger.info("Logging in to CVP...")
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
        if not logged_in:
            logger.error("Failed to log in")
            exit(1)
        logger.info("Logged in successfully")

        logger.info("Fetching CVP room list...")
        soup = BeautifulSoup(response.text, "html.parser")
        ul_element = soup.find(
            "ul",
            {"class": "dropdown-menu", "role": "menu", "aria-labelledby": "dLabel"},
        )
        if not ul_element:
            logger.error("Failed to fetch CVP room list")
            exit(1)

        room_id_map = {}
        for li in ul_element.find_all("li"):
            a_tag = li.find("a")
            if (
                a_tag
                and "href" in a_tag.attrs
                and a_tag["href"].startswith("/cloudroom/")
            ):
                room_id_map[a_tag.text.strip()] = a_tag["href"].split("/")[-2]
        logger.info("Fetched CVP room list successfully:")
        logger.info(json.dumps(room_id_map, indent=4))

        if room_name not in room_id_map.keys():
            logger.error(f"Room '{room_name}' not found")
            exit(1)

        room_id = room_id_map[room_name]
        logger.info(f"Found room '{room_name}' with ID {room_id}")

        logger.info("Updating CVP room settings...")
        room_url = f"{cvp_url}/cloudroom/{room_id}"
        response = session.get(room_url)

        soup = BeautifulSoup(response.text, "html.parser")
        headers = {
            "X-Csrftoken": soup.find("input", {"name": "csrfmiddlewaretoken"})["value"]
        }
        room_settings = {"recording_uri": recording_uri}

        response = session.post(
            f"{room_url}/save_settings/", headers=headers, json=room_settings
        )

        if "pin" not in response.text:
            logger.error("Failed to update CVP room settings")
            exit(1)

        logger.info("Updated CVP room settings successfully:")
        logger.info(json.dumps(json.loads(response.text), indent=4))


if __name__ == "__main__":
    if len(sys.argv) < 3:
        logger.error("Usage: python main.py room_name rtmps_link")
        exit(1)

    room_name = sys.argv[1]
    recording_uri = sys.argv[2]
    update_cvp_room_settings(room_name, recording_uri)
