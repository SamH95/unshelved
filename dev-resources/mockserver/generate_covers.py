"""Generate placeholder cover images for the mock server."""
import struct
import zlib
import os

COLORS = {
    # The Meridian Cycle (cool blue/purple tones)
    "b1": (26, 35, 126),      # Indigo
    "b2": (49, 27, 146),      # Deep purple
    "b3": (74, 20, 140),      # Purple
    # The Copper Age (warm copper/orange tones)
    "b4": (191, 54, 12),      # Deep orange
    "b5": (230, 81, 0),       # Orange
    "b6": (153, 51, 0),       # Burnt copper
    # The Cartographer's Secret (earthy tones)
    "b7": (27, 94, 32),       # Green
    "b8": (46, 125, 50),      # Medium green
    "b9": (51, 105, 30),      # Olive green
    # Clockwork Empire (golden/brass tones)
    "b10": (158, 118, 18),    # Dark gold
    "b11": (175, 143, 13),    # Brass
    "b12": (130, 95, 10),     # Bronze
    # The Wanderer's Path (deep red/maroon tones)
    "b13": (136, 14, 79),     # Deep pink
    "b14": (106, 27, 54),     # Maroon
    "b15": (183, 28, 28),     # Red
    # Podcasts
    "p1": (0, 96, 100),       # Teal
    "p2": (13, 71, 161),      # Blue
}

def create_png(width, height, r, g, b):
    """Create a minimal valid PNG with a solid color."""
    def chunk(chunk_type, data):
        c = chunk_type + data
        return struct.pack(">I", len(data)) + c + struct.pack(">I", zlib.crc32(c) & 0xFFFFFFFF)

    header = b"\x89PNG\r\n\x1a\n"
    ihdr = chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 2, 0, 0, 0))

    raw = b""
    for _ in range(height):
        raw += b"\x00" + bytes([r, g, b]) * width

    idat = chunk(b"IDAT", zlib.compress(raw))
    iend = chunk(b"IEND", b"")

    return header + ihdr + idat + iend


def main():
    out_dir = os.path.join(os.path.dirname(__file__), "covers")
    os.makedirs(out_dir, exist_ok=True)

    for item_id, (r, g, b) in COLORS.items():
        png_data = create_png(400, 400, r, g, b)
        path = os.path.join(out_dir, f"{item_id}.png")
        with open(path, "wb") as f:
            f.write(png_data)
        print(f"Created {path}")


if __name__ == "__main__":
    main()
