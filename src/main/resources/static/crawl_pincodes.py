#!/usr/bin/env python3
"""
Web crawler for https://www.mapsofindia.com/pincode/
Crawls all Indian states not yet in MongoDB, builds cluster records grouped by
3-digit pincode prefix + district (zone), and writes:
  - clusters_crawled.jsonl  — ready for mongoimport
  - clusters_crawled.csv    — for inspection / backup

Usage:
  python3 crawl_pincodes.py

Output files are written to the same directory as this script.
"""

import json
import csv
import time
import os
import sys
from urllib.request import urlopen, Request
from urllib.error import URLError, HTTPError
from html.parser import HTMLParser
from collections import defaultdict

# ── Config ─────────────────────────────────────────────────────────────────────

BASE_URL = "https://www.mapsofindia.com/pincode/"
DELAY    = 0.6          # seconds between requests (be polite)
START_CLUSTER_ID = 446  # max clusterId in MongoDB is 445

# States already loaded into MongoDB — skip these
ALREADY_LOADED = {
    "karnataka", "gujarat", "andhra pradesh", "tamil nadu",
    "maharashtra", "kerala", "uttar pradesh"
}

OUT_DIR  = os.path.dirname(os.path.abspath(__file__))
JSONL_OUT = os.path.join(OUT_DIR, "clusters_crawled.jsonl")
CSV_OUT   = os.path.join(OUT_DIR, "clusters_crawled.csv")

# ── HTML Parsers ────────────────────────────────────────────────────────────────

class LinkParser(HTMLParser):
    """Extracts <a href=...> links matching a path prefix."""
    def __init__(self, path_filter):
        super().__init__()
        self.path_filter = path_filter
        self.links = []          # list of (href, link_text)
        self._current_href = None
        self._capture = False

    def handle_starttag(self, tag, attrs):
        if tag == "a":
            attrs_d = dict(attrs)
            href = attrs_d.get("href", "")
            if self.path_filter in href and href.rstrip("/") != self.path_filter.rstrip("/"):
                self._current_href = href
                self._capture = True

    def handle_endtag(self, tag):
        if tag == "a":
            self._capture = False
            self._current_href = None

    def handle_data(self, data):
        if self._capture and self._current_href and data.strip():
            self.links.append((self._current_href, data.strip()))
            self._capture = False   # take only first text node per link
            self._current_href = None


class TableParser(HTMLParser):
    """Extracts rows from the first <table> on the page."""
    def __init__(self):
        super().__init__()
        self.rows = []
        self._in_table = False
        self._in_td = False
        self._cur_row = []
        self._cur_cell = []

    def handle_starttag(self, tag, attrs):
        if tag == "table":
            self._in_table = True
        elif tag == "tr" and self._in_table:
            self._cur_row = []
        elif tag in ("td", "th") and self._in_table:
            self._in_td = True
            self._cur_cell = []

    def handle_endtag(self, tag):
        if tag in ("td", "th") and self._in_table:
            self._in_td = False
            self._cur_row.append(" ".join(self._cur_cell).strip())
        elif tag == "tr" and self._in_table and self._cur_row:
            self.rows.append(self._cur_row[:])
            self._cur_row = []
        elif tag == "table":
            self._in_table = False

    def handle_data(self, data):
        if self._in_td and data.strip():
            self._cur_cell.append(data.strip())


# ── HTTP helper ─────────────────────────────────────────────────────────────────

def fetch(url, retries=3):
    headers = {"User-Agent": "Mozilla/5.0 (compatible; PincodeBot/1.0)"}
    for attempt in range(retries):
        try:
            req = Request(url, headers=headers)
            with urlopen(req, timeout=15) as resp:
                return resp.read().decode("utf-8", errors="replace")
        except (HTTPError, URLError) as e:
            print(f"    ⚠ fetch error ({e}) — attempt {attempt+1}/{retries}", file=sys.stderr)
            time.sleep(2 ** attempt)
    return ""


# ── Crawl logic ─────────────────────────────────────────────────────────────────

def get_state_links():
    html = fetch(BASE_URL)
    p = LinkParser("/pincode/india/")
    p.feed(html)
    # Deduplicate by href
    seen = set()
    result = []
    for href, text in p.links:
        if href not in seen and href.count("/") == 6:   # https://domain/pincode/india/<state>/
            seen.add(href)
            result.append((href, text))
    return result


def get_district_links(state_url, state_slug):
    html = fetch(state_url)
    p = LinkParser(f"/pincode/india/{state_slug}/")
    p.feed(html)
    seen = set()
    result = []
    for href, text in p.links:
        if href not in seen and href.count("/") == 7:   # https://domain/pincode/india/<state>/<district>/
            seen.add(href)
            result.append((href, text))
    return result


def get_pincodes_for_district(district_url):
    """Returns list of (place, pincode, state, district) tuples."""
    html = fetch(district_url)
    p = TableParser()
    p.feed(html)
    rows = []
    for row in p.rows:
        if len(row) < 4:
            continue
        place, pincode, state, district = row[0], row[1], row[2], row[3]
        pincode = pincode.strip()
        if not pincode.isdigit() or len(pincode) != 6:
            continue
        rows.append((place.strip(), pincode, state.strip(), district.strip()))
    return rows


# ── Main ────────────────────────────────────────────────────────────────────────

def main():
    print("🔍 Fetching state list...")
    state_links = get_state_links()
    print(f"   Found {len(state_links)} states on the site")

    # Filter out already-loaded states
    to_crawl = [
        (href, name) for href, name in state_links
        if name.lower() not in ALREADY_LOADED
    ]
    print(f"   Skipping {len(state_links) - len(to_crawl)} already-loaded states")
    print(f"   Will crawl {len(to_crawl)} states: {[n for _, n in to_crawl]}\n")

    # clusters[state][clusterName][zone] = {"pincodes": {pincode: place}, "zone": zone, "state": state}
    # We key by (state, clusterName, zone) — same as the existing data
    all_clusters = {}   # key=(state, clusterName, zone) -> {pincodes, state, clusterName, zone}

    for state_url, state_name in to_crawl:
        state_slug = state_url.rstrip("/").split("/")[-1]
        print(f"📍 {state_name} ({state_slug})")

        time.sleep(DELAY)
        district_links = get_district_links(state_url, state_slug)
        print(f"   {len(district_links)} districts")

        for dist_url, dist_name in district_links:
            print(f"   ↳ {dist_name}", end=" ", flush=True)
            time.sleep(DELAY)

            rows = get_pincodes_for_district(dist_url)
            print(f"({len(rows)} pincodes)")

            for place, pincode, state, district in rows:
                cluster_prefix = pincode[:3]
                key = (state, cluster_prefix, district)

                if key not in all_clusters:
                    all_clusters[key] = {
                        "state": state,
                        "clusterName": cluster_prefix,
                        "zone": district,
                        "pincodes": {}
                    }
                all_clusters[key]["pincodes"][pincode] = place

        print(f"   ✅ {state_name} done — clusters so far: {len(all_clusters)}\n")

    # ── Write JSONL ──────────────────────────────────────────────────────────────
    print(f"\n💾 Writing {len(all_clusters)} clusters to {JSONL_OUT}")
    cluster_id = START_CLUSTER_ID
    with open(JSONL_OUT, "w", encoding="utf-8") as jf:
        for key, cluster in sorted(all_clusters.items()):
            doc = {
                "clusterId": cluster_id,
                "state":       cluster["state"],
                "clusterName": cluster["clusterName"],
                "pincodes":    cluster["pincodes"],
                "zone":        cluster["zone"]
            }
            jf.write(json.dumps(doc, ensure_ascii=False) + "\n")
            cluster_id += 1

    # ── Write CSV ────────────────────────────────────────────────────────────────
    print(f"💾 Writing CSV to {CSV_OUT}")
    with open(CSV_OUT, "w", newline="", encoding="utf-8") as cf:
        writer = csv.writer(cf)
        writer.writerow(["clusterId", "state", "clusterName", "zone", "pincode", "placeName"])
        cid = START_CLUSTER_ID
        for key, cluster in sorted(all_clusters.items()):
            for pincode, place in cluster["pincodes"].items():
                writer.writerow([cid, cluster["state"], cluster["clusterName"], cluster["zone"], pincode, place])
            cid += 1

    total_clusters = cluster_id - START_CLUSTER_ID
    total_pincodes = sum(len(c["pincodes"]) for c in all_clusters.values())
    print(f"\n✅ Done: {total_clusters} clusters, {total_pincodes} pincodes")
    print(f"\nTo load into MongoDB:")
    print(f"  mongoimport --db pincodeClusters --collection clusters --file {JSONL_OUT}")


if __name__ == "__main__":
    main()
