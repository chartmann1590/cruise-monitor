#!/usr/bin/env python3
"""
Sync Wear OS screenshots (and other listing assets) to Google Play via the Android Publisher API v3.
"""

import json
import os
import sys
from pathlib import Path
from google.oauth2 import service_account
from googleapiclient.discovery import build
from googleapiclient.http import MediaFileUpload

PACKAGE_NAME = "com.cruisewatch.app"
REPO_ROOT = Path(__file__).resolve().parent.parent
WEAR_SCREENSHOTS_DIR = REPO_ROOT / "fastlane" / "metadata" / "android" / "en-US" / "images" / "wearScreenshots"


def get_credentials():
    service_account_json = os.environ.get("PLAY_SERVICE_ACCOUNT_JSON")
    if not service_account_json and len(sys.argv) > 1:
        key_file = Path(sys.argv[1])
        if key_file.exists():
            service_account_json = key_file.read_text(encoding="utf-8")

    if not service_account_json:
        raise ValueError(
            "Service account JSON not provided. Set PLAY_SERVICE_ACCOUNT_JSON env var or pass key file path as argv[1]."
        )

    # Could be JSON string or filepath in env var
    if service_account_json.strip().startswith("{"):
        info = json.loads(service_account_json)
        return service_account.Credentials.from_service_account_info(
            info,
            scopes=["https://www.googleapis.com/auth/androidpublisher"],
        )
    else:
        return service_account.Credentials.from_service_account_file(
            service_account_json.strip(),
            scopes=["https://www.googleapis.com/auth/androidpublisher"],
        )


def sync_wear_screenshots(dry_run: bool = False):
    screenshots = sorted(WEAR_SCREENSHOTS_DIR.glob("*.png"))
    if not screenshots:
        print(f"No screenshots found in {WEAR_SCREENSHOTS_DIR}")
        return

    print(f"Found {len(screenshots)} Wear OS screenshots to upload:")
    for s in screenshots:
        print(f"  - {s.name} ({s.stat().st_size} bytes)")

    if dry_run:
        print("Dry run requested; skipping API calls.")
        return

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
            changesNotSentForReview=False,
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
