"""Copyright (c) 2026 杨玺龙. SPDX-License-Identifier: MIT.
Disposable local MySQL test harness; no ports, persistent mounts or clinic settings.
"""
import json
import os
import secrets
import subprocess
import time

IMAGE = "mysql:8.4.10@sha256:8dbcf531a03aade657e181b9cf2f1d1803ce621a1d55610cb44cb531ab7d7db6"


class SyntheticMySQL:
    def __init__(self):
        self.password = secrets.token_urlsafe(40)
        self.container = "rehab-isolated-mysql-" + secrets.token_hex(6)
        self.owner = secrets.token_hex(24)
        self.local_target_verified = False

    def environment(self):
        return dict(os.environ, MYSQL_PWD=self.password, MYSQL_ROOT_PASSWORD=self.password)

    def command(self, args, data=None, check=True, timeout=60):
        result = subprocess.run(["docker", *args], input=data, capture_output=True,
                                text=True, env=self.environment(), timeout=timeout)
        if check and result.returncode:
            raise RuntimeError(f"isolated MySQL command failed (exit {result.returncode}); response omitted")
        return result

    def start(self):
        host = os.environ.get("DOCKER_HOST", "")
        if host and not host.startswith(("unix://", "npipe://")):
            raise RuntimeError("test refuses a remote Docker daemon")
        endpoint = json.loads(self.command(["context", "inspect"]).stdout)[0]["Endpoints"]["docker"]["Host"]
        if not endpoint.startswith(("unix://", "npipe://")):
            raise RuntimeError("test requires a local Docker socket")
        self.local_target_verified = True
        self.command(["run", "--detach", "--name", self.container, "--label", "com.rehab.synthetic-test=" + self.owner, "--network", "none",
                      "--tmpfs", "/var/lib/mysql:rw,size=512m", "--env", "MYSQL_ROOT_PASSWORD", IMAGE], timeout=180)
        info = json.loads(self.command(["inspect", self.container]).stdout)[0]
        if (info["Name"] != "/" + self.container or info["HostConfig"]["NetworkMode"] != "none"
                or info["HostConfig"].get("Binds") or info["HostConfig"].get("PortBindings")
                or any(m["Type"] != "tmpfs" for m in info.get("Mounts", []))):
            raise RuntimeError("unexpected synthetic container identity or storage/network configuration")
        for _ in range(90):
            # Do not mistake the image entrypoint's temporary socket server for readiness.
            process = self.command(["exec", self.container, "cat", "/proc/1/comm"], check=False, timeout=10)
            result = self.command(["exec", "--env", "MYSQL_PWD", self.container,
                                   "mysql", "-uroot", "--batch", "--skip-column-names",
                                   "--execute", "SELECT 1"], check=False, timeout=10)
            if process.stdout.strip() == "mysqld" and result.returncode == 0 and result.stdout.strip() == "1":
                return
            time.sleep(1)
        raise RuntimeError("synthetic MySQL startup timeout")

    def close(self):
        if self.local_target_verified:
            owned = self.command(["inspect", "--format", '{{index .Config.Labels "com.rehab.synthetic-test"}}', self.container], check=False)
            if owned.returncode == 0 and owned.stdout.strip() == self.owner:
                self.command(["rm", "--force", self.container], check=False)

    def sql(self, value, database=None, check=True):
        args = ["exec", "--interactive", "--env", "MYSQL_PWD", self.container,
                "mysql", "-uroot", "--default-character-set=utf8mb4", "--batch", "--skip-column-names", "--binary-mode"]
        if database:
            args.append(database)
        return self.command(args, value, check=check)
