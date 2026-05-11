"""Mock Audiobookshelf server for app store screenshots."""
import os
import time
from flask import Flask, jsonify, request, send_from_directory

app = Flask(__name__)

COVERS_DIR = os.path.join(os.path.dirname(__file__), "covers")
BOOK_COVERS_DIR = os.path.join(COVERS_DIR, "books")
PODCAST_COVERS_DIR = os.path.join(COVERS_DIR, "podcasts")

# ─── Fictional Data ───────────────────────────────────────────────────────────

BOOK_LIBRARY_ID = "lib-books"
PODCAST_LIBRARY_ID = "lib-podcasts"
FOLDER_ID = "folder-1"

BOOKS = [
    # Series 1: The Meridian Cycle (Fantasy/Sci-Fi) — Elena Marchetti
    {
        "id": "b1",
        "title": "The Midnight Garden",
        "author": "Elena Marchetti",
        "narrator": "Sophie Laurent",
        "duration": 43200.0,
        "description": "A haunting tale of memory and loss set in a mysterious garden that only blooms at night. When Clara inherits her grandmother's estate, she discovers that the garden holds secrets spanning generations — and that some memories are better left buried beneath the moonlit soil.",
        "year": "2022",
        "series": "The Meridian Cycle",
        "sequence": "1",
        "genres": ["Fantasy", "Literary Fiction"],
    },
    {
        "id": "b2",
        "title": "Echoes of Tomorrow",
        "author": "Elena Marchetti",
        "narrator": "Sophie Laurent",
        "duration": 52800.0,
        "description": "In a world where echoes of the future can be heard by those who listen carefully, Dr. Maren Solberg must decide what warnings to heed and which futures to let unfold. A sweeping science fiction epic about fate, free will, and the courage to face the unknown.",
        "year": "2023",
        "series": "The Meridian Cycle",
        "sequence": "2",
        "genres": ["Fantasy", "Science Fiction"],
    },
    {
        "id": "b3",
        "title": "Silent Meridian",
        "author": "Elena Marchetti",
        "narrator": "Sophie Laurent",
        "duration": 39600.0,
        "description": "The silence between worlds holds a secret that could unravel reality itself. When cartographer Elias Thorne maps an impossible coordinate, he stumbles into a conspiracy that stretches across dimensions. The third volume in the acclaimed Meridian Cycle.",
        "year": "2024",
        "series": "The Meridian Cycle",
        "sequence": "3",
        "genres": ["Fantasy", "Thriller"],
    },
    # Series 2: The Copper Age (Dystopian) — James Whitmore
    {
        "id": "b4",
        "title": "Beneath the Copper Sky",
        "author": "James Whitmore",
        "narrator": "David Chen",
        "duration": 34200.0,
        "description": "Under skies turned copper by decades of unchecked industry, a revolution brews in whispered code. Factory worker Nadia discovers she can decode the messages hidden in the smog patterns — messages that could topple an empire or doom a city.",
        "year": "2023",
        "series": "The Copper Age",
        "sequence": "1",
        "genres": ["Dystopian", "Thriller"],
    },
    {
        "id": "b5",
        "title": "The Iron Horizon",
        "author": "James Whitmore",
        "narrator": "David Chen",
        "duration": 38400.0,
        "description": "As Nadia's revolution spreads beyond the factory districts, the regime responds with an iron curtain of surveillance drones. In the wastelands beyond the city, a forgotten technology offers hope — if the rebels can reach it before the copper sky falls.",
        "year": "2024",
        "series": "The Copper Age",
        "sequence": "2",
        "genres": ["Dystopian", "Science Fiction"],
    },
    {
        "id": "b6",
        "title": "Rust and Reckoning",
        "author": "James Whitmore",
        "narrator": "David Chen",
        "duration": 41000.0,
        "description": "The final confrontation between Nadia's rebels and the industrial oligarchy unfolds across three continents. Ancient machines awaken, alliances shatter, and the true cost of revolution becomes clear in this explosive conclusion to The Copper Age.",
        "year": "2025",
        "series": "The Copper Age",
        "sequence": "3",
        "genres": ["Dystopian", "Thriller"],
    },
    # Series 3: The Cartographer's Secret (Historical Mystery) — Aisha Kato
    {
        "id": "b7",
        "title": "A Thousand Paper Cranes",
        "author": "Aisha Kato",
        "narrator": "Margot Dubois",
        "duration": 28800.0,
        "description": "A multi-generational story spanning three continents and seventy years, following the Nakamura family from post-war Hiroshima through the bustling streets of 1970s São Paulo to present-day Toronto. Each folded crane carries a wish — and a secret.",
        "year": "2021",
        "series": "The Cartographer's Secret",
        "sequence": "1",
        "genres": ["Historical Fiction", "Mystery"],
    },
    {
        "id": "b8",
        "title": "The Vermillion Map",
        "author": "Aisha Kato",
        "narrator": "Margot Dubois",
        "duration": 32400.0,
        "description": "When a rare 16th-century map surfaces in a Tokyo auction house, historian Yuki Nakamura recognizes her grandmother's handwriting in the margin notes. The map leads her through the hidden waterways of Venice to a secret her family has protected for generations.",
        "year": "2022",
        "series": "The Cartographer's Secret",
        "sequence": "2",
        "genres": ["Historical Fiction", "Mystery"],
    },
    {
        "id": "b9",
        "title": "Longitude of Shadows",
        "author": "Aisha Kato",
        "narrator": "Margot Dubois",
        "duration": 35100.0,
        "description": "The final map in the Nakamura collection points to a coordinate that doesn't exist on any modern chart. Yuki races against a shadowy collector's guild to decode the last cartographer's secret before history is rewritten forever.",
        "year": "2023",
        "series": "The Cartographer's Secret",
        "sequence": "3",
        "genres": ["Historical Fiction", "Thriller"],
    },
    # Series 4: Clockwork Empire (Steampunk) — Marcus Venn
    {
        "id": "b10",
        "title": "The Clockwork Atlas",
        "author": "Marcus Venn",
        "narrator": "Thomas Ashford",
        "duration": 46800.0,
        "description": "An atlas that maps not geography but time itself falls into the unlikely hands of a street urchin in Victorian London. As powerful factions close in, young Pip must learn to read the atlas before its knowledge reshapes history forever.",
        "year": "2022",
        "series": "Clockwork Empire",
        "sequence": "1",
        "genres": ["Steampunk", "Adventure"],
    },
    {
        "id": "b11",
        "title": "Gears of Rebellion",
        "author": "Marcus Venn",
        "narrator": "Thomas Ashford",
        "duration": 44200.0,
        "description": "Five years after mastering the atlas, Pip commands an airship fleet of rebels against the Chronarchs — a cabal of time-manipulating aristocrats who've enslaved London's working class. But the atlas reveals a terrible truth: rebellion was always part of their plan.",
        "year": "2023",
        "series": "Clockwork Empire",
        "sequence": "2",
        "genres": ["Steampunk", "Adventure"],
    },
    {
        "id": "b12",
        "title": "The Brass Meridian",
        "author": "Marcus Venn",
        "narrator": "Thomas Ashford",
        "duration": 49000.0,
        "description": "At the brass meridian — the temporal prime meridian running through the heart of a dying clockwork sun — Pip must make the ultimate choice: reset time and erase everything, or let the gears of the universe wind down forever.",
        "year": "2024",
        "series": "Clockwork Empire",
        "sequence": "3",
        "genres": ["Steampunk", "Science Fiction"],
    },
    # Series 5: The Wanderer's Path (Epic Fantasy) — Lena Voronova
    {
        "id": "b13",
        "title": "Ashes of the Old Road",
        "author": "Lena Voronova",
        "narrator": "Sophie Laurent",
        "duration": 54000.0,
        "description": "In a world where ancient roads possess memory, wanderer Kael Ashborne walks a forgotten path that leads through the ruins of fallen kingdoms. Each step forward is a step backward in time, and the road demands a price for its secrets.",
        "year": "2023",
        "series": "The Wanderer's Path",
        "sequence": "1",
        "genres": ["Epic Fantasy", "Adventure"],
    },
    {
        "id": "b14",
        "title": "The Shattered Compass",
        "author": "Lena Voronova",
        "narrator": "Sophie Laurent",
        "duration": 48600.0,
        "description": "Kael's journey leads him to the Shattered Compass — a legendary artifact split into four pieces and scattered across the cardinal realms. With each fragment recovered, the old road reveals more of its terrible purpose, and Kael realizes he may be walking toward the end of all paths.",
        "year": "2024",
        "series": "The Wanderer's Path",
        "sequence": "2",
        "genres": ["Epic Fantasy"],
    },
    {
        "id": "b15",
        "title": "Where All Roads End",
        "author": "Lena Voronova",
        "narrator": "Sophie Laurent",
        "duration": 51200.0,
        "description": "At the convergence of every road ever walked, Kael faces the Pathkeeper — the entity that wove the first road from starlight and sorrow. To save the world from unraveling, he must choose: become the new Pathkeeper, or let every road crumble to dust.",
        "year": "2025",
        "series": "The Wanderer's Path",
        "sequence": "3",
        "genres": ["Epic Fantasy", "Adventure"],
    },
]

PODCAST_EPISODES_P1 = [
    {"id": "e1", "title": "The Lost Archive of Prague", "duration": 4200.0, "publishedAt": int(time.time() * 1000) - 86400000},
    {"id": "e2", "title": "Forgotten Voices of the Renaissance", "duration": 3600.0, "publishedAt": int(time.time() * 1000) - 259200000},
    {"id": "e3", "title": "Manuscripts in the Margins", "duration": 3900.0, "publishedAt": int(time.time() * 1000) - 432000000},
    {"id": "e4", "title": "Secrets of the Vatican Library", "duration": 3300.0, "publishedAt": int(time.time() * 1000) - 604800000},
    {"id": "e5", "title": "The Ink Trade Routes", "duration": 2700.0, "publishedAt": int(time.time() * 1000) - 777600000},
]

PODCAST_EPISODES_P2 = [
    {"id": "e6", "title": "Dark Matter Coffee Break", "duration": 2700.0, "publishedAt": int(time.time() * 1000) - 172800000},
    {"id": "e7", "title": "Exoplanet Happy Hour", "duration": 3300.0, "publishedAt": int(time.time() * 1000) - 345600000},
    {"id": "e8", "title": "Nebulae and Lattes", "duration": 2400.0, "publishedAt": int(time.time() * 1000) - 518400000},
    {"id": "e9", "title": "The Gravity of Good Espresso", "duration": 2100.0, "publishedAt": int(time.time() * 1000) - 691200000},
    {"id": "e10", "title": "Stellar Origins of Flavor", "duration": 2850.0, "publishedAt": int(time.time() * 1000) - 864000000},
]

PODCAST_EPISODES_P3 = [
    {"id": "e11", "title": "Zero Waste Living", "duration": 2500.0, "publishedAt": int(time.time() * 1000) - 86400000},
    {"id": "e12", "title": "Circular Economy 101", "duration": 3100.0, "publishedAt": int(time.time() * 1000) - 259200000},
    {"id": "e13", "title": "Regenerative Agriculture", "duration": 2800.0, "publishedAt": int(time.time() * 1000) - 432000000},
    {"id": "e14", "title": "The Carbon Footprint Myth", "duration": 3400.0, "publishedAt": int(time.time() * 1000) - 604800000},
    {"id": "e15", "title": "Ocean Plastic Solutions", "duration": 2600.0, "publishedAt": int(time.time() * 1000) - 777600000},
]

PODCAST_EPISODES_P4 = [
    {"id": "e16", "title": "Olympus Mons Base Camp", "duration": 3200.0, "publishedAt": int(time.time() * 1000) - 172800000},
    {"id": "e17", "title": "The First Martian Sunrise", "duration": 2900.0, "publishedAt": int(time.time() * 1000) - 345600000},
    {"id": "e18", "title": "Terraforming Dreams", "duration": 3600.0, "publishedAt": int(time.time() * 1000) - 518400000},
    {"id": "e19", "title": "Water on the Red Planet", "duration": 2700.0, "publishedAt": int(time.time() * 1000) - 691200000},
    {"id": "e20", "title": "Dust Storms and Survival", "duration": 3100.0, "publishedAt": int(time.time() * 1000) - 864000000},
]

PODCAST_EPISODES_P5 = [
    {"id": "e21", "title": "AI in Your Pocket", "duration": 2400.0, "publishedAt": int(time.time() * 1000) - 86400000},
    {"id": "e22", "title": "Quantum Computing Simplified", "duration": 3500.0, "publishedAt": int(time.time() * 1000) - 259200000},
    {"id": "e23", "title": "The Future of Wearables", "duration": 2800.0, "publishedAt": int(time.time() * 1000) - 432000000},
    {"id": "e24", "title": "Blockchain Beyond Crypto", "duration": 3000.0, "publishedAt": int(time.time() * 1000) - 604800000},
    {"id": "e25", "title": "5G and the Connected World", "duration": 2600.0, "publishedAt": int(time.time() * 1000) - 777600000},
]

PODCAST_EPISODES_P6 = [
    {"id": "e26", "title": "Election Cycles Decoded", "duration": 3800.0, "publishedAt": int(time.time() * 1000) - 172800000},
    {"id": "e27", "title": "Diplomacy in the Digital Age", "duration": 3200.0, "publishedAt": int(time.time() * 1000) - 345600000},
    {"id": "e28", "title": "Trade Wars Explained", "duration": 2900.0, "publishedAt": int(time.time() * 1000) - 518400000},
    {"id": "e29", "title": "The Rise of City-States", "duration": 3400.0, "publishedAt": int(time.time() * 1000) - 691200000},
    {"id": "e30", "title": "Democracy Under Pressure", "duration": 3100.0, "publishedAt": int(time.time() * 1000) - 864000000},
]

PODCAST_EPISODES_P7 = [
    {"id": "e31", "title": "K-Pop's Global Takeover", "duration": 2500.0, "publishedAt": int(time.time() * 1000) - 86400000},
    {"id": "e32", "title": "Afrobeats Rising", "duration": 2800.0, "publishedAt": int(time.time() * 1000) - 259200000},
    {"id": "e33", "title": "Latin Music Renaissance", "duration": 3000.0, "publishedAt": int(time.time() * 1000) - 432000000},
    {"id": "e34", "title": "Sounds of the Silk Road", "duration": 2600.0, "publishedAt": int(time.time() * 1000) - 604800000},
    {"id": "e35", "title": "Nordic Folk Revival", "duration": 2400.0, "publishedAt": int(time.time() * 1000) - 777600000},
]

PODCAST_EPISODES_P8 = [
    {"id": "e36", "title": "The Mariana Trench Expedition", "duration": 3600.0, "publishedAt": int(time.time() * 1000) - 172800000},
    {"id": "e37", "title": "Bioluminescent Wonders", "duration": 2700.0, "publishedAt": int(time.time() * 1000) - 345600000},
    {"id": "e38", "title": "Coral Reef Resurrection", "duration": 3200.0, "publishedAt": int(time.time() * 1000) - 518400000},
    {"id": "e39", "title": "Giant Squid Encounters", "duration": 2900.0, "publishedAt": int(time.time() * 1000) - 691200000},
    {"id": "e40", "title": "Underwater Volcanoes", "duration": 3400.0, "publishedAt": int(time.time() * 1000) - 864000000},
]

PODCAST_EPISODES_P9 = [
    {"id": "e41", "title": "Crossing the Event Horizon", "duration": 3300.0, "publishedAt": int(time.time() * 1000) - 86400000},
    {"id": "e42", "title": "Life After Earth", "duration": 2800.0, "publishedAt": int(time.time() * 1000) - 259200000},
    {"id": "e43", "title": "Interstellar Navigation", "duration": 3100.0, "publishedAt": int(time.time() * 1000) - 432000000},
    {"id": "e44", "title": "The Dyson Sphere Debate", "duration": 3500.0, "publishedAt": int(time.time() * 1000) - 604800000},
    {"id": "e45", "title": "First Contact Protocols", "duration": 2600.0, "publishedAt": int(time.time() * 1000) - 777600000},
]

PODCAST_EPISODES_P10 = [
    {"id": "e46", "title": "From Garage to Empire", "duration": 2900.0, "publishedAt": int(time.time() * 1000) - 172800000},
    {"id": "e47", "title": "The VC Mindset", "duration": 3200.0, "publishedAt": int(time.time() * 1000) - 345600000},
    {"id": "e48", "title": "Scaling Without Burning Out", "duration": 2700.0, "publishedAt": int(time.time() * 1000) - 518400000},
    {"id": "e49", "title": "Pivot or Persevere", "duration": 3000.0, "publishedAt": int(time.time() * 1000) - 691200000},
    {"id": "e50", "title": "Exit Strategies", "duration": 2500.0, "publishedAt": int(time.time() * 1000) - 864000000},
]

PODCAST_EPISODES_P11 = [
    {"id": "e51", "title": "The Streaming Wars", "duration": 3400.0, "publishedAt": int(time.time() * 1000) - 86400000},
    {"id": "e52", "title": "Podcasting's Golden Age", "duration": 2600.0, "publishedAt": int(time.time() * 1000) - 259200000},
    {"id": "e53", "title": "Social Media Fatigue", "duration": 3100.0, "publishedAt": int(time.time() * 1000) - 432000000},
    {"id": "e54", "title": "The Newsletter Revolution", "duration": 2800.0, "publishedAt": int(time.time() * 1000) - 604800000},
    {"id": "e55", "title": "AI-Generated Content", "duration": 3300.0, "publishedAt": int(time.time() * 1000) - 777600000},
]

PODCAST_EPISODES_P12 = [
    {"id": "e56", "title": "Arctic Ice Melt Update", "duration": 3000.0, "publishedAt": int(time.time() * 1000) - 172800000},
    {"id": "e57", "title": "Wildfires and Wind Patterns", "duration": 3500.0, "publishedAt": int(time.time() * 1000) - 345600000},
    {"id": "e58", "title": "Rising Seas, Sinking Cities", "duration": 2900.0, "publishedAt": int(time.time() * 1000) - 518400000},
    {"id": "e59", "title": "Renewable Energy Progress", "duration": 3200.0, "publishedAt": int(time.time() * 1000) - 691200000},
    {"id": "e60", "title": "The Heat Dome Phenomenon", "duration": 2700.0, "publishedAt": int(time.time() * 1000) - 864000000},
]

PROGRESS = {
    "b1": {"progress": 0.72, "currentTime": 31104.0, "duration": 43200.0},
    "b2": {"progress": 0.35, "currentTime": 18480.0, "duration": 52800.0},
    "b4": {"progress": 0.91, "currentTime": 31122.0, "duration": 34200.0},
    "b7": {"progress": 0.55, "currentTime": 15840.0, "duration": 28800.0},
    "b10": {"progress": 0.18, "currentTime": 8424.0, "duration": 46800.0},
    "b13": {"progress": 0.63, "currentTime": 34020.0, "duration": 54000.0},
}

# ─── Helper functions ─────────────────────────────────────────────────────────


def make_book_media(book):
    chapters = []
    num_chapters = max(1, int(book["duration"] / 3600))
    chapter_dur = book["duration"] / num_chapters
    for i in range(num_chapters):
        chapters.append({
            "id": i,
            "start": i * chapter_dur,
            "end": (i + 1) * chapter_dur,
            "title": f"Chapter {i + 1}",
        })

    series_entries = []
    if book["series"]:
        series_id = next((s["id"] for s in SERIES_DATA if s["name"] == book["series"]), f"series-{book['id']}")
        series_entries.append({
            "id": series_id,
            "name": book["series"],
            "sequence": book["sequence"],
        })

    return {
        "id": f"media-{book['id']}",
        "metadata": {
            "title": book["title"],
            "titleIgnorePrefix": book["title"],
            "subtitle": None,
            "authorName": book["author"],
            "narratorName": book["narrator"],
            "seriesName": book["series"],
            "genres": book["genres"],
            "publishedYear": book["year"],
            "description": book["description"],
            "language": "en",
            "explicit": False,
            "authors": [{"id": f"author-{book['id']}", "name": book["author"]}],
            "series": series_entries,
        },
        "coverPath": None,
        "duration": book["duration"],
        "numTracks": num_chapters,
        "numAudioFiles": num_chapters,
        "numChapters": num_chapters,
        "chapters": chapters,
        "audioFiles": [{
            "index": i,
            "ino": f"ino-{book['id']}-{i}",
            "metadata": {
                "filename": f"chapter_{i+1}.m4b",
                "ext": ".m4b",
                "path": f"/audiobooks/{book['title']}/chapter_{i+1}.m4b",
                "relPath": f"chapter_{i+1}.m4b",
                "size": int(chapter_dur * 16000),
            },
            "duration": chapter_dur,
            "bitRate": 128000,
            "codec": "aac",
            "mimeType": "audio/mp4",
        } for i in range(num_chapters)],
    }


def make_library_item(book):
    return {
        "id": book["id"],
        "ino": f"ino-{book['id']}",
        "libraryId": BOOK_LIBRARY_ID,
        "mediaType": "book",
        "media": make_book_media(book),
        "addedAt": int(time.time() * 1000) - 86400000 * (6 - BOOKS.index(book)),
        "updatedAt": int(time.time() * 1000),
        "numFiles": max(1, int(book["duration"] / 3600)),
        "size": int(book["duration"] * 16000),
        "progressLastUpdate": int(time.time() * 1000),
    }


def make_podcast_item(podcast_id, title, author, episodes):
    return {
        "id": podcast_id,
        "ino": f"ino-{podcast_id}",
        "libraryId": PODCAST_LIBRARY_ID,
        "mediaType": "podcast",
        "media": {
            "id": f"media-{podcast_id}",
            "metadata": {
                "title": title,
                "titleIgnorePrefix": title,
                "author": author,
                "description": f"A fascinating podcast exploring {title.lower()}.",
                "releaseDate": "2024-01-15",
                "genres": ["Education"],
                "feedUrl": f"https://example.com/feed/{podcast_id}",
                "imageUrl": None,
                "explicit": False,
                "language": "en",
                "type": "episodic",
            },
            "coverPath": None,
            "tags": [],
            "numEpisodes": len(episodes),
            "autoDownloadEpisodes": False,
            "episodes": [make_episode(podcast_id, ep) for ep in episodes],
        },
        "addedAt": int(time.time() * 1000) - 604800000,
        "updatedAt": int(time.time() * 1000),
        "numFiles": len(episodes),
        "size": sum(int(ep["duration"] * 16000) for ep in episodes),
        "progressLastUpdate": 0,
    }


def make_episode(podcast_id, ep):
    return {
        "id": ep["id"],
        "libraryItemId": podcast_id,
        "podcastId": f"media-{podcast_id}",
        "index": None,
        "season": None,
        "episode": None,
        "episodeType": "full",
        "title": ep["title"],
        "subtitle": None,
        "description": f"In this episode, we explore {ep['title'].lower()}.",
        "pubDate": None,
        "publishedAt": ep["publishedAt"],
        "audioFile": {
            "index": 0,
            "ino": f"ino-{ep['id']}",
            "metadata": {
                "filename": f"{ep['id']}.mp3",
                "ext": ".mp3",
                "path": f"/podcasts/{podcast_id}/{ep['id']}.mp3",
                "relPath": f"{ep['id']}.mp3",
                "size": int(ep["duration"] * 16000),
            },
            "duration": ep["duration"],
            "bitRate": 128000,
            "codec": "mp3",
            "mimeType": "audio/mpeg",
        },
        "chapters": [],
        "duration": ep["duration"],
        "size": int(ep["duration"] * 16000),
    }


def make_progress_dto(item_id):
    p = PROGRESS.get(item_id)
    if not p:
        return None
    return {
        "id": f"prog-{item_id}",
        "libraryItemId": item_id,
        "episodeId": None,
        "duration": p["duration"],
        "progress": p["progress"],
        "currentTime": p["currentTime"],
        "isFinished": p["progress"] >= 1.0,
        "hideFromContinueListening": False,
        "lastUpdate": int(time.time() * 1000),
        "startedAt": int(time.time() * 1000) - 86400000,
        "finishedAt": None,
    }


SERIES_DATA = [
    {"id": "series-meridian", "name": "The Meridian Cycle"},
    {"id": "series-copper", "name": "The Copper Age"},
    {"id": "series-cartographer", "name": "The Cartographer's Secret"},
    {"id": "series-clockwork", "name": "Clockwork Empire"},
    {"id": "series-wanderer", "name": "The Wanderer's Path"},
]


def get_series_books(series_name):
    return [item for item in LIBRARY_ITEMS if item["media"]["metadata"]["seriesName"] == series_name]


def make_series_entry(series_info):
    books = get_series_books(series_info["name"])
    return {
        "id": series_info["id"],
        "name": series_info["name"],
        "description": None,
        "addedAt": int(time.time() * 1000) - 604800000,
        "updatedAt": int(time.time() * 1000),
        "totalDuration": sum(b["media"]["duration"] for b in books),
        "books": books,
        "progress": {
            "libraryItemIds": [b["id"] for b in books],
            "libraryItemIdsFinished": [],
            "isFinished": False,
        },
    }


# Pre-build items
LIBRARY_ITEMS = [make_library_item(b) for b in BOOKS]
PODCAST_ITEMS = [
    make_podcast_item("p1", "The Archivist's Diary", "Helena Cross", PODCAST_EPISODES_P1),
    make_podcast_item("p2", "Cosmic Café", "Raj Patel", PODCAST_EPISODES_P2),
    make_podcast_item("p3", "Sustainable Minds", "Olivia Green", PODCAST_EPISODES_P3),
    make_podcast_item("p4", "Untold Stories of Mars", "Dr. Nathan Cole", PODCAST_EPISODES_P4),
    make_podcast_item("p5", "Technology Today", "Mira Santos", PODCAST_EPISODES_P5),
    make_podcast_item("p6", "State of Affairs", "Jonathan Meyers", PODCAST_EPISODES_P6),
    make_podcast_item("p7", "Global Vibes", "Amara Okafor", PODCAST_EPISODES_P7),
    make_podcast_item("p8", "Marvels of the Deep", "Dr. Isla Chen", PODCAST_EPISODES_P8),
    make_podcast_item("p9", "Beyond the Verge", "Felix Harmon", PODCAST_EPISODES_P9),
    make_podcast_item("p10", "Capital Creators", "Priya Sharma", PODCAST_EPISODES_P10),
    make_podcast_item("p11", "Media Trends Watch", "Derek Walsh", PODCAST_EPISODES_P11),
    make_podcast_item("p12", "Climate Report", "Dr. Lena Voss", PODCAST_EPISODES_P12),
]

# ─── Auth Endpoints ───────────────────────────────────────────────────────────


@app.route("/login", methods=["POST"])
def login():
    return jsonify({
        "user": {
            "id": "user-1",
            "username": request.json.get("username", "demo") if request.json else "demo",
            "email": None,
            "type": "user",
            "token": "mock-token-12345",
            "accessToken": "mock-token-12345",
            "refreshToken": "mock-refresh-12345",
            "mediaProgress": [make_progress_dto(k) for k in PROGRESS],
        },
        "userDefaultLibraryId": BOOK_LIBRARY_ID,
    })


@app.route("/api/authorize", methods=["POST"])
def authorize():
    return jsonify({
        "user": {
            "id": "user-1",
            "username": "demo",
            "email": None,
            "type": "user",
            "token": "mock-token-12345",
            "accessToken": "mock-token-12345",
            "refreshToken": "mock-refresh-12345",
            "mediaProgress": [make_progress_dto(k) for k in PROGRESS],
        },
        "userDefaultLibraryId": BOOK_LIBRARY_ID,
    })


@app.route("/auth/refresh", methods=["POST"])
def refresh_token():
    return jsonify({
        "user": {
            "id": "user-1",
            "username": "demo",
            "email": None,
            "type": "user",
            "accessToken": "mock-token-12345",
            "refreshToken": "mock-refresh-12345",
        },
        "userDefaultLibraryId": BOOK_LIBRARY_ID,
    })


@app.route("/logout", methods=["POST"])
def logout():
    return jsonify({"success": True})


@app.route("/api/me", methods=["GET"])
def get_me():
    return jsonify({
        "id": "user-1",
        "username": "demo",
        "email": None,
        "mediaProgress": [make_progress_dto(k) for k in PROGRESS],
    })


# ─── Library Endpoints ────────────────────────────────────────────────────────


@app.route("/api/libraries", methods=["GET"])
def get_libraries():
    return jsonify({
        "libraries": [
            {
                "id": BOOK_LIBRARY_ID,
                "name": "Audiobooks",
                "mediaType": "book",
                "icon": "database",
                "folders": [{"id": FOLDER_ID, "fullPath": "/audiobooks"}],
            },
            {
                "id": PODCAST_LIBRARY_ID,
                "name": "Podcasts",
                "mediaType": "podcast",
                "icon": "podcast",
                "folders": [{"id": "folder-2", "fullPath": "/podcasts"}],
            },
        ]
    })


@app.route("/api/libraries/<library_id>/personalized", methods=["GET"])
def get_personalized(library_id):
    if library_id == PODCAST_LIBRARY_ID:
        shelves = [
            {
                "id": "continue-listening",
                "label": "Continue Listening",
                "labelStringKey": "continue-listening",
                "type": "podcast",
                "entities": [
                    {**PODCAST_ITEMS[0], "recentEpisode": make_episode("p1", PODCAST_EPISODES_P1[0])},
                    {**PODCAST_ITEMS[3], "recentEpisode": make_episode("p4", PODCAST_EPISODES_P4[0])},
                ],
                "category": "recentlyListened",
            },
            {
                "id": "newest-episodes",
                "label": "Newest Episodes",
                "labelStringKey": "newest-episodes",
                "type": "episode",
                "entities": PODCAST_ITEMS[:6],
                "category": "newestEpisodes",
            },
            {
                "id": "recently-added",
                "label": "Recently Added",
                "labelStringKey": "recently-added",
                "type": "podcast",
                "entities": PODCAST_ITEMS[6:],
                "category": "newestItems",
            },
        ]
    else:
        in_progress_items = [item for item in LIBRARY_ITEMS if item["id"] in PROGRESS]
        shelves = [
            {
                "id": "continue-listening",
                "label": "Continue Listening",
                "labelStringKey": "continue-listening",
                "type": "book",
                "entities": in_progress_items,
                "category": "recentlyListened",
            },
            {
                "id": "continue-series",
                "label": "Continue Series",
                "labelStringKey": "continue-series",
                "type": "book",
                "entities": [LIBRARY_ITEMS[1], LIBRARY_ITEMS[4], LIBRARY_ITEMS[10]],
                "category": "continueSeries",
            },
            {
                "id": "recently-added",
                "label": "Recently Added",
                "labelStringKey": "recently-added",
                "type": "book",
                "entities": LIBRARY_ITEMS[-5:],
                "category": "newestItems",
            },
            {
                "id": "recommended",
                "label": "Discover",
                "labelStringKey": "discover",
                "type": "book",
                "entities": [LIBRARY_ITEMS[9], LIBRARY_ITEMS[6], LIBRARY_ITEMS[11], LIBRARY_ITEMS[14]],
                "category": "recommended",
            },
        ]
    return jsonify(shelves)


@app.route("/api/libraries/<library_id>/items", methods=["GET"])
def get_library_items(library_id):
    if library_id == PODCAST_LIBRARY_ID:
        items = PODCAST_ITEMS
    else:
        items = LIBRARY_ITEMS

    page = int(request.args.get("page", 0))
    limit = int(request.args.get("limit", 100))
    return jsonify({
        "results": items,
        "total": len(items),
        "limit": limit,
        "page": page,
        "numPages": 1,
        "sortBy": "media.metadata.title",
        "sortDesc": False,
        "filterBy": None,
    })


@app.route("/api/libraries/<library_id>/series", methods=["GET"])
def get_library_series(library_id):
    series_list = [make_series_entry(s) for s in SERIES_DATA]
    return jsonify({
        "results": series_list,
        "total": len(series_list),
        "limit": 100,
        "page": 0,
    })


@app.route("/api/libraries/<library_id>/filterdata", methods=["GET"])
def get_filterdata(library_id):
    authors = list({b["author"] for b in BOOKS})
    genres = list({g for b in BOOKS for g in b["genres"]})
    narrators = list({b["narrator"] for b in BOOKS})
    return jsonify({
        "authors": [{"id": f"auth-{a.replace(' ', '')}", "name": a} for a in sorted(authors)],
        "genres": sorted(genres),
        "tags": [],
        "series": [{"id": s["id"], "name": s["name"]} for s in SERIES_DATA],
        "narrators": sorted(narrators),
        "languages": ["en"],
    })


@app.route("/api/libraries/<library_id>/recent-episodes", methods=["GET"])
def get_recent_episodes(library_id):
    all_episodes = []
    podcast_episode_map = [
        ("p1", "The Archivist's Diary", "Helena Cross", PODCAST_EPISODES_P1),
        ("p2", "Cosmic Café", "Raj Patel", PODCAST_EPISODES_P2),
        ("p3", "Sustainable Minds", "Olivia Green", PODCAST_EPISODES_P3),
        ("p4", "Untold Stories of Mars", "Dr. Nathan Cole", PODCAST_EPISODES_P4),
        ("p5", "Technology Today", "Mira Santos", PODCAST_EPISODES_P5),
        ("p6", "State of Affairs", "Jonathan Meyers", PODCAST_EPISODES_P6),
        ("p7", "Global Vibes", "Amara Okafor", PODCAST_EPISODES_P7),
        ("p8", "Marvels of the Deep", "Dr. Isla Chen", PODCAST_EPISODES_P8),
        ("p9", "Beyond the Verge", "Felix Harmon", PODCAST_EPISODES_P9),
        ("p10", "Capital Creators", "Priya Sharma", PODCAST_EPISODES_P10),
        ("p11", "Media Trends Watch", "Derek Walsh", PODCAST_EPISODES_P11),
        ("p12", "Climate Report", "Dr. Lena Voss", PODCAST_EPISODES_P12),
    ]
    for pid, title, author, episodes in podcast_episode_map:
        for ep in episodes:
            e = make_episode(pid, ep)
            e["podcastTitle"] = title
            e["podcastAuthor"] = author
            all_episodes.append(e)

    all_episodes.sort(key=lambda x: x["publishedAt"], reverse=True)

    page = int(request.args.get("page", 0))
    limit = int(request.args.get("limit", 25))
    start = page * limit
    end = start + limit
    return jsonify({
        "episodes": all_episodes[start:end],
        "total": len(all_episodes),
        "limit": limit,
        "page": page,
    })


@app.route("/api/libraries/<library_id>/search", methods=["GET"])
def search_library(library_id):
    q = (request.args.get("q", "")).lower()
    if library_id == PODCAST_LIBRARY_ID:
        matches = [item for item in PODCAST_ITEMS if q in item["media"]["metadata"]["title"].lower()]
        return jsonify({"podcast": [{"libraryItem": m} for m in matches]})
    else:
        matches = [item for item in LIBRARY_ITEMS if q in item["media"]["metadata"]["title"].lower() or q in item["media"]["metadata"]["authorName"].lower()]
        return jsonify({"book": [{"libraryItem": m} for m in matches]})


# ─── Item Endpoints ───────────────────────────────────────────────────────────


@app.route("/api/items/<item_id>", methods=["GET"])
def get_item(item_id):
    for item in LIBRARY_ITEMS + PODCAST_ITEMS:
        if item["id"] == item_id:
            result = dict(item)
            p = make_progress_dto(item_id)
            if p:
                result["userMediaProgress"] = p
            return jsonify(result)
    return jsonify({"error": "Not found"}), 404


@app.route("/api/items/<item_id>/cover", methods=["GET"])
def get_cover(item_id):
    book_covers = {
        "b1": "the-midnight-garden.jpg",
        "b2": "echoes-of-tomorrow.jpg",
        "b3": "silent-meridian.jpg",
        "b4": "beneath-the-copper-sky.jpg",
        "b5": "the-iron-horizon.jpg",
        "b6": "rust-and-reckoning.jpg",
        "b7": "a-thousand-paper-cranes.jpg",
        "b8": "the-vermillion-map.jpg",
        "b9": "longitude-of-shadows.jpg",
        "b10": "the-clockwork-atlas.jpg",
        "b11": "gears-of-rebellion.jpg",
        "b12": "the-brass-meridian.jpg",
        "b13": "ashes-of-the-old-road.jpg",
        "b14": "the-shattered-compass.jpg",
        "b15": "where-all-roads-end.jpg",
    }
    podcast_covers = {
        "p1": "the-archivists-diary.jpg",
        "p2": "cosmic-cafe.jpg",
        "p3": "sustainable-minds.jpg",
        "p4": "untold-stories-of-mars.jpg",
        "p5": "technology-today.jpg",
        "p6": "state-of-affairs.jpg",
        "p7": "global-vibes.jpg",
        "p8": "marvels-of-the-deep.jpg",
        "p9": "beyond-the-verge.jpg",
        "p10": "capital-creators.jpg",
        "p11": "media-trends-watch.jpg",
        "p12": "climate-report.jpg",
    }

    if item_id in book_covers:
        filename = book_covers[item_id]
        mimetype = "image/jpeg" if filename.endswith(".jpg") else "image/png"
        return send_from_directory(BOOK_COVERS_DIR, filename, mimetype=mimetype)
    if item_id in podcast_covers:
        filename = podcast_covers[item_id]
        mimetype = "image/jpeg" if filename.endswith(".jpg") else "image/png"
        return send_from_directory(PODCAST_COVERS_DIR, filename, mimetype=mimetype)

    # Fallback
    return send_from_directory(BOOK_COVERS_DIR, "the-midnight-garden.jpg", mimetype="image/png")


@app.route("/api/series/<series_id>", methods=["GET"])
def get_series(series_id):
    for s in SERIES_DATA:
        if s["id"] == series_id:
            return jsonify(make_series_entry(s))
    return jsonify({"error": "Not found"}), 404


# ─── Playback Endpoints ──────────────────────────────────────────────────────


@app.route("/api/items/<item_id>/play", methods=["POST"])
@app.route("/api/items/<item_id>/play/<episode_id>", methods=["POST"])
def start_playback(item_id, episode_id=None):
    item = None
    for i in LIBRARY_ITEMS + PODCAST_ITEMS:
        if i["id"] == item_id:
            item = i
            break

    if not item:
        return jsonify({"error": "Not found"}), 404

    media = item["media"]
    metadata = media["metadata"]
    duration = media.get("duration", 3600.0)
    p = PROGRESS.get(item_id, {"currentTime": 0.0})

    display_title = metadata.get("title", "Unknown")
    display_author = metadata.get("authorName") or metadata.get("author") or "Unknown"

    if episode_id and "episodes" in media:
        for ep in media["episodes"]:
            if ep["id"] == episode_id:
                display_title = ep["title"]
                duration = ep["duration"]
                break

    chapters = media.get("chapters", [{"id": 0, "start": 0, "end": duration, "title": "Full"}])

    return jsonify({
        "id": f"session-{item_id}-{int(time.time())}",
        "libraryItemId": item_id,
        "episodeId": episode_id,
        "mediaType": item.get("mediaType", "book"),
        "mediaMetadata": metadata,
        "chapters": chapters,
        "displayTitle": display_title,
        "displayAuthor": display_author,
        "duration": duration,
        "playMethod": 0,
        "currentTime": p.get("currentTime", 0.0),
        "audioTracks": [{
            "index": 0,
            "startOffset": 0.0,
            "duration": duration,
            "title": display_title,
            "contentUrl": f"/api/items/{item_id}/file/0",
            "mimeType": "audio/mp4",
        }],
    })


@app.route("/api/session/<session_id>/sync", methods=["POST"])
def sync_session(session_id):
    return jsonify({"success": True})


@app.route("/api/session/<session_id>/close", methods=["POST"])
def close_session(session_id):
    return jsonify({"success": True})


@app.route("/api/session/local", methods=["POST"])
def sync_local_session():
    return jsonify({"success": True})


# ─── Progress Endpoints ───────────────────────────────────────────────────────


@app.route("/api/me/progress/<item_id>", methods=["GET"])
@app.route("/api/me/progress/<item_id>/<episode_id>", methods=["GET"])
def get_progress(item_id, episode_id=None):
    p = make_progress_dto(item_id)
    if p:
        return jsonify(p)
    return jsonify({
        "id": f"prog-{item_id}",
        "libraryItemId": item_id,
        "episodeId": episode_id,
        "duration": 3600.0,
        "progress": 0.0,
        "currentTime": 0.0,
        "isFinished": False,
        "hideFromContinueListening": False,
        "lastUpdate": int(time.time() * 1000),
        "startedAt": None,
        "finishedAt": None,
    })


@app.route("/api/me/progress/<item_id>", methods=["PATCH"])
@app.route("/api/me/progress/<item_id>/<episode_id>", methods=["PATCH"])
def update_progress(item_id, episode_id=None):
    return jsonify({"success": True})


# ─── Items In Progress ────────────────────────────────────────────────────────


@app.route("/api/me/items-in-progress", methods=["GET"])
def items_in_progress():
    in_progress = [item for item in LIBRARY_ITEMS if item["id"] in PROGRESS]
    return jsonify({"libraryItems": in_progress})


# ─── Catch-all for unimplemented endpoints ────────────────────────────────────


@app.route("/api/<path:path>", methods=["GET", "POST", "PATCH", "DELETE", "PUT"])
def catch_all(path):
    return jsonify({"success": True})


# ─── Main ─────────────────────────────────────────────────────────────────────

if __name__ == "__main__":
    print("Mock Audiobookshelf server starting on http://0.0.0.0:3000")
    print("Connect from emulator: http://10.0.2.2:3000")
    print("Connect from device on same network: http://<your-ip>:3000")
    app.run(host="0.0.0.0", port=3000, debug=True)
