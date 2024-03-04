from azure.storage.blob import BlobServiceClient
from azure.core.exceptions import ResourceNotFoundError
import json
import os

connection_string = os.getenv("AZURE_STORAGE_CONNECTION_STRING")

class AzureBlobStorageManager:
    def __init__(self):
        self.blob_service_client = BlobServiceClient.from_connection_string(connection_string)

    def get_recording_duration(self, recording_id):
        try:
            container_client = self.blob_service_client.get_container_client(recording_id)
            blob_list = container_client.list_blobs()
            json_blob_name = next(blob.name for blob in blob_list if blob.name.endswith('.json'))
            json_blob_client = container_client.get_blob_client(json_blob_name)
            json_data = json_blob_client.download_blob().readall()
            metadata = json.loads(json_data)
            duration = metadata["AssetFile"][0]["Duration"]
            return duration
        except ResourceNotFoundError:
            raise ValueError("No blobs found for the recording ID.")
