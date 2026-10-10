#!/usr/bin/env python3
"""Headless *real Chrome* visual smoke for ephemeral synthetic Runtime only.

Runs against the localhost-only Compose project created by the sibling E2E
script. Never connects to patient data, never captures real credentials, and
never reuses the user's Chrome profile or touches the user's running Chrome.
The screenshots are evidence for human review, NOT a human sign-off.
"""
import asyncio
import base64
import json
import os
from pathlib import Path
import subprocess
import tempfile
import time
from urllib.request import urlopen

import websockets


CHROME = Path("/Applications/Google Chrome.app/Contents/MacOS/Google Chrome")
BASE = os.environ.get("REHAB_BROWSER_BASE", "")
TOKEN = os.environ.get("REHAB_BROWSER_TOKEN", "")
DEST = Path(os.environ.get("REHAB_BROWSER_EVIDENCE_DIR", ""))
SCENARIO = os.environ.get("REHAB_BROWSER_SCENARIO", "--upgrade-browser-smoke")


class CDP:
    def __init__(self, socket):
        self.socket = socket
        self.serial = 0

    async def call(self, method, params=None):
        self.serial += 1
        serial = self.serial
        await self.socket.send(json.dumps({"id": serial, "method": method,
                                           "params": params or {}}))
        while True:
            response = json.loads(await asyncio.wait_for(self.socket.recv(), 20))
            if response.get("id") == serial:
                if "error" in response:
                    raise RuntimeError(f"CDP {method}: {response['error']}")
                return response.get("result", {})

    async def js(self, expression):
        raw = await self.call("Runtime.evaluate", {
            "expression": expression, "returnByValue": True,
            "awaitPromise": True, "includeCommandLineAPI": False,
        })
        if "exceptionDetails" in raw:
            raise RuntimeError(f"Browser JavaScript failed: {raw['exceptionDetails']}")
        return raw.get("result", {}).get("value")

    async def navigate(self, url):
        await self.call("Page.navigate", {"url": url})

    async def capture(self, target):
        shot = await self.call("Page.captureScreenshot", {
            "format": "png", "captureBeyondViewport": False,
            "fromSurface": True,
        })
        payload = base64.b64decode(shot["data"])
        # Do not silently overwrite a previous visual-review artifact.
        fd = os.open(target, os.O_CREAT | os.O_EXCL | os.O_WRONLY, 0o600)
        with os.fdopen(fd, "wb") as handle:
            handle.write(payload)


async def wait_for(client, needle, seconds=24):
    until = time.monotonic() + seconds
    while time.monotonic() < until:
        data = await client.js("""(() => ({
          path:location.pathname, text:document.body?.innerText?.slice(0,25000)||'',
          ready:document.readyState
        }))()""")
        if needle in data["text"]:
            return data
        await asyncio.sleep(0.45)
    raise RuntimeError(f"Page did not display required text {needle!r}; path={data['path']}")

async def wait_for_login_form(client, seconds=24):
    until = time.monotonic() + seconds
    while time.monotonic() < until:
        visible = await client.js("""(() => {
          const form=document.querySelector('form.login-form');
          const pwd=form?.querySelector('input[type=password]');
          if (!form || !pwd) return false;
          const rect=form.getBoundingClientRect();
          const style=getComputedStyle(form);
          return rect.width>200 && rect.height>180 &&
            style.display!=='none' && style.visibility!=='hidden' &&
            form.querySelectorAll('input').length>=2;
        })()""")
        if visible:
            # Wait for the login form's Vue entrance animation to finish.
            await asyncio.sleep(1.35)
            return
        await asyncio.sleep(0.3)
    raise RuntimeError("Login page never presented a visible username/password form")


async def check_view(client, route, needle, title, width, height, mobile):
    await client.call("Emulation.setDeviceMetricsOverride", {
        "width": width, "height": height, "deviceScaleFactor": 1,
        "mobile": mobile, "screenWidth": width, "screenHeight": height,
    })
    await client.navigate(BASE + route)
    await wait_for(client, needle)
    await asyncio.sleep(1.1)
    data = await client.js("""(() => ({
      path:location.pathname,
      screenWidth:innerWidth,
      documentWidth:document.documentElement.scrollWidth,
      horizontalOverflow:Math.max(0,document.documentElement.scrollWidth-innerWidth),
      h2:[...document.querySelectorAll('h2')].map(n=>n.innerText).slice(0,4),
      alerts:[...document.querySelectorAll('[role=alert]')].map(n=>n.innerText).slice(0,6),
      bodyContainsSynthetic:document.body.innerText.includes('SYNTHETIC'),
      buttons:[...document.querySelectorAll('button')].filter(x=>x.getBoundingClientRect().width>0).length,
      narrowBreadcrumbItems:[...document.querySelectorAll('.el-breadcrumb__inner')]
        .filter(el=>el.getBoundingClientRect().width>0 &&
          (el.textContent||'').trim().length>=3 &&
          el.getBoundingClientRect().width<30)
        .map(el=>(el.textContent||'').trim())
    }))()""")
    if data["path"] != route.split("?")[0]:
        raise RuntimeError(f"Route redirected unexpectedly ({route} -> {data['path']})")
    if data["horizontalOverflow"] > 3:
        raise RuntimeError(f"Horizontal page overflow on {title}: {data['horizontalOverflow']} px")
    if data["narrowBreadcrumbItems"]:
        raise RuntimeError(f"Navbar breadcrumb labels squeezed on {title}: {data['narrowBreadcrumbItems']!r}")
    await client.capture(DEST / (title + ".png"))
    print(f"PASS: real Chrome {title}: {width}x{height}, route={data['path']}, "
          f"overflow={data['horizontalOverflow']}px")
    return {"name": title, "route": route, "viewport": [width, height],
            **data, "screenshot": title + ".png"}


async def main():
    if (not BASE.startswith("https://127.0.0.1:") or
            not BASE.removeprefix("https://127.0.0.1:").isdigit() or
            len(TOKEN) < 16 or not CHROME.is_file()):
        raise RuntimeError("Synthetic-only localhost Chrome preflight failed")
    if not DEST.is_absolute() or "validation-evidence" not in DEST.parts:
        raise RuntimeError("Visual evidence must stay in the private validation-evidence directory")
    if DEST.exists() and any(DEST.iterdir()):
        raise RuntimeError("Refusing to overwrite a non-empty visual-evidence directory")
    DEST.mkdir(parents=True, exist_ok=True, mode=0o700)
    os.chmod(DEST, 0o700)
    with tempfile.TemporaryDirectory(prefix="rehab-chrome-synthetic-") as tmp:
        profile = Path(tmp) / "profile"
        profile.mkdir(mode=0o700)
        chrome = subprocess.Popen([
            str(CHROME), "--headless=new", "--no-first-run",
            "--no-default-browser-check", "--disable-extensions",
            "--disable-background-networking", "--disable-sync",
            "--no-proxy-server", "--ignore-certificate-errors",
            "--remote-debugging-port=0", "--remote-allow-origins=*",
            f"--user-data-dir={profile}", "about:blank",
        ], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        try:
            portfile = profile / "DevToolsActivePort"
            for _ in range(100):
                if portfile.is_file():
                    break
                if chrome.poll() is not None:
                    raise RuntimeError("Isolated Chrome exited before DevTools became available")
                await asyncio.sleep(0.1)
            if not portfile.is_file():
                raise RuntimeError("Chrome DevTools port not available")
            port = int(portfile.read_text().splitlines()[0])
            with urlopen(f"http://127.0.0.1:{port}/json/list", timeout=5) as response:
                pages = json.load(response)
            page = next(p for p in pages if p.get("type") == "page")
            async with websockets.connect(page["webSocketDebuggerUrl"], max_size=16 * 1024 * 1024) as websocket:
                client = CDP(websocket)
                await client.call("Page.enable")
                await client.call("Runtime.enable")
                await client.call("Emulation.setDeviceMetricsOverride", {
                    "width": 1440, "height": 900, "deviceScaleFactor": 1, "mobile": False,
                })
                await client.navigate(BASE + "/login")
                await wait_for_login_form(client)
                await client.capture(DEST / "login-desktop.png")
                await client.call("Emulation.setDeviceMetricsOverride", {
                    "width": 390, "height": 844, "deviceScaleFactor": 1, "mobile": True,
                })
                await client.navigate(BASE + "/login")
                await wait_for_login_form(client)
                await client.capture(DEST / "login-mobile.png")
                print("PASS: real Chrome visible username/password login form, isolated profile")
                # Use the application's WebStorageCache format. Token exists
                # in the isolated profile only, and is never logged or persisted
                # into screenshot names/results.
                values = {"ACCESS_TOKEN": TOKEN, "tenantId": 1}
                expression = ("(() => { const values=" + json.dumps(values) +
                    "; for (const [key,value] of Object.entries(values)) "
                    "{localStorage.setItem(key, JSON.stringify({c:Date.now(),"
                    "e:4102444800000,v:JSON.stringify(value)}))} return true; })()")
                if await client.js(expression) is not True:
                    raise RuntimeError("Synthetic browser token injection failed")
                checks = []
                if SCENARIO == "--report-browser-smoke":
                    for width, height, label in [
                        (1440, 900, "desktop"),
                        (768, 1024, "tablet"),
                        (390, 844, "mobile"),
                    ]:
                        checks.append(await check_view(
                            client, "/rehab/report", "康复评估报告",
                            f"report-list-{label}", width, height, width < 600))
                        rendered = await client.js("""(() => ({
                          summary:!!document.querySelector('.report-overview'),
                          report:document.body.innerText.includes('SYNTHETIC_REPORT') ||
                            document.body.innerText.includes('SYNTHETIC_ALLOWED_1'),
                          warning:document.body.innerText.includes('V4.1')
                        }))()""")
                        if not all(rendered.values()):
                            raise RuntimeError(f"Main report page missing expected synthetic data or safety notice: {rendered}")
                        if width == 390:
                            mobile_layout = await client.js("""(() => {
                              const date=document.querySelector('.report-filter .el-date-editor');
                              const box=date?.getBoundingClientRect();
                              return {
                                pickerPresent:!!date,
                                pickerFits:!!box && box.left>=0 && box.right<=innerWidth+2,
                                pickerWidth:Math.round(box?.width||0)
                              };
                            })()""")
                            if not mobile_layout["pickerFits"] or mobile_layout["pickerWidth"] < 220:
                                raise RuntimeError(f"Mobile report date filter is clipped: {mobile_layout}")
                    # Exercise the actual report workflow; never click review,
                    # approve or export. A synthetic draft remains unsigned.
                    clicked = await client.js("""(() => {
                      const b=[...document.querySelectorAll('button')]
                        .find(x=>x.innerText?.includes('选择评估报告'));
                      if(!b) return false; b.click(); return true;
                    })()""")
                    if not clicked:
                        raise RuntimeError("Report selection button not rendered")
                    await wait_for(client, "SYNTHETIC_REPORT")
                    await asyncio.sleep(0.9)
                    mobile_dialog = await client.js("""(() => {
                      const d=[...document.querySelectorAll('.el-dialog')]
                        .find(x=>x.getBoundingClientRect().width>0);
                      const card=d?.querySelector('.report-mobile-card');
                      const table=d?.querySelector('.report-desktop-table');
                      return {
                        cardVisible:!!card && getComputedStyle(card).display!=='none',
                        tableHidden:!!table && getComputedStyle(table).display==='none',
                        dialogOverflow:d ? Math.max(0,d.scrollWidth-d.clientWidth) : -1
                      };
                    })()""")
                    if not (mobile_dialog["cardVisible"] and mobile_dialog["tableHidden"]
                            and mobile_dialog["dialogOverflow"] <= 3):
                        raise RuntimeError(f"Mobile report dialog still clips its content: {mobile_dialog}")
                    await client.capture(DEST / "report-patient-dialog-mobile.png")
                    clicked = await client.js("""(() => {
                      const b=[...document.querySelectorAll('.el-dialog .report-mobile-card button')]
                        .find(x=>x.innerText?.trim()==='预览' &&
                          x.getBoundingClientRect().width>0 &&
                          x.getBoundingClientRect().height>0);
                      if(!b) return false; b.click(); return true;
                    })()""")
                    if not clicked:
                        raise RuntimeError("Authorized report preview entrypoint missing")
                    await wait_for(client, "SYNTHETIC_PREVIEW_OK")
                    await asyncio.sleep(0.75)
                    safe = await client.js("""(() => {
                      const el=[...document.querySelectorAll('.preview-wrap')]
                        .find(n=>n.textContent.includes('SYNTHETIC_PREVIEW_OK'));
                      if(!el) return {preview:false};
                      return {
                        preview:true,
                        scripts:el.querySelectorAll('script,iframe,object,embed,form,style').length,
                        eventAttributes:[...el.querySelectorAll('*')]
                          .flatMap(n=>[...n.attributes].filter(a=>a.name.startsWith('on')).map(a=>a.name)),
                        unsafeHrefs:[...el.querySelectorAll('[href]')]
                          .filter(n=>/^javascript:/i.test(n.getAttribute('href')||'')).length,
                        executed:window.__rehab_html_injected!==undefined
                      };
                    })()""")
                    if not (safe.get("preview") and safe.get("scripts") == 0
                            and not safe.get("eventAttributes") and safe.get("unsafeHrefs") == 0
                            and safe.get("executed") is False):
                        raise RuntimeError(f"Report preview HTML sanitizer failed: {safe}")
                    await client.capture(DEST / "report-sanitized-preview-mobile.png")
                    print("PASS: real Chrome synthetic report selection and unsafe HTML sanitized before DOM insertion")
                else:
                    for width, height, label in [
                        (1440, 900, "desktop"),
                        (768, 1024, "tablet"),
                        (390, 844, "mobile"),
                    ]:
                        checks.append(await check_view(
                            client, "/rehab/motion", "评估记录",
                            f"motion-list-{label}", width, height, width < 600))
                        checks.append(await check_view(
                            client, "/rehab/motion/detail/990101",
                            "动作评估 #990101", f"motion-detail-{label}",
                            width, height, width < 600))
                    clicked = await client.js("""(() => {
                      const btn=[...document.querySelectorAll('button')]
                        .find(el=>el.innerText?.includes('查看审核'));
                      if (!btn) return false; btn.click(); return true;
                    })()""")
                    if not clicked:
                        raise RuntimeError("Mobile review entrypoint missing")
                    await asyncio.sleep(1)
                    active = await client.js("""(() => [...document.querySelectorAll('.el-tabs__item.is-active')]
                      .some(el=>el.innerText?.includes('审核与签署')))()""")
                    if not active:
                        raise RuntimeError("Review tab did not activate after Chrome click")
                    await client.capture(DEST / "motion-review-mobile.png")
                    print("PASS: real Chrome mobile review entrypoint interactive")
                result = {"chromeVersion": "154", "origin": "synthetic-localhost-only",
                          "realBrowser": True, "humanVisualSignoff": False,
                          "views": checks}
                (DEST / "visual-results.json").write_text(
                    json.dumps(result, ensure_ascii=False, indent=2) + "\n")
                os.chmod(DEST / "visual-results.json", 0o600)
                print(f"PASS: real-browser DOM and screenshots: {len(checks)} secured views, human review still pending")
        finally:
            chrome.terminate()
            try:
                chrome.wait(timeout=6)
            except subprocess.TimeoutExpired:
                chrome.kill()
                chrome.wait(timeout=6)


if __name__ == "__main__":
    asyncio.run(main())
