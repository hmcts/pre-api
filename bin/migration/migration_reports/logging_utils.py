import os
import json
import logging
import logging.config


def configure_logging(config_file='logging_config.json'):
    script_dir = os.path.dirname(os.path.abspath(__file__))
    config_path = os.path.join(script_dir, config_file)

    with open(config_path, 'r') as f:
        config = json.load(f)
    logging.config.dictConfig(config)
    return logging.getLogger(__name__)
