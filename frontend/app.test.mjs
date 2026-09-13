import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { setTimeout } from 'node:timers/promises';
import { JSDOM } from 'jsdom';

const enabled = process.env.RUN_FRONTEND_INTEGRATION === 'true';
test('frontend DOM drives registration, login, owned CRUD and logout against real Java', { skip: !enabled, timeout: 40000 }, async () => {
  const html = await readFile(new URL('./index.html', import.meta.url), 'utf8');
  const script = await readFile(new URL('./app.js', import.meta.url), 'utf8');
  const dom = new JSDOM(html, { url: 'http://127.0.0.1:5173', runScripts: 'outside-only' });
  const { window } = dom;
  const $ = selector => window.document.querySelector(selector);
  const calls = [];
  window.fetch = async (url, options) => {
    assert.ok(url.startsWith('http://127.0.0.1:8080/api/v1/'));
    const response = await fetch(url, { ...options, headers: { ...options.headers, Origin: 'http://127.0.0.1:5173' } });
    assert.equal(response.headers.get('access-control-allow-origin'), 'http://127.0.0.1:5173');
    calls.push({ path: new URL(url).pathname, method: options.method, status: response.status, authenticated: Boolean(options.headers.Authorization) });
    return response;
  };
  window.confirm = () => true;
  async function until(predicate) {
    const deadline = Date.now() + 10000;
    while (!predicate()) {
      if (Date.now() > deadline) throw new Error('DOM action timed out: ' + $('#notice').textContent);
      await setTimeout(30);
    }
  }
  function submit(form) { $(form).dispatchEvent(new window.Event('submit', { bubbles: true, cancelable: true })); }
  const username = 'dom_' + crypto.randomUUID().replaceAll('-', '').slice(0, 24);
  const password = 'synthetic-' + crypto.randomUUID();
  try {
    window.eval(script);
    $('#switch-auth').click();
    $('#auth-form [name=username]').value = username;
    $('#auth-form [name=password]').value = password;
    $('#auth-form [name=displayName]').value = 'SYNTHETIC DOM';
    submit('#auth-form');
    await until(() => !$('#workspace').hidden && !$('#auth-submit').disabled);
    assert.equal($('#role-label').textContent, '客户工作区');
    assert.equal($('#merchant').hidden, true);
    assert.equal($('#auth-form [name=password]').value, '');
    assert.equal(window.localStorage.length, 0);
    const hostileTitle = '<img src=x onerror=alert(1)> SYNTHETIC DOM';
    $('#create-form [name=title]').value = hostileTitle;
    $('#create-form [name=notes]').value = 'Real API fixture';
    submit('#create-form');
    await until(() => $('#notice').textContent === '咨询已创建');
    assert.ok($('#session-list').textContent.includes(hostileTitle));
    assert.equal($('#session-list img'), null, 'User text must never become executable HTML');
    $('#edit-form [name=notes]').value = 'Edited through DOM';
    submit('#edit-form');
    await until(() => $('#notice').textContent === '修改已保存');
    $('#toggle-status').click();
    await until(() => $('#notice').textContent === '咨询状态已更新');
    assert.equal($('#session-meta').textContent, '已结束');
    $('#delete-session').click();
    await until(() => $('#notice').textContent === '咨询已删除');
    assert.equal($('#editor').hidden, true);
    $('#logout').click();
    assert.equal($('#workspace').hidden, true);
    assert.equal($('#auth').hidden, false);
    $('#switch-auth').click();
    $('#auth-form [name=password]').value = password;
    submit('#auth-form');
    await until(() => !$('#workspace').hidden && !$('#auth-submit').disabled);
    // Deterministic UI test for a server-side expired-token response. Real JWT expiry is tested in Java.
    window.fetch = async () => new Response(JSON.stringify({ message: '登录凭证已过期' }), { status: 401, headers: { 'Content-Type': 'application/json' } });
    $('#refresh').click();
    await until(() => $('#auth').hidden === false && !$('#refresh').disabled);
    assert.equal($('#workspace').hidden, true);
    assert.ok($('#notice').textContent.includes('已过期'));
    assert.ok(calls.some(call => call.path.endsWith('/auth/register') && call.status === 201 && !call.authenticated));
    assert.ok(calls.some(call => call.method === 'DELETE' && call.status === 204 && call.authenticated));
    assert.ok(calls.every(call => call.status < 400));
  } finally { window.close(); }
});
