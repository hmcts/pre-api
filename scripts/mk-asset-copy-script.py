import requests
import sys
import json
import logging
import urllib.parse
from datetime import datetime

BEARER_TOKEN = ""
DEBUG = False

def api_help():

  print ("Command format:\n" \
         "Usage: mk-asset-copy-script ENVIRONMENT [IGNORE_MIGRATED]\n" \
         "\n" \
         "Arguments:\n" \
         "  ENVIRONMENT       Target environment (dev, test, stg, demo, prod)\n" \
         "                    Assets will be copied from old system to new system within the same environment\n" \
         "  IGNORE_MIGRATED   Optional: 'true' to skip already migrated assets\n" \
         "\n" \
         "Example: python mk-asset-copy-script dev true")

try:
  ENVIRONMENT = sys.argv[1]
  IGNORE_MIGRATED = sys.argv[2].lower() == 'true' if len(sys.argv) > 2 else False

except:
  api_help()
  sys.exit(1)

def get_url_old(system):

  if system != "test" and system != "dev" and system != "prod" and system != "stg" and system != "demo":
    print ("System must be TEST, PRE or PROD.")
    raise ExceptionGroup('there were problems', excs)

  if system == "dev":
    return "PRE-MEDIAKIND-DEV"

  return "pre-mediakind-{}".format(system)

def get_url_new(system):

  if system != "test" and system != "dev" and system != "prod" and system != "stg" and system != "demo":
    print ("System must be TEST, PRE or PROD.")
    raise ExceptionGroup('there were problems', excs)

  return "pre-mkio-{}".format(system)


def get_api(url):

  ##### Get function ####

  # Set headers with Bearer token
  headers = {
    "accept": "application/json",
    "Authorization": "Bearer {}".format(BEARER_TOKEN)
  }

  try:
    response = requests.get(url, headers=headers)
    if DEBUG == True:
      print ("Status: {} - {}".format(response.status_code, response.content))
    return response.content
  except Exception as e:
    print (str(e))

def update_source_asset_label(system_url, asset_name):
  """Update source asset with hasBeenMigrated label after successful migration"""

  # First, GET the current asset to retrieve its existing data
  url_get = "https://api.mk.io/api/v1/projects/{}/media/assets/{}".format(system_url, asset_name)

  try:
    json_data = get_api(url_get)
    if not json_data:
      logging.error("Failed to retrieve asset %s for label update", asset_name)
      return

    asset_data = json.loads(json_data)

    # Get existing labels or create new dict
    if "labels" not in asset_data:
      asset_data["labels"] = {}

    # Add hasBeenMigrated label
    asset_data["labels"]["hasBeenMigrated"] = "true"

    # Remove fields that shouldn't be in PUT request
    asset_data.pop("name", None)
    asset_data.pop("id", None)
    asset_data.pop("type", None)
    asset_data.pop("systemData", None)

    # Remove read-only properties
    properties_data = asset_data.get("properties", {})
    properties_data.pop("lastModified", None)
    properties_data.pop("created", None)
    properties_data.pop("assetId", None)

    # PUT the updated asset back
    url_put = "https://api.mk.io/api/v1/projects/{}/media/assets/{}".format(system_url, asset_name)

    headers = {
      "accept": "application/json",
      "Authorization": "Bearer {}".format(BEARER_TOKEN),
      "Content-Type": "application/json"
    }

    response = requests.put(url_put, headers=headers, json=asset_data)

    if response.status_code not in [200, 201]:
      logging.error("Failed to update hasBeenMigrated label for asset %s. Status: %s - Response: %s",
                    asset_name, response.status_code, response.text)
    elif DEBUG:
      print(f"\nUpdated source asset {asset_name} with hasBeenMigrated label")

  except Exception as e:
    logging.error("Exception updating hasBeenMigrated label for asset %s: %s", asset_name, str(e))


def set_api(url, payload, name, type):

  #### Set function ####

  # Set headers with Bearer token
  headers = {
    "accept": "application/json",
    "Authorization": "Bearer {}".format(BEARER_TOKEN),
    "Content-Type": "application/json"
  }
  # Convert to JSON string
  json_object = json.dumps(payload)
  if DEBUG == True:
    print (payload)
  try:
    response = requests.put(url, headers=headers, json=payload)

    # Log error if status code is not 200 or 201
    if response.status_code not in [200, 201]:
      logging.error("PUT request failed for %s %s. Status: %s - Response: %s", type, name, response.status_code, response.text)

    if response.status_code in [200, 201]:
      print (".", end=" ", flush=True)
    else:
      print ("Status: {} - {}".format(response.status_code, response.content))

    return response.status_code

  except Exception as e:
    print (str(e))
    return None



def main():


  # Set up logging
  logging.basicConfig(filename='error_log.txt', level=logging.ERROR,
                      format='%(asctime)s - %(levelname)s - %(message)s')


  try:
    SYSTEM1_URL = get_url_old(ENVIRONMENT)
    SYSTEM2_URL = get_url_new(ENVIRONMENT)
  except Exception as e:
    sys.exit(1)


  # Asset URL
  type = "ASSET"

  skiptoken = 0
  processed_count = 0  # Counter for testing - stop after process_limit assets
  process_limit = 20000  # Safety limit to prevent infinite loops

  while True:

    # Build URL with optional filter for non-migrated assets
    if IGNORE_MIGRATED:
      url_old = "https://api.mk.io/api/v1/projects/{}/media/assets?$skiptoken={}&$label=hasBeenMigrated!=true".format(SYSTEM1_URL, skiptoken)
    else:
      url_old = "https://api.mk.io/api/v1/projects/{}/media/assets?$skiptoken={}".format(SYSTEM1_URL, skiptoken)

    json_data = get_api(url_old)

    data = json.loads(json_data)

    # Extract all sections with a "name" key
    name_sections = [item for item in data.get("value", []) if "name" in item]

    # Print or use the extracted sections
    for section in name_sections:
      #print (section)
      name = section.get("name")
      if not name:
        print ("Skipping entry with no 'name'")

      properties_data = section.get("properties", {})

      # Convert ISO timestamps to epoch seconds (alphanumeric values for labels)
      created_iso = properties_data.get('created')
      last_modified_iso = properties_data.get('lastModified')

      created_epoch = ""
      last_modified_epoch = ""

      if created_iso:
        created_dt = datetime.fromisoformat(created_iso.replace('Z', '+00:00'))
        created_epoch = str(int(created_dt.timestamp()))

      if last_modified_iso:
        last_modified_dt = datetime.fromisoformat(last_modified_iso.replace('Z', '+00:00'))
        last_modified_epoch = str(int(last_modified_dt.timestamp()))

      section["labels"] = {
        "isMigrated": "true",
        "created": created_epoch,
        "lastModified": last_modified_epoch,
      }

      properties_data.pop("lastModified", None)
      properties_data.pop("created", None)
      properties_data.pop("assetId", None)

      section.pop("name", None)
      section.pop("id", None)
      section.pop("type", None)
      section.pop("systemData", None)

      url_new = "https://api.mk.io/api/v1/projects/{}/media/assets/{}".format(SYSTEM2_URL, name)

      # print (f"Uploading asset: {name}")
      print (".", end=" ")

      status_code = set_api(url_new, section, name, type)

      # If migration successful, update source asset with hasBeenMigrated label
      if status_code in [200, 201]:
        update_source_asset_label(SYSTEM1_URL, name)
        processed_count += 1

        # For testing: stop after processing 1 asset
        if processed_count >= process_limit:
          print(f"\n\nTest mode: Stopped after processing {processed_count} asset(s)")
          print(f"Successfully migrated and labeled: {name}")
          return


    # if over 1000 assets get the skiptoken value to read the next batch
    next_link = data.get("@odata.nextLink")
    if next_link:
      decoded_link = urllib.parse.unquote(next_link)
      parsed = urllib.parse.urlparse(decoded_link)
      query_params = urllib.parse.parse_qs(parsed.query)
      skiptoken = query_params.get('$skiptoken', [None])[0]
      print (f"Skiptoken: {skiptoken}")
    else:
      break

  # Policy URL
  type = "POLICY"

  url_old = "https://api.mk.io/api/v1/projects/{}/media/streamingPolicies".format(SYSTEM1_URL)

  json_data = get_api(url_old)

  data = json.loads(json_data)

  # Extract all sections with a "name" key
  name_sections = [item for item in data.get("value", []) if "name" in item]

  # Print or use the extracted sections
  for section in name_sections:
    #print (section)
    name = section.get("name")
    if not name:
      print ("Skipping entry with no 'name'")

    section.pop("id", None)
    section.pop("type", None)
    section.pop("systemData", None)

    url_new = "https://api.mk.io/api/v1/projects/{}/media/streamingPolicies/{}".format(SYSTEM2_URL, name)

    json_data = set_api(url_new, section, name, type)


  # Content Key Policy URL
  type = "CONTENT KEY POLICY"

  url_old = "https://api.mk.io/api/v1/projects/{}/media/contentKeyPolicies".format(SYSTEM1_URL)

  json_data = get_api(url_old)

  data = json.loads(json_data)

  # Extract all sections with a "name" key
  name_sections = [item for item in data.get("value", []) if "name" in item]

  # Print or use the extracted sections
  for section in name_sections:
    #print (section)
    name = section.get("name")
    if not name:
      print ("Skipping entry with no 'name'")

    properties_data = section.get("properties", {})
    properties_data.pop("lastModified", None)
    properties_data.pop("created", None)

    section.pop("id", None)
    section.pop("type", None)
    section.pop("systemData", None)
    section.pop("supplemental", None)

    url_new = "https://api.mk.io/api/v1/projects/{}/media/contentKeyPolicies/{}".format(SYSTEM2_URL, name)

    json_data = set_api(url_new, section, name, type)

if __name__ == "__main__":
  main()


