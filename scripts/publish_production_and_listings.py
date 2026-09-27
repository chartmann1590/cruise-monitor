#!/usr/bin/env python3
"""
Update Google Play Store listings in all supported languages and publish
version 0.5.0 (versionCode 15 for phone, 1015 for Wear OS) to Production.
Also updates subscription product listings in all supported languages.
"""

import argparse
import json
import os
import sys
from pathlib import Path
from googleapiclient.discovery import build

REPO_ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(REPO_ROOT))
from scripts.upload_to_play_track import get_credentials, PACKAGE_NAME

LANGUAGES = ["en-US", "es-ES", "de-DE", "fr-FR", "it-IT", "pt-BR"]
METADATA_DIR = REPO_ROOT / "fastlane" / "metadata" / "android"

SUBSCRIPTION_LISTINGS = [
    {
        "languageCode": "en-US",
        "title": "CruiseWatch Ad-Free Monthly",
        "description": "Browse cruises and track price drops completely ad-free.",
        "benefits": [
            "Remove all banner ads",
            "Remove interstitial ads",
            "Support ongoing app development",
        ],
    },
    {
        "languageCode": "es-ES",
        "title": "CruiseWatch Sin Anuncios Mensual",
        "description": "Explora cruceros y sigue bajadas de precio sin ningún anuncio.",
        "benefits": [
            "Elimina todos los banners",
            "Elimina los anuncios intersticiales",
            "Apoya el desarrollo continuo",
        ],
    },
    {
        "languageCode": "de-DE",
        "title": "CruiseWatch Werbefrei Monatlich",
        "description": "Kreuzfahrten durchsuchen und Preisalarme komplett werbefrei verfolgen.",
        "benefits": [
            "Alle Werbebanner entfernen",
            "Interstitial-Anzeigen entfernen",
            "Laufende App-Entwicklung unterstützen",
        ],
    },
    {
        "languageCode": "fr-FR",
        "title": "CruiseWatch Sans Pub Mensuel",
        "description": "Parcourez les croisières et suivez les baisses de prix sans aucune publicité.",
        "benefits": [
            "Supprimer toutes les bannières",
            "Supprimer les annonces interstitielles",
            "Soutenir le développement continu",
        ],
    },
    {
        "languageCode": "it-IT",
        "title": "CruiseWatch Senza Annunci Mensile",
        "description": "Sfoglia le crociere e monitora i cali di prezzo senza alcuna pubblicità.",
        "benefits": [
            "Rimuovi tutti i banner pubblicitari",
            "Rimuovi gli annunci interstitial",
            "Supporta lo sviluppo continuo",
        ],
    },
    {
        "languageCode": "pt-BR",
        "title": "CruiseWatch Sem Anúncios Mensal",
        "description": "Navegue por cruzeiros e acompanhe quedas de preços totalmente sem anúncios.",
        "benefits": [
            "Remover todos os banners",
            "Remover anúncios intersticiais",
            "Apoiar o desenvolvimento contínuo",
        ],
    },
]


def update_subscription_listings(service):
    print("Fetching current regions version for monetization...")
    price_resp = service.monetization().convertRegionPrices(
        packageName=PACKAGE_NAME,
        body={"price": {"currencyCode": "USD", "units": "1", "nanos": 990000000}},
    ).execute()
    region_version = price_resp["regionVersion"]["version"]
    print(f"Regions version: {region_version}")

    print("Updating subscription 'ad_free_monthly' listings for all languages...")
    sub = (
        service.monetization()
        .subscriptions()
        .patch(
            packageName=PACKAGE_NAME,
            productId="ad_free_monthly",
            updateMask="listings",
            regionsVersion_version=region_version,
            body={"listings": SUBSCRIPTION_LISTINGS},
        )
        .execute()
    )
    print("Subscription listings updated successfully:")
    for listing in sub.get("listings", []):
        print(f"  [{listing.get('languageCode')}] {listing.get('title')}: {listing.get('description')}")


def publish_to_production(dry_run: bool = False):
    credentials = get_credentials()
    service = build("androidpublisher", "v3", credentials=credentials)

    # 1. Update subscription localized listings
    if not dry_run:
        update_subscription_listings(service)

    # 2. Create app edit
    print(f"\nCreating Play Store edit for {PACKAGE_NAME}...")
    edit_req = service.edits().insert(packageName=PACKAGE_NAME, body={})
    edit = edit_req.execute()
    edit_id = edit["id"]
    print(f"Created edit ID: {edit_id}")

    try:
        # 3. Update store listings for all languages
        print("\nUpdating App Store listings for all languages...")
        for lang in LANGUAGES:
            lang_dir = METADATA_DIR / lang
            title_file = lang_dir / "title.txt"
            short_desc_file = lang_dir / "short_description.txt"
            full_desc_file = lang_dir / "full_description.txt"

            title = title_file.read_text(encoding="utf-8").strip()
            short_desc = short_desc_file.read_text(encoding="utf-8").strip()
            full_desc = full_desc_file.read_text(encoding="utf-8").strip()

            print(f"  [{lang}] Title: '{title}' ({len(title)} chars), Short desc: {len(short_desc)} chars, Full desc: {len(full_desc)} chars")
            service.edits().listings().update(
                packageName=PACKAGE_NAME,
                editId=edit_id,
                language=lang,
                body={
                    "language": lang,
                    "title": title,
                    "shortDescription": short_desc,
                    "fullDescription": full_desc,
                },
            ).execute()
        print("All store listings updated successfully.")

        # 4. Prepare localized release notes
        phone_release_notes = []
        wear_release_notes = []
        for lang in LANGUAGES:
            lang_dir = METADATA_DIR / lang / "changelogs"
            phone_changelog = lang_dir / "15.txt"
            wear_changelog = lang_dir / "1015.txt"

            if phone_changelog.exists():
                phone_release_notes.append({
                    "language": lang,
                    "text": phone_changelog.read_text(encoding="utf-8").strip()
                })
            if wear_changelog.exists():
                wear_release_notes.append({
                    "language": lang,
                    "text": wear_changelog.read_text(encoding="utf-8").strip()
                })

        # 5. Update production track for phone
        print("\nUpdating phone 'production' track with versionCode 15...")
        service.edits().tracks().update(
            packageName=PACKAGE_NAME,
            editId=edit_id,
            track="production",
            body={
                "track": "production",
                "releases": [
                    {
                        "name": "0.5.0 (15)",
                        "versionCodes": ["15"],
                        "status": "completed",
                        "releaseNotes": phone_release_notes,
                    }
                ],
            },
        ).execute()
        print("Phone 'production' track updated.")

        # 6. Update wear:production track for Wear OS
        print("\nUpdating Wear OS 'wear:production' track with versionCode 1015...")
        service.edits().tracks().update(
            packageName=PACKAGE_NAME,
            editId=edit_id,
            track="wear:production",
            body={
                "track": "wear:production",
                "releases": [
                    {
                        "name": "0.5.0 (1015)",
                        "versionCodes": ["1015"],
                        "status": "completed",
                        "releaseNotes": wear_release_notes,
                    }
                ],
            },
        ).execute()
        print("Wear OS 'wear:production' track updated.")

        # 7. Commit edit (or delete if dry run)
        if dry_run:
            print("\n[DRY RUN] Validated all edits successfully. Deleting edit without committing...")
            service.edits().delete(packageName=PACKAGE_NAME, editId=edit_id).execute()
            print("[DRY RUN] Edit deleted cleanly.")
        else:
            print("\nCommitting edit to Google Play Console...")
            commit_res = service.edits().commit(
                packageName=PACKAGE_NAME,
                editId=edit_id,
            ).execute()
            print(f"\n[SUCCESS] Successfully published 0.5.0 (15 / 1015) to PRODUCTION! Commit result: {commit_res}")

    except Exception as e:
        print(f"\nError during production release: {e}", file=sys.stderr)
        try:
            print("Cleaning up edit...")
            service.edits().delete(packageName=PACKAGE_NAME, editId=edit_id).execute()
        except Exception:
            pass
        raise


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Publish store listings and release to Google Play Production")
    parser.add_argument("--dry-run", action="store_true", help="Validate changes without committing")
    args = parser.parse_args()

    publish_to_production(dry_run=args.dry_run)
