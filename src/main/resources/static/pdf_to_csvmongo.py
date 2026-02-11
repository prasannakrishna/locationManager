#!/usr/bin/env python3
import sys
import pdfplumber
import json
from collections import defaultdict

def pdf_to_mongo_jsonl(input_pdf, output_jsonl="mongo_clusters2.jsonl"):
    clusters = defaultdict(lambda: {"pincodes": {}})

    with pdfplumber.open(input_pdf) as pdf:
        for page_num, page in enumerate(pdf.pages, 1):
            table = page.extract_table()
            if not table:
                continue

            for row in table:
                if not row or len(row) < 4:
                    continue

                place, pincode, state, zone = row[:4]

                if not pincode or not pincode.strip().isdigit():
                    continue

                place = place.strip()
                pincode = pincode.strip()
                state = state.strip()
                zone = zone.strip()
                cluster_name = pincode[:3]

                key = (state, cluster_name, zone)

                clusters[key]["pincodes"][pincode] = place
                clusters[key]["state"] = state
                clusters[key]["clusterName"] = cluster_name
                clusters[key]["zone"] = zone

    # Write JSONL file cluster by cluster
    with open(output_jsonl, "w", encoding="utf-8") as f:
        cluster_id_counter = 285
        for key, value in clusters.items():
            doc = {
                "clusterId": cluster_id_counter,
                "state": value["state"],
                "clusterName": value["clusterName"],
                "pincodes": value["pincodes"],
                "zone": value["zone"]
            }
            f.write(json.dumps(doc, ensure_ascii=False) + "\n")
            cluster_id_counter += 1

    print(f"✅ Extracted {cluster_id_counter-1} cluster records into {output_jsonl}")


if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("Usage: python pdf_to_mongo_jsonl.py <input_pdf>")
        sys.exit(1)

    input_pdf = sys.argv[1]
    pdf_to_mongo_jsonl(input_pdf)

