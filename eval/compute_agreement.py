"""
compute_agreement.py
사람 2명의 채점 결과 + LLM Judge 점수로 Inter-rater agreement 계산.

사용:
    python eval/compute_agreement.py
"""

import json
import sys
import pathlib
import csv

sys.stdout.reconfigure(encoding="utf-8", errors="replace")

CRITERIA = ["groundedness", "completeness", "language_match", "tts_naturalness", "conciseness"]
CRITERIA_KO = ["Groundedness", "Completeness", "Lang Match", "TTS Natural", "Conciseness"]


def krippendorff_alpha(ratings: list[list[float]]) -> float:
    """
    Krippendorff's Alpha (ordinal) 계산.
    ratings: [[rater1_score, rater2_score, ...], ...] — 케이스별 채점자 점수 목록
    """
    n_items = len(ratings)
    raters  = len(ratings[0])
    n_total = n_items * raters

    # 전체 평균
    all_vals = [v for row in ratings for v in row]
    mean     = sum(all_vals) / len(all_vals)

    # Do (관측된 불일치)
    do = 0.0
    for row in ratings:
        for i in range(len(row)):
            for j in range(i + 1, len(row)):
                do += (row[i] - row[j]) ** 2
    do /= (n_items * raters * (raters - 1) / 2)

    # De (기대 불일치) — 모든 쌍의 제곱 차이 평균
    de = 0.0
    for i in range(n_total):
        for j in range(n_total):
            if i != j:
                de += (all_vals[i] - all_vals[j]) ** 2
    de /= (n_total * (n_total - 1))

    if de == 0:
        return 1.0
    return 1.0 - (do / de)


def load_scores(csv_path: pathlib.Path) -> tuple[list, list, list]:
    """CSV에서 judge, rater1, rater2 점수 로드."""
    judge_scores  = {c: [] for c in CRITERIA}
    rater1_scores = {c: [] for c in CRITERIA}
    rater2_scores = {c: [] for c in CRITERIA}
    case_ids      = []

    with open(csv_path, encoding="utf-8-sig") as f:
        reader = csv.DictReader(f)
        for row in reader:
            # rater 점수가 비어있는 행 건너뜀
            r1_filled = all(row.get(f"G_rater1","").strip() for _ in [1])
            r2_filled = all(row.get(f"G_rater2","").strip() for _ in [1])
            if not r1_filled or not r2_filled:
                continue

            try:
                case_ids.append(row["케이스ID"])
                for c, col1, col2, colj in zip(
                    CRITERIA,
                    ["G_rater1","C_rater1","L_rater1","T_rater1","Cn_rater1"],
                    ["G_rater2","C_rater2","L_rater2","T_rater2","Cn_rater2"],
                    ["G_judge", "C_judge", "L_judge", "T_judge", "Cn_judge"],
                ):
                    judge_scores[c].append(float(row[colj]))
                    rater1_scores[c].append(float(row[col1]))
                    rater2_scores[c].append(float(row[col2]))
            except (ValueError, KeyError):
                continue

    return judge_scores, rater1_scores, rater2_scores, case_ids


def run() -> None:
    csv_path = pathlib.Path("eval/logs/human_rating_sheet.csv")
    if not csv_path.exists():
        print("human_rating_sheet.csv 없음")
        return

    judge, r1, r2, ids = load_scores(csv_path)

    if not ids:
        print("채점된 케이스가 없습니다. CSV에 rater1, rater2 점수를 입력해주세요.")
        return

    print(f"\n채점된 케이스: {len(ids)}개\n")
    print("=" * 60)
    print("  INTER-RATER AGREEMENT")
    print("=" * 60)

    print(f"\n[기준별 Krippendorff's Alpha]")
    print(f"  Alpha 해석: < 0.2=거의 불일치 / 0.4~0.6=보통 / 0.6~0.8=좋음 / 0.8+=매우 좋음\n")
    print(f"  {'기준':<20} {'Human A↔B':>12} {'Human A↔Judge':>15} {'Human B↔Judge':>15} {'3자 전체':>10}")
    print(f"  {'-'*74}")

    alphas_all = []
    for c, ck in zip(CRITERIA, CRITERIA_KO):
        j_vals  = judge[c]
        r1_vals = r1[c]
        r2_vals = r2[c]

        ab     = krippendorff_alpha([[r1_vals[i], r2_vals[i]] for i in range(len(ids))])
        aj     = krippendorff_alpha([[r1_vals[i], j_vals[i]] for i in range(len(ids))])
        bj     = krippendorff_alpha([[r2_vals[i], j_vals[i]] for i in range(len(ids))])
        all3   = krippendorff_alpha([[r1_vals[i], r2_vals[i], j_vals[i]] for i in range(len(ids))])
        alphas_all.append(all3)

        print(f"  {ck:<20} {ab:>12.3f} {aj:>15.3f} {bj:>15.3f} {all3:>10.3f}")

    print(f"  {'-'*74}")
    print(f"  {'전체 평균':<20} {'':>12} {'':>15} {'':>15} {sum(alphas_all)/len(alphas_all):>10.3f}")

    # 평균 점수 비교
    print(f"\n[기준별 평균 점수 비교 (1-5)]")
    print(f"  {'기준':<20} {'Rater1':>8} {'Rater2':>8} {'Judge':>8} {'R1-R2 차':>10} {'R1-J 차':>10}")
    print(f"  {'-'*66}")
    for c, ck in zip(CRITERIA, CRITERIA_KO):
        m1 = sum(r1[c]) / len(r1[c])
        m2 = sum(r2[c]) / len(r2[c])
        mj = sum(judge[c]) / len(judge[c])
        print(f"  {ck:<20} {m1:>8.2f} {m2:>8.2f} {mj:>8.2f} {abs(m1-m2):>10.2f} {abs(m1-mj):>10.2f}")

    # 케이스별 상세
    print(f"\n[케이스별 Rater1 ↔ Rater2 ↔ Judge 비교]")
    print(f"  {'ID':<28} {'기준':<16} {'R1':>4} {'R2':>4} {'J':>4}")
    print(f"  {'-'*60}")
    for i, cid in enumerate(ids):
        first = True
        for c, ck in zip(CRITERIA, CRITERIA_KO):
            v1 = int(r1[c][i])
            v2 = int(r2[c][i])
            vj = int(judge[c][i])
            flag = " ← 불일치" if abs(v1 - v2) >= 2 else ""
            id_col = cid if first else ""
            print(f"  {id_col:<28} {ck:<16} {v1:>4} {v2:>4} {vj:>4}{flag}")
            first = False

    print()


if __name__ == "__main__":
    run()
