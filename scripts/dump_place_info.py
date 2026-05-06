"""
dump_place_info.py
ChromaDB place_info 컬렉션을 사람이 읽을 수 있는 형식으로 출력/저장한다.

사용법:
    python scripts/dump_place_info.py             # 터미널에 표 출력
    python scripts/dump_place_info.py --csv       # place_info.csv 저장
    python scripts/dump_place_info.py --json      # place_info.json 저장
    python scripts/dump_place_info.py --id <ufid> # 특정 건물 상세 출력
"""

import argparse
import csv
import json
import os
import sys

import chromadb

CHROMA_PATH = "./chroma_db"
COLLECTION  = "place_info"


def get_collection():
    client = chromadb.PersistentClient(path=CHROMA_PATH)
    return client.get_or_create_collection(COLLECTION)


def fetch_all(collection) -> list[dict]:
    result = collection.get(include=["metadatas", "documents"])
    rows = []
    ids       = result.get("ids", [])
    metadatas = result.get("metadatas", []) or []
    documents = result.get("documents", []) or []

    for i, doc_id in enumerate(ids):
        meta = metadatas[i] if i < len(metadatas) else {}
        doc  = documents[i]  if i < len(documents)  else ""
        rows.append({"id": doc_id, "document": doc, **meta})
    return rows


def print_table(rows: list[dict]):
    if not rows:
        print("place_info 컬렉션이 비어있습니다.")
        return

    print(f"\n{'#':>3}  {'건물명':<22}  {'층':<4}  {'source':<10}  {'coverage':<8}  {'last_updated':<25}  ID")
    print("-" * 110)
    for i, r in enumerate(rows, 1):
        name     = (r.get("name_ko") or "")[:20]
        grnd_flr = r.get("grnd_flr", "")
        source   = r.get("source", "manual")[:10]
        coverage = f"{float(r.get('coverage_rate', 0)):.0%}" if r.get("coverage_rate") is not None else "-"
        updated  = (r.get("last_updated") or "")[:19]
        doc_id   = r["id"]

        # floor_info에서 층 수 파악
        try:
            fi = json.loads(r.get("floor_info", "[]"))
            floor_cnt = f"{len(fi)}층"
        except Exception:
            floor_cnt = "-"

        print(f"{i:>3}  {name:<22}  {floor_cnt:<4}  {source:<10}  {coverage:<8}  {updated:<25}  {doc_id}")

    print(f"\n총 {len(rows)}개 건물\n")


def print_detail(rows: list[dict], target_id: str):
    row = next((r for r in rows if r["id"] == target_id), None)
    if not row:
        print(f"ID {target_id!r} 없음")
        return

    print(f"\n{'='*60}")
    print(f"건물명   : {row.get('name_ko', '')}")
    print(f"ID       : {row['id']}")
    print(f"source   : {row.get('source', 'manual')}")
    print(f"좌표     : {row.get('lat', '')}, {row.get('lng', '')}")
    print(f"주소     : {row.get('addr', '')}")
    print(f"전화     : {row.get('phone', '')}")
    print(f"카테고리 : {row.get('category', '')}")
    print(f"coverage : {row.get('coverage_rate', '-')}")
    print(f"갱신일시 : {row.get('last_updated', '-')}")
    print(f"\n[floor_info]")
    try:
        fi = json.loads(row.get("floor_info", "[]"))
        if not fi:
            print("  (없음)")
        else:
            for f in fi:
                floor = f.get("floor") or "미확인"
                stores = f.get("stores", [])
                print(f"  {floor}층: {', '.join(stores[:5])}" + ("..." if len(stores) > 5 else ""))
    except Exception as e:
        print(f"  파싱 오류: {e}")
    print()


def save_csv(rows: list[dict], path: str = "place_info.csv"):
    if not rows:
        print("데이터 없음")
        return
    # floor_info는 JSON 문자열 그대로 유지 (Excel에서 별도 파싱)
    fieldnames = ["id", "name_ko", "source", "lat", "lng", "addr", "phone",
                  "category", "coverage_rate", "last_updated", "floor_info"]
    with open(path, "w", newline="", encoding="utf-8-sig") as f:
        writer = csv.DictWriter(f, fieldnames=fieldnames, extrasaction="ignore")
        writer.writeheader()
        writer.writerows(rows)
    print(f"CSV 저장: {path}  ({len(rows)}행)")


def save_json(rows: list[dict], path: str = "place_info.json"):
    # floor_info JSON 문자열을 파싱해서 nested object로 저장
    out = []
    for r in rows:
        row = dict(r)
        try:
            row["floor_info"] = json.loads(row.get("floor_info", "[]"))
        except Exception:
            pass
        out.append(row)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(out, f, ensure_ascii=False, indent=2)
    print(f"JSON 저장: {path}  ({len(out)}건물)")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--csv",  action="store_true", help="place_info.csv 저장")
    parser.add_argument("--json", action="store_true", help="place_info.json 저장")
    parser.add_argument("--id",   type=str, default="", help="특정 ufid 상세 출력")
    args = parser.parse_args()

    if not os.path.exists(CHROMA_PATH):
        print(f"ChromaDB 경로 없음: {CHROMA_PATH}")
        sys.exit(1)

    col  = get_collection()
    rows = fetch_all(col)

    if args.id:
        print_detail(rows, args.id)
    elif args.csv:
        save_csv(rows)
    elif args.json:
        save_json(rows)
    else:
        print_table(rows)


if __name__ == "__main__":
    main()
