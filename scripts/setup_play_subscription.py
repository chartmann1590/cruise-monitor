#!/usr/bin/env python3
"""
Create and activate the monthly ad-free subscription on Google Play
using the Android Publisher API v3 and Google Play Developer Service Account.
"""

import json
import os
import sys
from pathlib import Path
from google.oauth2 import service_account
from googleapiclient.discovery import build

PACKAGE_NAME = "com.cruisewatch.app"
PRODUCT_ID = "ad_free_monthly"
BASE_PLAN_ID = "monthly"
PRICE_USD_UNITS = "1"
PRICE_USD_NANOS = 990000000  # $1.99/mo

REPO_ROOT = Path(__file__).resolve().parent.parent
LOCAL_KEY_PATH = REPO_ROOT / "fastlane" / "play-service-account.json"


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


def setup_subscription():
    credentials = get_credentials()
    service = build("androidpublisher", "v3", credentials=credentials)

    print(f"Checking existing subscription '{PRODUCT_ID}' for {PACKAGE_NAME}...")
    existing = None
    try:
        existing = (
            service.monetization()
            .subscriptions()
            .get(packageName=PACKAGE_NAME, productId=PRODUCT_ID)
            .execute()
        )
        print(f"Subscription '{PRODUCT_ID}' already exists!")
    except Exception as e:
        print(f"Subscription '{PRODUCT_ID}' does not exist yet (or get failed: {e}). Will create.")

    if not existing:
        print(f"Converting regional prices for $1.99 USD...")
        price_resp = (
            service.monetization()
            .convertRegionPrices(
                packageName=PACKAGE_NAME,
                body={
                    "price": {
                        "currencyCode": "USD",
                        "units": PRICE_USD_UNITS,
                        "nanos": PRICE_USD_NANOS,
                    }
                },
            )
            .execute()
        )

        region_version = price_resp["regionVersion"]["version"]
        converted_prices = price_resp.get("convertedRegionPrices", {})
        converted_other = price_resp.get("convertedOtherRegionsPrice", {})

        regional_configs = []
        for region_code, info in converted_prices.items():
            regional_configs.append(
                {
                    "regionCode": region_code,
                    "newSubscriberAvailability": True,
                    "price": info["price"],
                }
            )

        other_regions_config = {
            "newSubscriberAvailability": True,
            "usdPrice": converted_other.get(
                "usdPrice",
                {
                    "currencyCode": "USD",
                    "units": PRICE_USD_UNITS,
                    "nanos": PRICE_USD_NANOS,
                },
            ),
            "eurPrice": converted_other.get(
                "eurPrice",
                {"currencyCode": "EUR", "units": "1", "nanos": 750000000},
            ),
        }

        body = {
            "packageName": PACKAGE_NAME,
            "productId": PRODUCT_ID,
            "basePlans": [
                {
                    "basePlanId": BASE_PLAN_ID,
                    "autoRenewingBasePlanType": {
                        "billingPeriodDuration": "P1M",
                        "legacyCompatible": True,
                    },
                    "regionalConfigs": regional_configs,
                    "otherRegionsConfig": other_regions_config,
                }
            ],
            "listings": [
                {
                    "languageCode": "en-US",
                    "title": "CruiseWatch Ad-Free Monthly",
                    "description": "Browse cruises and track price drops completely ad-free.",
                    "benefits": [
                        "Remove all banner ads",
                        "Remove interstitial ads",
                        "Support ongoing app development",
                    ],
                }
            ],
            "taxAndComplianceSettings": {
                "eeaWithdrawalRightType": "WITHDRAWAL_RIGHT_DIGITAL_CONTENT"
            },
        }

        print(f"Creating subscription '{PRODUCT_ID}' with region version {region_version}...")
        created = (
            service.monetization()
            .subscriptions()
            .create(
                packageName=PACKAGE_NAME,
                productId=PRODUCT_ID,
                regionsVersion_version=region_version,
                body=body,
            )
            .execute()
        )
        print("Created subscription successfully!")
        existing = created

    # Check base plan state and activate if draft
    base_plans = existing.get("basePlans", [])
    for bp in base_plans:
        bp_id = bp.get("basePlanId")
        state = bp.get("state")
        print(f"Base plan '{bp_id}' current state: {state}")
        if bp_id == BASE_PLAN_ID and state != "ACTIVE":
            print(f"Activating base plan '{BASE_PLAN_ID}'...")
            try:
                activated = (
                    service.monetization()
                    .subscriptions()
                    .basePlans()
                    .activate(
                        packageName=PACKAGE_NAME,
                        productId=PRODUCT_ID,
                        basePlanId=BASE_PLAN_ID,
                        body={},
                    )
                    .execute()
                )
                print(f"Base plan activated: {activated.get('state')}")
            except Exception as e:
                print(f"Failed to activate base plan: {e}")

    # Fetch and display final subscription status
    final_sub = (
        service.monetization()
        .subscriptions()
        .get(packageName=PACKAGE_NAME, productId=PRODUCT_ID)
        .execute()
    )
    print("\n--- Final Subscription Details ---")
    print(f"Product ID: {final_sub.get('productId')}")
    for listing in final_sub.get("listings", []):
        print(f"Listing ({listing.get('languageCode')}): {listing.get('title')} - {listing.get('description')}")
    for bp in final_sub.get("basePlans", []):
        print(f"Base plan: {bp.get('basePlanId')} (State: {bp.get('state')})")
    print("----------------------------------\n")


if __name__ == "__main__":
    setup_subscription()
