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
3. The app loads with fictional library data and custom covers

## What's included

- **15 audiobooks** across 5 authors and 5 series (3 books each)
- **12 podcasts** with 5 episodes each (60 episodes total)
- **Reading progress** on 6 books
- **Custom cover art** for all books and podcasts

### Series

| Series | Author | Genre |
|--------|--------|-------|
| The Meridian Cycle | Elena Marchetti | Fantasy |
| The Copper Age | James Whitmore | Dystopian |
| The Cartographer's Secret | Aisha Kato | Historical Fiction |
| Clockwork Empire | Marcus Venn | Steampunk |
| The Wanderer's Path | Lena Voronova | Epic Fantasy |

### Podcasts

The Archivist's Diary, Cosmic Café, Sustainable Minds, Untold Stories of Mars, Technology Today, State of Affairs, Global Vibes, Marvels of the Deep, Beyond the Verge, Capital Creators, Media Trends Watch, Climate Report

## Endpoints implemented

All standard ABS endpoints the app uses: login, authorize, libraries, personalized shelves, items, series, search, playback sessions, progress sync, covers, and recent episodes.

## Network security

The app includes a network security config (`res/xml/network_security_config.xml`) that allows cleartext HTTP to `10.0.2.2` (emulator loopback to host). This is required for the mock server to work on the emulator.
