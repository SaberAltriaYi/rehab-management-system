// Read-only navigation smoke test against a manually authenticated, isolated Chrome session.
// No credentials, business payloads, screenshots or storage state are exported.
const fs = require('node:fs');
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
(async () => {
  const browser = await chromium.connectOverCDP(process.env.REHAB_CDP || 'http://127.0.0.1:9223');
  try {
    const page = browser.contexts().flatMap(c => c.pages()).find(p => p.url().startsWith('https://127.0.0.1:8443/'));
    if (!page || new URL(page.url()).pathname === '/login') throw new Error('Manual test-account login required');
    const targets = await page.locator('.el-menu-item').evaluateAll(items => items.map(item => {
      const parentTitles = [];
      for (let parent = item.parentElement; parent; parent = parent.parentElement) {
        if (parent.classList.contains('el-sub-menu')) {
          const header = parent.querySelector(':scope > .el-sub-menu__title');
          if (header) parentTitles.unshift(header.textContent.trim());
        }
      }
      return { menu: item.textContent.trim(), parentTitles };
    }));
    const findDirectSubMenu = async (scope, title) => {
      const candidates = scope.locator(':scope > .el-sub-menu');
      const count = await candidates.count();
      for (let index = 0; index < count; index++) {
        const candidate = candidates.nth(index);
        const header = candidate.locator(':scope > .el-sub-menu__title');
        if ((await header.textContent()).trim() === title) return candidate;
      }
      throw new Error(`Menu branch not found: ${title}`);
    };
    const findDirectItem = async (scope, title) => {
      const candidates = scope.locator(':scope > .el-menu-item');
      const count = await candidates.count();
      for (let index = 0; index < count; index++) {
        const candidate = candidates.nth(index);
        if ((await candidate.textContent()).trim() === title) return candidate;
      }
      throw new Error(`Menu item not found: ${title}`);
    };
    const results = [];
    let current;
    let pending = [];
    page.on('pageerror', e => { if (current) current.pageErrors.push(e.name); });
    page.on('response', response => {
      if (!current) return;
      const row = current;
      const url = new URL(response.url());
      if (url.origin !== new URL(page.url()).origin) return;
      if (response.status() >= 400) row.httpErrors.push({ path: url.pathname, status: response.status() });
      if (url.pathname.startsWith('/admin-api/') && (response.headers()['content-type'] || '').includes('json')) {
        pending.push(response.json().then(body => {
          if (typeof body.code === 'number' && body.code !== 0) row.apiErrors.push({ path: url.pathname, code: body.code });
          row.apiResponses++;
        }).catch(() => {}));
      }
    });
    page.on('dialog', d => d.dismiss());
    for (const target of targets) {
      current = { menu: target.menu, route: '', pageErrors: [], httpErrors: [], apiErrors: [], apiResponses: 0, status: 'not_run' };
      pending = [];
      try {
        // Resolve the target from its complete menu path on every iteration.
        // This remains stable even when Element Plus mounts/collapses branches
        // and when different modules reuse a label such as “审计日志”.
        let scope = page.locator('ul.el-menu[role="menubar"]').first();
        for (const title of target.parentTitles) {
          const parent = await findDirectSubMenu(scope, title);
          const header = parent.locator(':scope > .el-sub-menu__title');
          if (!await header.evaluate(el => el.parentElement.classList.contains('is-opened'))) {
            await header.click({timeout:5000});
            // Nested Element Plus menus are mounted after the parent expansion.
            // Without this tiny settle interval, a deep item can retain a zero
            // sized box and make a healthy route look unreachable.
            await page.waitForTimeout(350);
          }
          scope = parent.locator(':scope > ul.el-menu').first();
        }
        const item = await findDirectItem(scope, target.menu);
        const previousUrl = page.url();
        const alreadyCurrent = await item.evaluate(el => el.classList.contains('is-active'));
        await item.click({ timeout: 8000 });
        if (!alreadyCurrent) {
          try {
            await page.waitForFunction(url => location.href !== url, previousUrl, { timeout: 3500 });
          } catch (error) {
            if (!await item.evaluate(el => el.classList.contains('is-active'))) throw error;
          }
        }
        // Some pages intentionally keep polling or maintain WebSockets, so
        // networkidle would turn a working navigation into a false timeout.
        // Allow the initial API requests to settle, then inspect every JSON
        // response observed during that short window.
        await page.waitForTimeout(1200);
        await Promise.allSettled(pending);
        current.route = new URL(page.url()).pathname;
        current.status = current.route === '/login' ? 'auth_lost' :
          current.pageErrors.length || current.httpErrors.length || current.apiErrors.length ? 'failed' : 'navigation_only_pass';
      } catch (e) {
        current.status = 'blocked';
        current.errorType = e.name;
        current.route = new URL(page.url()).pathname;
      }
      results.push(current);
      console.log(JSON.stringify(current));
      if (current.status === 'auth_lost') break;
    }
    const report = { timestamp: new Date().toISOString(), browser: browser.version(), scope: 'Read-only menu navigation only. Not CRUD, role/permission, mobile, or modified-code acceptance.', results };
    fs.writeFileSync(process.env.REHAB_E2E_REPORT || '/tmp/rehab-browser-check/route-smoke.json', JSON.stringify(report, null, 2));
    if (results.some(r => r.status !== 'navigation_only_pass')) process.exitCode = 2;
  } finally { await browser.close(); }
})().catch(e => { console.error(e.message); process.exitCode = 1; });
