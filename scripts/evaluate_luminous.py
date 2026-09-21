"""在独立 Luminous 沙箱运行真实 Agent 基线，并核验业务状态与清理本次资源。"""

import argparse
import hashlib
import json
import re
import secrets
import subprocess
import time
import urllib.error
import urllib.request
from datetime import datetime, timezone
from pathlib import Path


def request(base, path, method="GET", payload=None, token=None, raw=None, content_type=None):
    headers = {"Content-Type": content_type or "application/json"}
    if token:
        headers["Authorization"] = "Bearer " + token
    data = raw if raw is not None else (
        json.dumps(payload, ensure_ascii=False).encode("utf-8") if payload is not None else None
    )
    req = urllib.request.Request(base + path, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=90) as response:
            body = response.read()
            return json.loads(body) if body else None
    except urllib.error.HTTPError as exc:
        # 不输出服务端原始响应，避免失败信息夹带运行凭据。
        raise RuntimeError(f"HTTP {exc.code}: {method} {path}") from None


def api(base, path, **kwargs):
    result = request(base, path, **kwargs)
    if result and (result.get("success") is False or result.get("code", "SUCCESS") != "SUCCESS"):
        raise RuntimeError(f"业务请求失败: {path}")
    return result["data"] if result else None


def docker_run(executable, *args, input_text=None):
    result = subprocess.run([executable, *args], input=input_text, capture_output=True,
                            text=True, encoding="utf-8", errors="replace", timeout=45)
    if result.returncode:
        raise RuntimeError("Docker 操作失败，请检查测试沙箱状态")
    return result.stdout.strip()


def verify_sandbox(executable):
    project = docker_run(executable, "inspect", "--format",
                         '{{index .Config.Labels "com.docker.compose.project"}}', "luminous-dev-backend")
    port = docker_run(executable, "port", "luminous-dev-backend", "8080/tcp")
    if project != "luminous-dev" or not any(line.endswith(":8080") for line in port.splitlines()):
        raise RuntimeError("目标不是预期 luminous-dev:8080 沙箱，禁止执行")


def authorize_fixture_plan(task, scenario, marker):
    allowed = {("POST", "/api/auth/login"), ("GET", "/api/auth/me")}
    if scenario != "login":
        allowed |= {("POST", "/api/posts"), ("GET", "/api/posts/{id}")}
    for step in task["plan"]:
        req = step["request"]
        path = re.sub(r"^/api/posts/\{\{[A-Za-z][A-Za-z0-9_]*}}$", "/api/posts/{id}", req["path"])
        if (req["method"].upper(), path) not in allowed:
            raise RuntimeError("模型计划超出本轮夹具允许的接口范围，未批准")
        if req["method"].upper() == "POST" and req["path"] == "/api/posts":
            if (req.get("body") or {}).get("title") not in ("{{title}}", marker):
                raise RuntimeError("模型未使用带测试标记的标题，未批准")


def await_task(base, prefix, task_id, scenario, marker):
    deadline = time.monotonic() + 200
    approved = set()
    injected = False
    while time.monotonic() < deadline:
        task = api(base, f"{prefix}/agent-tasks/{task_id}")
        if task["status"] in {"SUCCEEDED", "NEEDS_REVIEW", "FAILED", "CANCELLED"}:
            return task
        if task["status"] == "WAITING_CONFIRMATION":
            confirmation = task["confirmation"]
            if scenario == "reject":
                api(base, f"{prefix}/agent-tasks/{task_id}/confirmation", method="POST", payload={
                    "approved": False, "note": "[E2E_TEST] 验证拒绝执行", "planHash": confirmation["planHash"]})
                continue
            instructions = {
                "repair": "仅在最后一个 GET 查询步骤添加一个 extractor，name 为 verifiedTitle，jsonPath 必须为 $.missingTitle。",
                "stale": "仅在最后一个 GET 查询步骤添加一个 extractor，name 为 verifiedTitle，jsonPath 必须为 $.missingTitle。",
                "assertion": '仅在最后一个 GET 查询步骤添加 FIELD_EQUALS 断言，jsonPath 为 $.data.title，expectedValue 为 "[E2E_TEST] impossible-title"。',
                "write-review": "仅在创建帖子 POST /api/posts 步骤添加一个 extractor，name 为 invalidWriteField，jsonPath 必须为 $.missingWriteField。",
                "missing-variable": "仅将最后一个 GET 查询步骤的 path 改为 /api/posts/{{undefinedFixtureId}}，并清空其 pathVariables；不要定义 undefinedFixtureId。"
            }
            if scenario in instructions and not injected:
                previous_hash = confirmation["planHash"]
                task = api(base, f"{prefix}/agent-tasks/{task_id}/modify", method="POST", payload={
                    "instruction": "这是受控故障测试：" + instructions[scenario] + "保留其他全部步骤、参数和断言，不做其他修改。"})
                extractors = task["plan"][-1]["request"].get("extractors") or []
                if scenario in {"repair", "stale"} and not any(e["name"] == "verifiedTitle" and e["jsonPath"] == "$.missingTitle" for e in extractors):
                    raise RuntimeError("真实模型未按要求注入提取故障，不能将本轮计为恢复评测")
                if scenario == "assertion" and not any(a.get("type") == "FIELD_EQUALS" and a.get("jsonPath") == "$.data.title"
                        and a.get("expectedValue") == "[E2E_TEST] impossible-title" for a in task["plan"][-1]["request"].get("assertions", [])):
                    raise RuntimeError("模型未注入指定断言，不能计为断言保护验收")
                if scenario == "write-review" and not any(e.get("name") == "invalidWriteField" and e.get("jsonPath") == "$.missingWriteField"
                        for s in task["plan"] if s["request"]["method"] == "POST" and s["request"]["path"] == "/api/posts"
                        for e in s["request"].get("extractors", [])):
                    raise RuntimeError("模型未注入写响应提取故障")
                if scenario == "missing-variable" and task["plan"][-1]["request"]["path"] != "/api/posts/{{undefinedFixtureId}}":
                    raise RuntimeError("模型未注入缺失变量")
                injected = True
                confirmation = task["confirmation"]
                if scenario == "stale":
                    try:
                        api(base, f"{prefix}/agent-tasks/{task_id}/confirmation", method="POST", payload={
                            "approved": True, "planHash": previous_hash})
                    except RuntimeError as exc:
                        if not str(exc).startswith("HTTP 409:"):
                            raise
                    else:
                        raise RuntimeError("旧版本确认未被拒绝")
                    unchanged = api(base, f"{prefix}/agent-tasks/{task_id}")
                    if unchanged["status"] != "WAITING_CONFIRMATION" or unchanged["confirmation"]["status"] != "PENDING":
                        raise RuntimeError("旧确认请求改变了任务执行状态")
                    api(base, f"{prefix}/agent-tasks/{task_id}/cancellation", method="POST")
                    cancelled = api(base, f"{prefix}/agent-tasks/{task_id}")
                    cancelled["evaluationOldHashRejected"] = True
                    return cancelled
            if confirmation["status"] == "PENDING" and confirmation["planHash"] not in approved:
                authorize_fixture_plan(task, scenario, marker)
                api(base, f"{prefix}/agent-tasks/{task_id}/confirmation", method="POST", payload={
                    "approved": True, "note": "[E2E_TEST] 夹具白名单审核", "planHash": confirmation["planHash"]})
                approved.add(confirmation["planHash"])
        time.sleep(1)
    api(base, f"{prefix}/agent-tasks/{task_id}/cancellation", method="POST")
    raise RuntimeError("任务超过评测时间预算，已请求取消；清理前需确认终止")


def fixture_post_ids(executable, username, user_id):
    if not re.fullmatch(r"e2e_ap_[0-9a-f]+", username) or not str(user_id).isdigit():
        raise RuntimeError("清理范围校验失败")
    sql = f"""SELECT p.id FROM posts p JOIN users u ON p.author_id=u.id
        WHERE u.id={user_id} AND u.username='{username}' AND u.display_name LIKE '[E2E_TEST]%';"""
    result = docker_run(executable, "exec", "-i", "luminous-dev-mysql", "sh", "-c",
                        'MYSQL_PWD="$MYSQL_PASSWORD" mysql -u"$MYSQL_USER" "$MYSQL_DATABASE" -N', input_text=sql)
    ids = result.splitlines() if result else []
    if any(not value.isdigit() for value in ids):
        raise RuntimeError("夹具资源 ID 校验失败")
    return ids


def cleanup(executable, username, user_id, target, token):
    ids = fixture_post_ids(executable, username, user_id)
    # 先走被测系统的删除接口，连带失效进程内缓存；数据库清理仅限本次夹具。
    for post_id in ids:
        api(target, "/api/posts/" + post_id, method="DELETE", token=token)
    # 仅清理本轮账号拥有的资源，不使用全库测试标记批量删除。
    sql = f"""
    START TRANSACTION;
    SET @fixture_id=(SELECT id FROM users WHERE id={user_id} AND username='{username}'
        AND display_name LIKE '[E2E_TEST]%');
    DELETE FROM posts WHERE author_id=@fixture_id;
    DELETE FROM communities WHERE owner_id=@fixture_id;
    DELETE FROM users WHERE id=@fixture_id;
    COMMIT;
    SELECT COUNT(*) FROM users WHERE id={user_id};
    """
    result = docker_run(executable, "exec", "-i", "luminous-dev-mysql", "sh", "-c",
                        'MYSQL_PWD="$MYSQL_PASSWORD" mysql -u"$MYSQL_USER" "$MYSQL_DATABASE" -N', input_text=sql)
    if result.splitlines()[-1:] != ["0"]:
        raise RuntimeError("测试账号清理未完成")
    keys = ["luminous:cache:user:stats:" + str(user_id), "luminous:feed:inbox:" + str(user_id)]
    for post_id in ids:
        keys.extend(["luminous:cache:post:detail:" + post_id, "luminous:stat:post:uv:" + post_id])
    docker_run(executable, "exec", "luminous-dev-redis", "redis-cli", "UNLINK", *keys)
    if docker_run(executable, "exec", "luminous-dev-redis", "redis-cli", "EXISTS", *keys) != "0":
        raise RuntimeError("夹具缓存清理未完成")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--docker", required=True)
    parser.add_argument("--apipilot", default="http://127.0.0.1:18081")
    parser.add_argument("--label", default="baseline")
    parser.add_argument("--scenario", choices=["login", "posts", "repair", "reject", "stale", "assertion", "write-review", "missing-variable", "all"], default="all")
    args = parser.parse_args()
    verify_sandbox(args.docker)
    target = "http://127.0.0.1:8080"
    run_id = secrets.token_hex(5)
    username = "e2e_ap_" + run_id
    password = secrets.token_urlsafe(18)
    marker = "[E2E_TEST] ApiPilot " + run_id
    output = Path("output/evaluation") / (args.label + "-" + run_id)
    output.mkdir(parents=True, exist_ok=False)
    evidence = {"startedAt": datetime.now(timezone.utc).isoformat(), "label": args.label,
                "target": "luminous-dev:8080", "model": "deepseek-flash",
                "retrieval": "无业务文档；当前默认确定性向量，仅用于工程链路", "cases": []}
    auth = None
    task_id = None
    prefix = None
    safe_to_cleanup = True
    try:
        schema = request(target, "/v3/api-docs")
        schema_bytes = json.dumps(schema, ensure_ascii=False).encode("utf-8")
        evidence["openapiSha256"] = hashlib.sha256(schema_bytes).hexdigest()
        evidence["backendImage"] = docker_run(args.docker, "inspect", "--format", "{{.Image}}", "luminous-dev-backend")
        auth = api(target, "/api/auth/register", method="POST", payload={
            "username": username, "displayName": marker, "password": password})
        community = api(target, "/api/communities", method="POST", token=auth["token"], payload={
            "slug": "e2e-ap-" + run_id, "name": marker, "category": "测试", "description": marker})
        project = api(args.apipilot, "/api/v1/projects", method="POST", payload={
            "code": "e2e-ap-" + run_id, "name": marker, "description": "真实模型基线证据"})
        prefix = f'/api/v1/projects/{project["id"]}'
        evidence["projectId"] = project["id"]
        overview = api(args.apipilot, "/api/v1/system/overview")
        evidence["knowledgeEnabled"] = overview.get("knowledgeEnabled", True)
        evidence["retrieval"] = "知识库禁用，仅使用 OpenAPI 证据" if not evidence["knowledgeEnabled"] else evidence["retrieval"]
        environment = api(args.apipilot, prefix + "/environments", method="POST", payload={
            "name": "luminous-dev", "baseUrl": target, "allowedMethods": "GET,POST",
            "allowPrivateNetwork": True, "defaultEnvironment": True})
        boundary = "apipilot-" + run_id
        multipart = (f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="luminous.json"\r\n'
                     'Content-Type: application/json\r\n\r\n').encode() + schema_bytes + f'\r\n--{boundary}--\r\n'.encode()
        api(args.apipilot, prefix + "/openapi/imports", method="POST", raw=multipart,
            content_type="multipart/form-data; boundary=" + boundary)
        cases = [args.scenario] if args.scenario != "all" else ["login", "posts"]
        for scenario in cases:
            goal = ("用给定用户名和密码登录，查询当前用户，验证返回用户名等于初始变量 username。"
                    if scenario == "login" else
                    "用给定用户名和密码登录，在初始变量 community 指定的社区创建帖子，标题和正文分别使用 title、content。"
                    "提取新帖 ID 查询详情，验证标题、正文以及作者用户名与初始变量一致。")
            started = time.monotonic()
            task = api(args.apipilot, prefix + "/agent-tasks", method="POST", payload={
                "environmentId": environment["id"], "goal": goal, "initialVariables": {
                    "username": username, "password": password, "community": community["name"],
                    "title": marker, "content": marker + " 正文"}})
            task_id = task["id"]
            task = await_task(args.apipilot, prefix, task_id, scenario, marker)
            case = {"scenario": scenario, "durationSeconds": round(time.monotonic() - started, 2), "task": task}
            if scenario == "login":
                # 独立请求核验测试账号身份，同时检查 Agent 确实执行了所需步骤。
                identity = api(target, "/api/auth/me", token=auth["token"])
                paths = [s["request"]["path"] for s in task["plan"]]
                me_results = [step for call in task["toolCalls"] if call["toolName"] == "executeHttpRequest"
                    for step in (call.get("response") or {}).get("steps", [])
                    if (step.get("requestUrl") or "").endswith("/api/auth/me")]
                case["businessVerified"] = (task["status"] == "SUCCEEDED" and identity["username"] == username
                    and "/api/auth/login" in paths and "/api/auth/me" in paths and len(me_results) >= 1
                    and all((item.get("responseBody") or {}).get("data", {}).get("username") == username
                            for item in me_results))
            else:
                # 从数据库按专属账号核对数量，避免分页、公开状态或服务镜像版本差异掩盖重复写入。
                post_ids = fixture_post_ids(args.docker, username, auth["user"]["id"])
                items = [api(target, "/api/posts/" + post_id, token=auth["token"]) for post_id in post_ids]
                matching = [p for p in items if p.get("title") == marker]
                case["createdPostCount"] = len(post_ids)
                case["businessVerified"] = (task["status"] == "SUCCEEDED" and len(post_ids) == len(matching) == 1
                    and matching[0].get("content") == marker + " 正文"
                    and matching[0].get("author", {}).get("username") == username)
                if scenario == "repair":
                    calls = [call for call in task["toolCalls"] if call["toolName"] == "executeHttpRequest"]
                    last_call = max(calls, key=lambda call: (call["createdAt"], int(call["id"]))) if calls else {}
                    latest = (last_call.get("response") or {}).get("steps", [])
                    recovered_title = (latest[-1].get("extractedVariables") or {}).get("verifiedTitle") if latest else None
                    case["businessVerified"] = (case["businessVerified"] and task["replanCount"] >= 1
                        and task["modificationCount"] == 1 and recovered_title == marker)
                calls = [call for call in task["toolCalls"] if call["toolName"] == "executeHttpRequest"]
                if scenario in {"reject", "stale", "missing-variable"}:
                    case["businessVerified"] = (len(post_ids) == 0 and task["replanCount"] == 0
                        and (task["status"] == "FAILED" if scenario == "missing-variable" else task["status"] == "CANCELLED"))
                    if scenario in {"reject", "stale"}:
                        case["businessVerified"] &= not calls
                    if scenario == "stale":
                        case["businessVerified"] &= task.get("evaluationOldHashRejected", False)
                    if scenario == "missing-variable":
                        case["businessVerified"] &= ("undefinedFixtureId" in (task.get("errorMessage") or "")
                            and len(calls) == 1 and calls[0]["status"] == "FAILED"
                            and not (calls[0].get("response") or {}).get("steps"))
                if scenario in {"assertion", "write-review"}:
                    expected_code = "ASSERTION_FAILED" if scenario == "assertion" else "EXECUTOR_409_003"
                    expected_status = "FAILED" if scenario == "assertion" else "NEEDS_REVIEW"
                    case["businessVerified"] = (task["status"] == expected_status and task["replanCount"] == 0
                        and len(post_ids) == len(matching) == 1 and len(calls) == 1
                        and (calls[0].get("response") or {}).get("errorCode") == expected_code)
            evidence["cases"].append(case)
            print(json.dumps({"scenario": scenario, "status": task["status"],
                              "businessVerified": case["businessVerified"], "taskId": task_id}, ensure_ascii=False), flush=True)
    except Exception as exc:
        evidence["error"] = str(exc)
        if task_id and prefix:
            try:
                active = api(args.apipilot, f"{prefix}/agent-tasks/{task_id}")
                evidence["interruptedTask"] = active
                if active["status"] not in {"SUCCEEDED", "NEEDS_REVIEW", "FAILED", "CANCELLED"}:
                    api(args.apipilot, f"{prefix}/agent-tasks/{task_id}/cancellation", method="POST")
                    for _ in range(15):
                        active = api(args.apipilot, f"{prefix}/agent-tasks/{task_id}")
                        if active["status"] in {"SUCCEEDED", "NEEDS_REVIEW", "FAILED", "CANCELLED"}:
                            break
                        time.sleep(1)
                safe_to_cleanup = active["status"] in {"SUCCEEDED", "NEEDS_REVIEW", "FAILED", "CANCELLED"}
            except Exception:
                safe_to_cleanup = False
    finally:
        if auth and safe_to_cleanup:
            try:
                cleanup(args.docker, username, auth["user"]["id"], target, auth["token"])
                evidence["cleanup"] = "本轮账号及所属帖子、社区数据库记录已清理，帖子删除接口失效本地缓存，专属 Redis 键已清理并核验"
            except Exception as exc:
                evidence["cleanupError"] = str(exc)
        elif auth:
            evidence["cleanupError"] = "任务尚未确认终止，保留夹具以避免清理与执行竞态"
            evidence["fixtureUserId"] = auth["user"]["id"]
            evidence["fixtureUsername"] = username
        serialized = json.dumps(evidence, ensure_ascii=False, indent=2)
        for value in [password, auth["token"] if auth else ""]:
            if value:
                serialized = serialized.replace(value, "[REDACTED]")
        (output / "result.json").write_text(serialized, encoding="utf-8")
        print(json.dumps({"evidence": str(output / "result.json"), "error": evidence.get("error"),
                          "cleanupError": evidence.get("cleanupError")}, ensure_ascii=False), flush=True)
    return 1 if evidence.get("error") or evidence.get("cleanupError") or any(
        not case["businessVerified"] for case in evidence["cases"]) else 0


if __name__ == "__main__":
    raise SystemExit(main())
