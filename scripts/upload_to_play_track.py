#!/usr/bin/env python3
"""
Upload signed App Bundles to Google Play Console tracks (e.g. internal track)
using the Android Publisher API v3 and Google Play Developer Service Account.
"""

import argparse
import json
import os
import sys
from pathlib import Path
from google.oauth2 import service_account
from googleapiclient.discovery import build
from googleapiclient.http import MediaFileUpload

import socket
socket.setdefaulttimeout(600)

PACKAGE_NAME = "com.cruisewatch.app"
REPO_ROOT = Path(__file__).resolve().parent.parent
LOCAL_KEY_PATH = REPO_ROOT / "fastlane" / "play-service-account.json"


def upload_bundle_chunked(service, edit_id: str, bundle_path: Path):
    print(f"Uploading {bundle_path.name} ({bundle_path.stat().st_size} bytes) in 5MB chunks...")
    media = MediaFileUpload(
        str(bundle_path),
        mimetype="application/octet-stream",
        chunksize=5 * 1024 * 1024,
        resumable=True,
    )
    request = service.edits().bundles().upload(
        packageName=PACKAGE_NAME,
        editId=edit_id,
        media_body=media,
    )
    response = None
    while response is None:
        status, response = request.next_chunk()
        if status:
            print(f"  Progress: {int(status.progress() * 100)}%")
    return response


def get_credentials():
    service_account_json = os.environ.get("PLAY_SERVICE_ACCOUNT_JSON")
    if not service_account_json and len(sys.argv) > 1:
        key_arg = Path(sys.argv[1])
        if key_arg.exists():
            service_account_json = key_arg.read_text(encoding="utf-8")
    if not service_account_json and LOCAL_KEY_PATH.exists():
        service_account_json = LOCAL_KEY_PATH.read_text(encoding="utf-8")

    if not service_account_json:
        raise ValueError(
            "Service account JSON not provided. Set PLAY_SERVICE_ACCOUNT_JSON env var or place key in fastlane/play-service-account.json"
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


def upload_release(track: str = "internal", upload_wear: bool = True):
    phone_bundle = REPO_ROOT / "app" / "build" / "outputs" / "bundle" / "release" / "app-release.aab"
    wear_bundle = REPO_ROOT / "wear" / "build" / "outputs" / "bundle" / "release" / "wear-release.aab"

    if not phone_bundle.exists():
        raise FileNotFoundError(f"Phone bundle not found at {phone_bundle}")

    credentials = get_credentials()
    service = build("androidpublisher", "v3", credentials=credentials)

    print(f"Creating edit for {PACKAGE_NAME}...")
    edit_req = service.edits().insert(packageName=PACKAGE_NAME, body={})
    edit = edit_req.execute()
    edit_id = edit["id"]
    print(f"Created edit ID: {edit_id}")

    try:
        # 1. Upload phone AAB
        phone_res = upload_bundle_chunked(service, edit_id, phone_bundle)
        phone_vc = phone_res["versionCode"]
        print(f"Uploaded phone bundle with versionCode: {phone_vc}")

        # 2. Update phone track
        print(f"Updating track '{track}' with versionCode {phone_vc}...")
        service.edits().tracks().update(
            packageName=PACKAGE_NAME,
            editId=edit_id,
            track=track,
            body={
                "track": track,
                "releases": [
                    {
                        "name": f"0.5.0 ({phone_vc})",
                        "versionCodes": [str(phone_vc)],
                        "status": "completed",
                        "releaseNotes": [
                            {
                                "language": "en-US",
                                "text": "Added monthly ad-free subscription option and performance improvements.",
                            }
                        ],
                    }
                ],
            },
        ).execute()
        print(f"Track '{track}' updated successfully.")

        # 3. Upload wear AAB if available
        if upload_wear and wear_bundle.exists():
            wear_track = f"wear:{track}" if track in ("internal", "beta", "production") else "wear:internal"
            wear_res = upload_bundle_chunked(service, edit_id, wear_bundle)
            wear_vc = wear_res["versionCode"]
            print(f"Uploaded Wear OS bundle with versionCode: {wear_vc}")

            print(f"Updating track '{wear_track}' with versionCode {wear_vc}...")
            service.edits().tracks().update(
                packageName=PACKAGE_NAME,
                editId=edit_id,
                track=wear_track,
                body={
                    "track": wear_track,
                    "releases": [
                        {
                            "name": f"0.5.0 ({wear_vc})",
                            "versionCodes": [str(wear_vc)],
                            "status": "completed",
                            "releaseNotes": [
                                {
                                    "language": "en-US",
                                    "text": "Companion watch updates.",
                                }
                            ],
                        }
                    ],
                },
            ).execute()
            print(f"Track '{wear_track}' updated successfully.")

        # 4. Commit edit
        print("Committing edit to Google Play Console...")
        commit_res = (
            service.edits()
            .commit(
                packageName=PACKAGE_NAME,
                editId=edit_id,
            )
            .execute()
        )
        print(f"Successfully published release to '{track}' track! Commit result: {commit_res}")

    except Exception as e:
        print(f"Error during release upload: {e}", file=sys.stderr)
        try:
            print("Cleaning up edit...")
            service.edits().delete(packageName=PACKAGE_NAME, editId=edit_id).execute()
        except Exception:
            pass
        raise


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Upload AAB to Google Play track")
    parser.add_argument("--track", default="internal", help="Track name (default: internal)")
    parser.add_argument("--skip-wear", action="store_true", help="Skip Wear OS bundle")
    args = parser.parse_args()

    upload_release(track=args.track, upload_wear=not args.skip_wear)
