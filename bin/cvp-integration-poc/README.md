# CVP Integration POC

This is a POC for integrating CVP using HTTP requests.

## Installation

```bash
virtualenv venv
source venv/bin/activate
pip install -r requirements.txt
cp .env.example .env
```

Fill in the `.env` file with the appropriate values.

## Usage

Usage: `python main.py room_name rtmps_link`

```bash
python main.py PRE008 rtmps://example.com:443/live/123456
```
