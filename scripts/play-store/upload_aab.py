#!/usr/bin/env python3
"""
Upload the signed ShellDroid AAB to a Google Play track.

Run with the system Python that has the Google API libs:
  /usr/bin/python3 scripts/play-store/upload_aab.py --track production --status completed
  /usr/bin/python3 scripts/play-store/upload_aab.py --track production --status inProgress --user-fraction 0.1
  /usr/bin/python3 scripts/play-store/upload_aab.py --track internal --status draft

Tracks:
  internal | alpha (closed) | beta (open) | production

Statuses:
  completed  full rollout
  draft      create release but don't publish (finish in Play Console UI)
  inProgress gradual rollout (requires --user-fraction, e.g. 0.1 = 10%)
  halted     pause a gradual rollout

Safety:
  --dry-run uploads the bundle and prepares the track but DISCARDS the edit
  (nothing is published). Use it to validate versionCode acceptance first.
"""

import argparse
import os
import sys
from pathlib import Path

from google.oauth2 import service_account
from googleapiclient.discovery import build
from googleapiclient.errors import HttpError
from googleapiclient.http import MediaFileUpload

# Reuses the same service account that already has access to this app's console.
DEFAULT_SA_JSON = "/home/errante/homedir-es-18c5091cc31d.json"
DEFAULT_PACKAGE = "com.ebadenes.shelldroid"
DEFAULT_AAB = "app/build/outputs/bundle/release/app-release.aab"
SCOPES = ["https://www.googleapis.com/auth/androidpublisher"]


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--sa-json", default=os.environ.get("GOOGLE_PLAY_SA_JSON", DEFAULT_SA_JSON)
    )
    parser.add_argument(
        "--package", default=os.environ.get("GOOGLE_PLAY_PACKAGE", DEFAULT_PACKAGE)
    )
    parser.add_argument("--aab", default=DEFAULT_AAB)
    parser.add_argument(
        "--track",
        default="production",
        choices=["internal", "alpha", "beta", "production"],
    )
    parser.add_argument(
        "--status",
        default="completed",
        choices=["completed", "draft", "inProgress", "halted"],
    )
    parser.add_argument(
        "--user-fraction",
        type=float,
        help="Required for inProgress (e.g. 0.1 for 10%%)",
    )
    parser.add_argument("--release-name", help="Optional release name override")
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="Upload + prepare but DISCARD the edit (nothing published)",
    )
    args = parser.parse_args()

    aab_path = Path(args.aab)
    if not aab_path.exists():
        print(f"ERROR: AAB not found at {aab_path}", file=sys.stderr)
        return 1
    if not Path(args.sa_json).exists():
        print(f"ERROR: SA JSON not found at {args.sa_json}", file=sys.stderr)
        return 1
    if args.status == "inProgress" and not args.user_fraction:
        print("ERROR: --user-fraction required when status=inProgress", file=sys.stderr)
        return 1

    print(f"Package:  {args.package}")
    print(f"AAB:      {aab_path} ({aab_path.stat().st_size // 1024} KB)")
    print(f"Track:    {args.track}")
    print(f"Status:   {args.status}")
    if args.user_fraction:
        print(f"Rollout:  {args.user_fraction * 100:.0f}%")
    if args.dry_run:
        print("Mode:     DRY RUN (edit will be discarded, nothing published)")

    creds = service_account.Credentials.from_service_account_file(
        args.sa_json, scopes=SCOPES
    )
    service = build("androidpublisher", "v3", credentials=creds, cache_discovery=False)
    edits = service.edits()
    edit_id = None

    try:
        edit = edits.insert(packageName=args.package, body={}).execute()
        edit_id = edit["id"]
        print(f"\nOpened edit {edit_id}")

        media = MediaFileUpload(
            str(aab_path), mimetype="application/octet-stream", resumable=True
        )
        bundle = (
            edits.bundles()
            .upload(packageName=args.package, editId=edit_id, media_body=media)
            .execute()
        )
        version_code = bundle["versionCode"]
        print(f"  ✓ Uploaded bundle, versionCode={version_code}")

        release = {"status": args.status, "versionCodes": [str(version_code)]}
        if args.release_name:
            release["name"] = args.release_name
        if args.status == "inProgress":
            release["userFraction"] = args.user_fraction

        edits.tracks().update(
            packageName=args.package,
            editId=edit_id,
            track=args.track,
            body={"track": args.track, "releases": [release]},
        ).execute()
        print(
            f"  ✓ Assigned versionCode={version_code} to '{args.track}' (status={args.status})"
        )

        if args.dry_run:
            edits.delete(packageName=args.package, editId=edit_id).execute()
            print("\n✓ DRY RUN OK — versionCode accepted. Edit discarded, nothing published.")
            return 0

        result = edits.commit(packageName=args.package, editId=edit_id).execute()
        print(f"\n✓ Committed edit {result.get('id')}")
        print(f"  Check Play Console → {args.track}")
        return 0

    except HttpError as e:
        print(f"\nERROR: {e}", file=sys.stderr)
        if edit_id:
            try:
                edits.delete(packageName=args.package, editId=edit_id).execute()
                print("Edit rolled back — nothing published.", file=sys.stderr)
            except Exception:
                pass
        return 1


if __name__ == "__main__":
    sys.exit(main())
