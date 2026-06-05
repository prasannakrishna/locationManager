#!/usr/bin/env python3
"""
Seed cluster centroids for ALL 1051 clusters in India.

Strategy:
  1. Read all clusters from MongoDB (pincodeClusters.clusters)
  2. For each cluster, geocode using Nominatim (OpenStreetMap) — FREE, no API key
  3. Save centroids to cluster_centroids collection
  4. Precompute all-pairs distances using Haversine
  5. Save to cluster_distances collection

Usage:
  pip install pymongo requests
  python seed_centroids.py

Rate limiting: Nominatim allows 1 request/second. 1051 clusters ≈ 17 minutes.
To skip geocoding and only precompute distances: python seed_centroids.py --distances-only
"""

import sys
import time
import math
import requests
from pymongo import MongoClient, UpdateOne
from datetime import datetime

# ── Config ──────────────────────────────────────────────────────────────────
MONGO_URI = "mongodb://localhost:27017"
DB_NAME = "pincodeClusters"
NOMINATIM_URL = "https://nominatim.openstreetmap.org/search"
NOMINATIM_HEADERS = {"User-Agent": "SCM-Platform/1.0 (prasannakrishnabhagwat@github)"}
ROAD_FACTOR = 1.3        # straight-line to road distance multiplier
AVG_SPEED_KMH = 50.0     # average road speed in India
BATCH_SIZE = 100          # save every N centroids
REQUEST_DELAY = 1.1       # seconds between Nominatim requests (fair use)

# ── Haversine ───────────────────────────────────────────────────────────────
def haversine(lat1, lng1, lat2, lng2):
    R = 6371.0
    dLat = math.radians(lat2 - lat1)
    dLng = math.radians(lng2 - lng1)
    a = math.sin(dLat/2)**2 + math.cos(math.radians(lat1)) * math.cos(math.radians(lat2)) * math.sin(dLng/2)**2
    return R * 2 * math.atan2(math.sqrt(a), math.sqrt(1 - a))

def proximity_level(c1, c2):
    if c1["clusterName"] == c2["clusterName"]: return "SAME_CLUSTER"
    z1 = (c1.get("zone") or "").strip().lower()
    z2 = (c2.get("zone") or "").strip().lower()
    if z1 and z2 and z1 == z2: return "SAME_ZONE"
    s1 = (c1.get("state") or "").strip().lower()
    s2 = (c2.get("state") or "").strip().lower()
    if s1 and s2 and s1 == s2: return "SAME_STATE"
    return "DIFFERENT_STATE"

# ── Geocode using Nominatim ─────────────────────────────────────────────────
def geocode(query):
    """Geocode a location string. Returns (lat, lng) or None."""
    try:
        params = {"q": query, "format": "json", "limit": 1, "countrycodes": "in"}
        resp = requests.get(NOMINATIM_URL, params=params, headers=NOMINATIM_HEADERS, timeout=10)
        if resp.status_code == 200 and resp.json():
            result = resp.json()[0]
            return float(result["lat"]), float(result["lon"])
    except Exception as e:
        pass
    return None

def geocode_cluster(cluster):
    """Try multiple strategies to geocode a cluster."""
    state = (cluster.get("state") or "").replace("\n", " ").strip()
    zone = (cluster.get("zone") or "").replace("\n", " ").strip()
    pincodes = cluster.get("pincodes") or {}

    # Strategy 1: first place name + state
    if pincodes:
        first_place = list(pincodes.values())[0].replace("\n", " ").strip()
        first_pincode = list(pincodes.keys())[0]
        result = geocode(f"{first_place}, {state}, India")
        if result: return result

        # Strategy 2: pincode directly
        result = geocode(f"{first_pincode}, India")
        if result: return result

    # Strategy 3: zone (district) + state
    if zone:
        result = geocode(f"{zone}, {state}, India")
        if result: return result

    # Strategy 4: just state
    if state:
        result = geocode(f"{state}, India")
        if result: return result

    return None

# ── Main ────────────────────────────────────────────────────────────────────
def seed_centroids(db):
    """Phase 1: Geocode all clusters and save centroids."""
    clusters = list(db.clusters.find())
    print(f"\n📍 Phase 1: Geocoding {len(clusters)} clusters...")
    print(f"   Rate: 1 request/sec → estimated {len(clusters) * REQUEST_DELAY / 60:.0f} minutes\n")

    # Load existing centroids to skip already geocoded
    existing = {c["clusterName"]: c for c in db.cluster_centroids.find()}
    print(f"   Existing centroids: {len(existing)} (will skip these)")

    batch = []
    geocoded = 0
    skipped = 0
    failed = 0

    for i, cluster in enumerate(clusters):
        cn = cluster["clusterName"]

        # Skip if already has coordinates
        if cn in existing and existing[cn].get("latitude"):
            skipped += 1
            continue

        coords = geocode_cluster(cluster)
        time.sleep(REQUEST_DELAY)  # Nominatim fair use

        if coords:
            lat, lng = coords
            state = (cluster.get("state") or "").replace("\n", " ").strip()
            zone = (cluster.get("zone") or "").replace("\n", " ").strip()
            pincodes = cluster.get("pincodes") or {}
            city = zone if zone else (list(pincodes.values())[0] if pincodes else cn)

            batch.append(UpdateOne(
                {"clusterName": cn},
                {"$set": {
                    "clusterName": cn,
                    "state": state,
                    "zone": zone,
                    "city": city.replace("\n", " ").strip(),
                    "latitude": lat,
                    "longitude": lng,
                    "pincodeCount": len(pincodes),
                }},
                upsert=True
            ))
            geocoded += 1
        else:
            failed += 1

        # Progress
        total_processed = geocoded + skipped + failed
        if total_processed % 50 == 0:
            print(f"   Progress: {total_processed}/{len(clusters)} "
                  f"(geocoded={geocoded}, skipped={skipped}, failed={failed})")

        # Batch save
        if len(batch) >= BATCH_SIZE:
            db.cluster_centroids.bulk_write(batch)
            print(f"   💾 Saved batch of {len(batch)} centroids")
            batch = []

    # Save remaining
    if batch:
        db.cluster_centroids.bulk_write(batch)

    print(f"\n   ✅ Geocoding complete: {geocoded} new, {skipped} skipped, {failed} failed")
    return geocoded

def precompute_distances(db):
    """Phase 2: Compute all-pairs distances."""
    centroids = list(db.cluster_centroids.find({"latitude": {"$ne": None}}))
    n = len(centroids)
    total_pairs = n * (n - 1) // 2
    print(f"\n📐 Phase 2: Computing distances for {n} centroids ({total_pairs} pairs)...")

    # Drop and rebuild
    db.cluster_distances.drop()

    batch = []
    computed = 0

    for i in range(n):
        for j in range(i + 1, n):
            c1, c2 = centroids[i], centroids[j]

            # Normalize order
            if c1["clusterName"] > c2["clusterName"]:
                c1, c2 = c2, c1

            dist_km = haversine(c1["latitude"], c1["longitude"], c2["latitude"], c2["longitude"])
            road_km = dist_km * ROAD_FACTOR
            hours = road_km / AVG_SPEED_KMH

            batch.append({
                "fromCluster": c1["clusterName"],
                "toCluster": c2["clusterName"],
                "fromState": c1.get("state", ""),
                "toState": c2.get("state", ""),
                "fromZone": c1.get("zone", ""),
                "toZone": c2.get("zone", ""),
                "fromCity": c1.get("city", ""),
                "toCity": c2.get("city", ""),
                "distanceKm": round(dist_km, 1),
                "estimatedRoadKm": round(road_km, 1),
                "estimatedTransitHours": round(hours, 1),
                "proximityLevel": proximity_level(c1, c2),
            })
            computed += 1

            if len(batch) >= 5000:
                db.cluster_distances.insert_many(batch)
                print(f"   Saved {computed}/{total_pairs} distances...")
                batch = []

    if batch:
        db.cluster_distances.insert_many(batch)

    # Create indexes
    db.cluster_distances.create_index([("fromCluster", 1), ("toCluster", 1)], unique=True)
    db.cluster_distances.create_index([("fromCluster", 1), ("distanceKm", 1)])
    db.cluster_distances.create_index([("toCluster", 1), ("distanceKm", 1)])

    print(f"\n   ✅ Computed {computed} distances with indexes")
    return computed

def print_sample_heatmap(db, cluster_name="560"):
    """Print sample heatmap for verification."""
    print(f"\n🗺️  Sample Heatmap: Cluster {cluster_name}")
    print("=" * 60)

    results = list(db.cluster_distances.find(
        {"$or": [{"fromCluster": cluster_name}, {"toCluster": cluster_name}]}
    ).sort("distanceKm", 1).limit(20))

    tiers = {"0-50km": [], "50-100km": [], "100-200km": [], "200-500km": [], "500km+": []}

    for d in results:
        other = d["toCluster"] if d["fromCluster"] == cluster_name else d["fromCluster"]
        city = d["toCity"] if d["fromCluster"] == cluster_name else d["fromCity"]
        km = d["distanceKm"]
        entry = f"{other} ({city}) {km}km"

        if km <= 50: tiers["0-50km"].append(entry)
        elif km <= 100: tiers["50-100km"].append(entry)
        elif km <= 200: tiers["100-200km"].append(entry)
        elif km <= 500: tiers["200-500km"].append(entry)
        else: tiers["500km+"].append(entry)

    for tier, entries in tiers.items():
        if entries:
            print(f"\n  {tier}: {', '.join(entries[:5])}")
            if len(entries) > 5:
                print(f"    ... and {len(entries) - 5} more")

def main():
    distances_only = "--distances-only" in sys.argv

    client = MongoClient(MONGO_URI)
    db = client[DB_NAME]

    print("🚀 Cluster Distance Heatmap Generator")
    print(f"   MongoDB: {MONGO_URI}/{DB_NAME}")
    print(f"   Existing clusters: {db.clusters.count_documents({})}")
    print(f"   Existing centroids: {db.cluster_centroids.count_documents({})}")

    if not distances_only:
        seed_centroids(db)

    precompute_distances(db)

    # Stats
    centroid_count = db.cluster_centroids.count_documents({"latitude": {"$ne": None}})
    distance_count = db.cluster_distances.count_documents({})
    print(f"\n📊 Final Stats:")
    print(f"   Centroids with coordinates: {centroid_count}")
    print(f"   Precomputed distances: {distance_count}")
    print(f"   Coverage: {centroid_count} clusters, {distance_count} pairs")

    print_sample_heatmap(db)

    client.close()
    print("\n✅ Done!")

if __name__ == "__main__":
    main()
