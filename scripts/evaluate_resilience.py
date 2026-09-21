"""以真实模型、真实业务服务和本地故障代理验证取消、超时与进程重启。"""

import argparse
import hashlib
import http.client
import json
import os
import re
import secrets
import socket
import subprocess
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

from evaluate_luminous import api, request, verify_sandbox, docker_run, fixture_post_ids, cleanup, authorize_fixture_plan


TERMINAL = {"SUCCEEDED", "NEEDS_REVIEW", "FAILED", "CANCELLED"}
SCENARIOS = ["drop-write", "cancel-read", "deadline-read", "confirmation-expiry",
             "restart-waiting", "restart-read", "restart-write", "restart-wrong-key"]


class FaultProxy(ThreadingHTTPServer):
    """仅转发本轮允许的接口，不记录认证头与响应内容。"""

    def __init__(self, scenario):
        super().__init__(("127.0.0.1", 0), ProxyHandler)
        self.scenario = scenario
        self.reached = threading.Event()
        self.release = threading.Event()
        self.calls = []
        self.lock = threading.Lock()
        self.thread = threading.Thread(target=self.serve_forever, daemon=True)

    def start(self):
        self.thread.start()

    def close(self):
        self.release.set()
        self.shutdown()
        self.server_close()
        self.thread.join(timeout=5)


class ProxyHandler(BaseHTTPRequestHandler):
    def log_message(self, *_args):
        pass

    def do_GET(self):
        self.forward()

    def do_POST(self):
        self.forward()

    def forward(self):
        allowed = ((self.command == "POST" and self.path in {"/api/auth/login", "/api/posts"})
                   or (self.command == "GET" and (self.path == "/api/auth/me" or re.fullmatch(r"/api/posts/\d+", self.path))))
        if not allowed:
            self.send_error(403)
            return
        body = self.rfile.read(int(self.headers.get("Content-Length", 0)))
        headers = {k: v for k, v in self.headers.items() if k.lower() not in {"host", "connection", "content-length"}}
        upstream = http.client.HTTPConnection("127.0.0.1", 8080, timeout=15)
        try:
            upstream.request(self.command, self.path, body=body, headers=headers)
            response = upstream.getresponse()
            payload = response.read()
            with self.server.lock:
                self.server.calls.append({"method": self.command, "path": self.path, "status": response.status})
            scenario = self.server.scenario
            gate = ((scenario in {"cancel-read", "deadline-read"} and self.path == "/api/auth/me")
                    or (scenario == "restart-read" and self.command == "GET" and self.path.startswith("/api/posts/"))
                    or (scenario in {"drop-write", "restart-write"} and self.command == "POST" and self.path == "/api/posts"))
            if gate and not self.server.reached.is_set():
                self.server.reached.set()
                if scenario != "drop-write":
                    self.server.release.wait(timeout=25)
                if scenario in {"drop-write", "restart-write"}:
                    self.connection.shutdown(socket.SHUT_RDWR)
                    self.connection.close()
                    return
            self.send_response(response.status)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(payload)))
            self.end_headers()
            self.wfile.write(payload)
        except (BrokenPipeError, ConnectionResetError, OSError):
            # 被测进程被主动终止时，代理不能再把响应交给客户端。
            pass
        finally:
            upstream.close()


class ManagedApp:
    """仅管理本脚本创建的 Java 子进程，主密钥在本次评测内保持稳定。"""

    def __init__(self, args, output, scenario):
        self.args, self.output, self.scenario = args, output, scenario
        self.base = f"http://127.0.0.1:{args.port}"
        self.master = secrets.token_urlsafe(32)
        self.process = None
        self.logs = []
        self.launch_count = 0

    def start(self, change_key=False):
        with socket.socket() as probe:
            if probe.connect_ex(("127.0.0.1", self.args.port)) == 0:
                raise RuntimeError("评测应用端口已占用，拒绝接管已有进程")
        key_match = re.search(r"sk-[A-Za-z0-9_-]+", Path(self.args.key_file).read_text(encoding="utf-8-sig"))
        if not key_match:
            raise RuntimeError("密钥文件格式不正确")
        environment = os.environ.copy()
        environment.update({"SPRING_PROFILES_ACTIVE": "local,deepseek,lightweight",
            "AI_BASE_URL": "https://api.deepseek.com", "AI_CHAT_MODEL": "deepseek-flash",
            "AI_API_KEY": key_match.group(), "SECRET_STORE_MASTER_KEY": secrets.token_urlsafe(32) if change_key else self.master})
        self.launch_count += 1
        log = (self.output / f"app-{self.launch_count}.log").open("wb")
        self.logs.append(log)
        self.process = subprocess.Popen(["java", "-jar", "target/dochelper-0.0.1-SNAPSHOT.jar",
            f"--server.port={self.args.port}", "--server.address=127.0.0.1",
            "--dochelper.agent.task-timeout=" + ("12s" if self.scenario == "deadline-read" else "150s"),
            "--dochelper.agent.confirmation-timeout=" + ("3s" if self.scenario == "confirmation-expiry" else "120s"),
            "--dochelper.agent.recovery-interval-ms=1000", "--dochelper.executor.response-timeout=20s"],
            env=environment, stdout=log, stderr=subprocess.STDOUT,
            creationflags=subprocess.CREATE_NO_WINDOW if os.name == "nt" else 0)
        for _ in range(60):
            if self.process.poll() is not None:
                raise RuntimeError("评测应用启动失败，检查本轮应用日志")
            try:
                if request(self.base, "/actuator/health")["status"] == "UP":
                    return
            except Exception:
                pass
            time.sleep(1)
        raise RuntimeError("评测应用未按时就绪")

    def stop(self):
        if self.process and self.process.poll() is None:
            self.process.kill()
            self.process.wait(timeout=15)
        for log in self.logs:
            log.close()
        self.logs.clear()


def poll_task(base, path, statuses, timeout=90):
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        task = api(base, path)
        if task["status"] in statuses:
            return task
        time.sleep(0.25)
    raise RuntimeError("任务未在评测预算内进入目标状态")


def run_case(args, scenario):
    run_id = secrets.token_hex(5)
    output = Path("output/resilience") / f"{scenario}-{run_id}"
    output.mkdir(parents=True)
    username, password = "e2e_ap_" + run_id, secrets.token_urlsafe(18)
    marker = "[E2E_TEST] ApiPilot " + run_id
    target = "http://127.0.0.1:8080"
    proxy = FaultProxy(scenario)
    app = ManagedApp(args, output, scenario)
    evidence = {"scenario": scenario, "model": "deepseek-flash", "testId": run_id}
    auth = None
    started = time.monotonic()
    print(json.dumps({"scenario": scenario, "phase": "starting"}), flush=True)
    try:
        proxy.start()
        app.start()
        auth = api(target, "/api/auth/register", method="POST", payload={"username": username, "displayName": marker, "password": password})
        community = api(target, "/api/communities", method="POST", token=auth["token"], payload={
            "slug": "e2e-ap-" + run_id, "name": marker, "category": "测试", "description": marker})
        project = api(app.base, "/api/v1/projects", method="POST", payload={"code": "e2e-ap-" + run_id,
            "name": marker, "description": "故障与恢复验收"})
        prefix = f'/api/v1/projects/{project["id"]}'
        environment = api(app.base, prefix + "/environments", method="POST", payload={"name": "fault-proxy",
            "baseUrl": f"http://127.0.0.1:{proxy.server_port}", "allowedMethods": "GET,POST", "allowPrivateNetwork": True, "defaultEnvironment": True})
        schema = request(target, "/v3/api-docs")
        schema_bytes = json.dumps(schema, ensure_ascii=False).encode("utf-8")
        evidence["openapiSha256"] = hashlib.sha256(schema_bytes).hexdigest()
        evidence["backendImage"] = docker_run(args.docker, "inspect", "--format", "{{.Image}}", "luminous-dev-backend")
        boundary = "resilience-" + run_id
        raw = (f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="luminous.json"\r\nContent-Type: application/json\r\n\r\n').encode() + schema_bytes + f'\r\n--{boundary}--\r\n'.encode()
        api(app.base, prefix + "/openapi/imports", method="POST", raw=raw, content_type="multipart/form-data; boundary=" + boundary)
        task = api(app.base, prefix + "/agent-tasks", method="POST", payload={"environmentId": environment["id"],
            "goal": "依次完成四个步骤：用 username 和 password 登录；查询当前用户并验证用户名；"
                    "在 community 指定社区创建帖子，标题 title、正文 content；提取帖子 ID 查询详情并验证标题与正文。",
            "initialVariables": {"username": username, "password": password, "community": community["name"], "title": marker, "content": marker + " 正文"}})
        path = prefix + f'/agent-tasks/{task["id"]}'
        task = poll_task(app.base, path, TERMINAL | {"WAITING_CONFIRMATION"})
        if task["status"] != "WAITING_CONFIRMATION":
            evidence["task"] = task
            raise RuntimeError("真实模型没有产生可确认计划")
        authorize_fixture_plan(task, "posts", marker)
        requests = [s["request"] for s in task["plan"]]
        if len(requests) != 4 or requests[1]["path"] != "/api/auth/me" or requests[2]["path"] != "/api/posts":
            raise RuntimeError("模型未产生所需故障注入顺序，不能计为验收通过")
        evidence["initialTask"] = task
        if scenario in {"restart-waiting", "restart-wrong-key"}:
            app.stop()
            app.start(change_key=scenario == "restart-wrong-key")
        if scenario != "confirmation-expiry":
            api(app.base, path + "/confirmation", method="POST", payload={"approved": True,
                "planHash": task["confirmation"]["planHash"], "note": "[E2E_TEST] 故障验收白名单"})
        if scenario in {"cancel-read", "deadline-read", "restart-read", "restart-write", "drop-write"}:
            if not proxy.reached.wait(timeout=30):
                raise RuntimeError("没有到达真实业务故障注入点")
            if scenario == "cancel-read":
                api(app.base, path + "/cancellation", method="POST")
                proxy.release.set()
            elif scenario == "deadline-read":
                # 等待总预算实际耗尽，不修改数据库时间来模拟过期。
                time.sleep(13)
                proxy.release.set()
            elif scenario in {"restart-read", "restart-write"}:
                app.stop()
                proxy.release.set()
                app.start()
        task = poll_task(app.base, path, TERMINAL)
        evidence["task"] = task
        ids = fixture_post_ids(args.docker, username, auth["user"]["id"])
        items = [api(target, "/api/posts/" + value, token=auth["token"]) for value in ids]
        evidence["postCount"] = len(ids)
        evidence["proxyCalls"] = list(proxy.calls)
        writes = [call for call in proxy.calls if call["method"] == "POST" and call["path"] == "/api/posts"]
        expected = {"drop-write": ("NEEDS_REVIEW", 1), "cancel-read": ("CANCELLED", 0), "deadline-read": ("FAILED", 0),
            "confirmation-expiry": ("FAILED", 0), "restart-waiting": ("SUCCEEDED", 1), "restart-read": ("SUCCEEDED", 1),
            "restart-write": ("NEEDS_REVIEW", 1), "restart-wrong-key": ("FAILED", 0)}
        status, count = expected[scenario]
        verified = task["status"] == status and len(ids) == len(writes) == count
        verified &= all(item["title"] == marker and item["content"] == marker + " 正文"
                        and item["author"]["username"] == username for item in items)
        if scenario in {"drop-write", "restart-write"}:
            verified &= any((call.get("response") or {}).get("errorCode") == "EXECUTOR_409_003" for call in task["toolCalls"])
        if scenario in {"deadline-read", "confirmation-expiry", "restart-wrong-key"}:
            verified &= task["errorCode"] == {"deadline-read": "AGENT_408_001", "confirmation-expiry": "AGENT_409_002",
                "restart-wrong-key": "AGENT_409_005"}[scenario]
        if scenario in {"confirmation-expiry", "restart-wrong-key"}:
            verified &= not proxy.calls
        evidence["verified"] = verified
    except Exception as exc:
        evidence["error"] = str(exc)
        evidence["verified"] = False
    finally:
        # 先停止自己的客户端进程并等代理请求结束，再清理远端夹具，避免清理与写入竞争。
        app.stop()
        proxy.close()
        if auth:
            try:
                cleanup(args.docker, username, auth["user"]["id"], target, auth["token"])
                evidence["cleanup"] = "数据库与专属缓存已清理"
            except Exception as exc:
                evidence["cleanupError"] = str(exc)
        evidence["durationSeconds"] = round(time.monotonic() - started, 2)
        serialized = json.dumps(evidence, ensure_ascii=False, indent=2)
        for value in [password, auth["token"] if auth else ""]:
            if value:
                serialized = serialized.replace(value, "[REDACTED]")
        (output / "result.json").write_text(serialized, encoding="utf-8")
        print(json.dumps({"scenario": scenario, "verified": evidence["verified"], "error": evidence.get("error"),
            "cleanupError": evidence.get("cleanupError"), "evidence": str(output / "result.json")}, ensure_ascii=False), flush=True)
    return evidence["verified"] and not evidence.get("cleanupError")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--docker", required=True)
    parser.add_argument("--key-file", required=True)
    parser.add_argument("--port", type=int, default=18082)
    parser.add_argument("--scenario", choices=SCENARIOS + ["all"], default="all")
    args = parser.parse_args()
    verify_sandbox(args.docker)
    # 恢复扫描作用于同一 dochelper 数据库，评测时不能让另一个实例用不同主密钥竞争领取任务。
    if args.port != 18081:
        with socket.socket() as probe:
            if probe.connect_ex(("127.0.0.1", 18081)) == 0:
                raise RuntimeError("请先停止自己的 18081 开发实例，避免与恢复评测竞争；脚本不会接管既有进程")
    for scenario in SCENARIOS if args.scenario == "all" else [args.scenario]:
        if not run_case(args, scenario):
            return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
