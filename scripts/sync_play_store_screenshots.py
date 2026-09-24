#!/usr/bin/env python3
"""
Sync Wear OS screenshots (and other listing assets) to Google Play via the Android Publisher API v3.
"""

import json
import os
import re
import struct
import sys
from pathlib import Path

PACKAGE_NAME = "com.cruisewatch.app"
REPO_ROOT = Path(__file__).resolve().parent.parent
WEAR_SCREENSHOTS_DIR = REPO_ROOT / "fastlane" / "metadata" / "android" / "en-US" / "images" / "wearScreenshots"

# Google Play Wear OS preview asset constraints
MIN_DIMENSION = 384
MAX_DIMENSION = 3840


def natural_sort_key(path: Path):
    """Sort filenames with natural numeric ordering (e.g. 1, 2, ... 10)."""
    return [int(text) if text.isdigit() else text.lower() for text in re.split(r"(\d+)", path.name)]


def validate_screenshot(path: Path) -> tuple[int, int]:
    """Validate screenshot meets Play Store Wear OS requirements: valid PNG, 1:1, 384-3840px."""
    if not path.is_file() or path.stat().st_size == 0:
        raise ValueError(f"Screenshot file {path.name} is missing or empty")

    with path.open("rb") as f:
        header = f.read(24)
        if len(header) < 24 or header[:8] != b"\x89PNG\r\n\x1a\n":
            raise ValueError(f"{path.name} is not a valid PNG image")
        width, height = struct.unpack(">II", header[16:24])

    if width != height:
        raise ValueError(f"{path.name} violates 1:1 aspect ratio constraint: got {width}x{height}")
    if width < MIN_DIMENSION or height < MIN_DIMENSION:
        raise ValueError(
            f"{path.name} dimensions ({width}x{height}) below minimum required {MIN_DIMENSION}x{MIN_DIMENSION}"
        )
    if width > MAX_DIMENSION or height > MAX_DIMENSION:
        raise ValueError(
            f"{path.name} dimensions ({width}x{height}) exceed maximum allowed {MAX_DIMENSION}x{MAX_DIMENSION}"
        )

    return width, height


def get_credentials():
    from google.oauth2 import service_account

    service_account_json = os.environ.get("PLAY_SERVICE_ACCOUNT_JSON")
    if not service_account_json and len(sys.argv) > 1:
        key_file = Path(sys.argv[1])
        if key_file.exists():
            service_account_json = key_file.read_text(encoding="utf-8")

    if not service_account_json:
        raise ValueError(
            "Service account JSON not provided. Set PLAY_SERVICE_ACCOUNT_JSON env var or pass key file path as argv[1]."
        )

    raw_value = service_account_json.strip()
    if os.path.isfile(raw_value):
        return service_account.Credentials.from_service_account_file(
            raw_value,
            scopes=["https://www.googleapis.com/auth/androidpublisher"],
        )

    try:
        info = json.loads(raw_value)
        return service_account.Credentials.from_service_account_info(
            info,
            scopes=["https://www.googleapis.com/auth/androidpublisher"],
        )
    except json.JSONDecodeError as err:
        raise ValueError(
            f"Provided service account value is neither an existing file path nor valid JSON: {err}"
        ) from err


def sync_wear_screenshots(dry_run: bool = False):
    screenshots = sorted(WEAR_SCREENSHOTS_DIR.glob("*.png"), key=natural_sort_key)
    if not screenshots:
        print(f"No screenshots found in {WEAR_SCREENSHOTS_DIR}")
        return

    print(f"Found {len(screenshots)} Wear OS screenshots to upload:")
    for s in screenshots:
        w, h = validate_screenshot(s)
        print(f"  - {s.name} ({w}x{h} px, {s.stat().st_size} bytes) [VALID]")

    if dry_run:
        print("Dry run requested; validation passed, skipping API calls.")
        return

    from googleapiclient.discovery import build
    from googleapiclient.http import MediaFileUpload

    credentials = get_credentials()
    service = build("androidpublisher", "v3", credentials=credentials)

    print(f"Creating edit for package {PACKAGE_NAME}...")
    edit_req = service.edits().insert(packageName=PACKAGE_NAME, body={})
    edit = edit_req.execute()
    edit_id = edit["id"]
    print(f"Created edit {edit_id}")

    try:
        # Delete existing wearScreenshots
        print("Deleting existing wearScreenshots for en-US...")
        service.edits().images().deleteall(
            packageName=PACKAGE_NAME,
            editId=edit_id,
            language="en-US",
            imageType="wearScreenshots",
        ).execute()

        # Upload new wearScreenshots
        for s in screenshots:
            print(f"Uploading {s.name}...")
            media = MediaFileUpload(str(s), mimetype="image/png")
            service.edits().images().upload(
                packageName=PACKAGE_NAME,
                editId=edit_id,
                language="en-US",
                imageType="wearScreenshots",
                media_body=media,
            ).execute()

        print("Committing edit...")
        service.edits().commit(
            packageName=PACKAGE_NAME,
            editId=edit_id,
            changesNotSentForReview=True,
        ).execute()
        print("Successfully updated Wear OS screenshots on Google Play!")
    except Exception as e:
        print(f"Error during image update: {e}", file=sys.stderr)
        try:
            print("Cleaning up edit...")
            service.edits().delete(packageName=PACKAGE_NAME, editId=edit_id).execute()
        except Exception:
            pass
        raise


if __name__ == "__main__":
    dry_run = "--dry-run" in sys.argv
    sync_wear_screenshots(dry_run=dry_run)
