"""运行最终真实模型回归集，并汇总候选召回与端到端任务指标。"""

import argparse
import hashlib
import json
import math
import subprocess
import sys
from datetime import datetime, timezone
from pathlib import Path
from urllib.request import urlopen


SCENARIOS = ["login", "posts", "repair", "assertion", "write-review", "missing-variable"]
EXPECTED_PATHS = {
    "login": {"/api/auth/login", "/api/auth/me"},
    "posts": {"/api/auth/login", "/api/posts"},
    "repair": {"/api/auth/login", "/api/posts"},
    "assertion": {"/api/auth/login", "/api/posts"},
    "write-review": {"/api/auth/login", "/api/posts"},
    "missing-variable": {"/api/auth/login", "/api/posts"},
}


def api_get(base_url, path):
    with urlopen(base_url + path, timeout=30) as response:
        payload = json.loads(response.read())
    if payload.get("code", "SUCCESS") != "SUCCESS":
        raise RuntimeError("读取 ApiPilot 评测证据失败")
    return payload["data"]


def run_scenario(args, scenario, run_label):
    command = [
        sys.executable,
        str(Path(__file__).with_name("evaluate_luminous.py")),
        "--docker", args.docker,
        "--apipilot", args.apipilot,
        "--label", f"{run_label}-{scenario}",
        "--scenario", scenario,
    ]
    result = subprocess.run(
        command,
        cwd=Path(__file__).resolve().parent.parent,
        capture_output=True,
        text=True,
        encoding="utf-8",
        errors="replace",
        timeout=args.scenario_timeout,
    )
    output_lines = [line for line in result.stdout.splitlines() if line.strip()]
    if not output_lines:
        raise RuntimeError(f"场景 {scenario} 没有输出评测证据")
    final_line = json.loads(output_lines[-1])
    evidence_path = Path(final_line["evidence"])
    evidence = json.loads(evidence_path.read_text(encoding="utf-8"))
    if result.returncode or final_line.get("error") or final_line.get("cleanupError"):
        raise RuntimeError(f"场景 {scenario} 失败，证据：{evidence_path}")
    case = evidence["cases"][0]
    task = case["task"]
    reports = api_get(
        args.apipilot,
        f"/api/v1/projects/{evidence['projectId']}/reports?limit=20",
    )
    report = next((item for item in reports if str(item["taskId"]) == str(task["id"])), None)
    plan_paths = {step["request"]["path"] for step in task.get("plan", [])}
    expected_paths = EXPECTED_PATHS[scenario]
    normalized_paths = {
        "/api/posts" if path.startswith("/api/posts/") else path
        for path in plan_paths
    }
    model_calls = task.get("modelCalls") or []
    return {
        "scenario": scenario,
        "taskId": task["id"],
        "taskStatus": task["status"],
        "businessVerified": bool(case.get("businessVerified")),
        "validPlan": bool(task.get("plan")) and expected_paths.issubset(normalized_paths),
        "planPaths": sorted(plan_paths),
        "reportGenerated": report is not None,
        "reportStatus": report.get("status") if report else None,
        "durationSeconds": case["durationSeconds"],
        "modelTokens": sum(int(call.get("totalTokens") or 0) for call in model_calls),
        "modelDurationMs": sum(int(call.get("durationMs") or 0) for call in model_calls),
        "toolCallCount": len(task.get("toolCalls") or []),
        "replanCount": int(task.get("replanCount") or 0),
        "evidence": str(evidence_path),
    }


def percentile95(values):
    if not values:
        return 0
    ordered = sorted(values)
    return ordered[max(0, math.ceil(len(ordered) * 0.95) - 1)]


def sha256_files(paths):
    digest = hashlib.sha256()
    for path in paths:
        digest.update(path.read_bytes())
    return digest.hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--docker", required=True)
    parser.add_argument("--apipilot", default="http://127.0.0.1:18081")
    parser.add_argument(
        "--candidate-metrics",
        default="target/evaluation/candidate-recall.json",
    )
    parser.add_argument("--scenario-timeout", type=int, default=300)
    args = parser.parse_args()

    started_at = datetime.now(timezone.utc)
    run_label = "final-" + started_at.strftime("%Y%m%dT%H%M%SZ")
    results = []
    for scenario in SCENARIOS:
        result = run_scenario(args, scenario, run_label)
        results.append(result)
        print(json.dumps(result, ensure_ascii=False), flush=True)

    candidate_metrics = json.loads(Path(args.candidate_metrics).read_text(encoding="utf-8"))
    durations = [item["durationSeconds"] for item in results]
    verified_count = sum(item["businessVerified"] for item in results)
    valid_plan_count = sum(item["validPlan"] for item in results)
    report_count = sum(item["reportGenerated"] for item in results)
    success_count = sum(item["taskStatus"] == "SUCCEEDED" for item in results)
    negative_results = [item for item in results if item["scenario"] in {
        "assertion", "write-review", "missing-variable"
    }]
    summary = {
        "datasetVersion": "api-pilot-final-regression-v1",
        "startedAt": started_at.isoformat(),
        "completedAt": datetime.now(timezone.utc).isoformat(),
        "model": "deepseek-flash",
        "candidateRecall": {
            key: candidate_metrics[key]
            for key in [
                "caseCount", "serviceCount", "expectedOperationCount",
                "singleOperationCaseCount", "top1Accuracy", "mrr",
                "operationRecallAt3", "operationRecallAt12", "caseCompleteRecallAt12",
            ]
        },
        "realModel": {
            "caseCount": len(results),
            "expectedOutcomePassRate": verified_count / len(results),
            "validPlanRate": valid_plan_count / len(results),
            "taskSucceededRate": success_count / len(results),
            "negativeCaseSafeStopRate": sum(item["businessVerified"] for item in negative_results) / len(negative_results),
            "reportGenerationRate": report_count / len(results),
            "totalModelTokens": sum(item["modelTokens"] for item in results),
            "averageModelTokens": round(sum(item["modelTokens"] for item in results) / len(results), 2),
            "p95TaskDurationSeconds": percentile95(durations),
            "averageTaskDurationSeconds": round(sum(durations) / len(durations), 2),
        },
        "reviewStateVerified": any(
            item["scenario"] == "write-review"
            and item["taskStatus"] == "NEEDS_REVIEW"
            and item["reportStatus"] == "NEEDS_REVIEW"
            for item in results
        ),
        "implementationSha256": sha256_files([
            Path("src/main/java/com/dochelper/agent/application/OpenApiCandidateSelector.java"),
            Path("src/main/java/com/dochelper/agent/application/AgentTaskRunner.java"),
            Path("src/main/java/com/dochelper/agent/application/AgentTaskAdmissionService.java"),
        ]),
        "cases": results,
        "limitations": [
            "候选召回数据来自仓库内固定的三份 OpenAPI 和五十条中文目标。",
            "真实模型数据为每个场景单次运行，用于回归证据，不代表长期成功率。",
            "taskSucceededRate 只统计 SUCCEEDED；预期失败和 NEEDS_REVIEW 由 expectedOutcomePassRate 评价。",
        ],
    }
    output_dir = Path("output/evaluation") / run_label
    output_dir.mkdir(parents=True, exist_ok=False)
    output_path = output_dir / "summary.json"
    output_path.write_text(json.dumps(summary, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps({"summary": str(output_path)}, ensure_ascii=False), flush=True)

    required = (
        verified_count == len(results)
        and valid_plan_count == len(results)
        and report_count == len(results)
        and summary["reviewStateVerified"]
        and candidate_metrics["operationRecallAt12"] >= 0.95
    )
    return 0 if required else 1


if __name__ == "__main__":
    raise SystemExit(main())
