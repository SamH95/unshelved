# Mock Audiobookshelf Server

A lightweight Flask server that implements the Audiobookshelf API with fictional data, for taking full-fidelity app store screenshots without licensing concerns.

## Setup

```bash
cd dev-resources/mockserver
python3 -m venv .venv
source .venv/bin/activate
pip install flask
```

## Running

```bash
python3 server.py
```

The server starts on `http://0.0.0.0:3000`.

## Connecting from the Android Emulator

1. Start the mock server on your host machine
2. In the app's login screen, enter:
   - **Server:** `http://10.0.2.2:3000`
   - **Username / Password:** anything (all credentials are accepted)
3. The app loads with fictional library data and placeholder covers

## What's included

- **6 audiobooks** across 2 authors, 1 series ("The Meridian Cycle")
- **2 podcasts** with 5 episodes each
- **Reading progress** on 3 books (72%, 35%, 91%)
- **Colored placeholder covers** (400×400 PNGs)

## Regenerating covers

```bash
python3 generate_covers.py
```

## Endpoints implemented

All standard ABS endpoints the app uses: login, authorize, libraries, personalized shelves, items, series, search, playback sessions, progress sync, covers, and recent episodes.
