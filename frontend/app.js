const $ = (selector) => document.querySelector(selector);
const API = 'http://127.0.0.1:8080/api/v1';
let token = null, current = null, selected = null, registerMode = false, page = 1, epoch = 0;

function notice(message, error = false) { $('#notice').textContent = message; $('#notice').className = error ? 'error' : ''; }
function signedOut() {
  epoch++; token = null; current = null; selected = null;
  $('#workspace').hidden = true; $('#logout').hidden = true; $('#auth').hidden = false;
  $('#session-list').replaceChildren(); $('#article-list').replaceChildren(); $('#editor').hidden = true;
}
async function api(path, method = 'GET', body) {
  const started = epoch;
  let response;
  try {
    response = await fetch(API + path, {
      method, credentials: 'omit',
      headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) },
      ...(body === undefined ? {} : { body: JSON.stringify(body) }),
    });
  } catch { throw new Error('暂时无法连接服务，请检查后端是否启动。'); }
  if (started !== epoch) throw new Error('登录状态已变化，请重新操作。');
  const data = response.status === 204 ? null : await response.json();
  if (!response.ok) {
    if (response.status === 401 && token) signedOut();
    throw new Error(data?.message || `请求失败（${response.status}）`);
  }
  return data;
}
async function action(button, operation) {
  button.disabled = true;
  try { await operation(); } catch (error) { notice(error.message, true); }
  finally { button.disabled = false; }
}
function field(form, name) { return form.elements.namedItem(name); }
$('#switch-auth').onclick = () => {
  registerMode = !registerMode;
  $('#auth-title').textContent = registerMode ? '注册客户账号' : '登录账号';
  $('#auth-hint').textContent = registerMode ? '密码至少12个字符，用户名支持字母、数字和下划线。' : '登录后继续管理你的咨询。';
  $('#auth-submit').textContent = registerMode ? '注册并登录' : '登录';
  $('#switch-auth').textContent = registerMode ? '已有账号？返回登录' : '没有账号？注册客户账号';
  $('#display-field').hidden = !registerMode;
  field($('#auth-form'), 'displayName').required = registerMode;
  field($('#auth-form'), 'password').autocomplete = registerMode ? 'new-password' : 'current-password';
  notice('');
};
$('#auth-form').onsubmit = (event) => {
  event.preventDefault();
  action($('#auth-submit'), async () => {
    const form = event.target;
    const credentials = { username: field(form, 'username').value, password: field(form, 'password').value };
    try {
      if (registerMode) await api('/auth/register', 'POST', { ...credentials, displayName: field(form, 'displayName').value });
      const login = await api('/auth/login', 'POST', credentials);
      token = login.accessToken; epoch++;
      // 令牌仅保留在内存，既不写localStorage，也不存放到URL或日志。
      current = await api('/users/me');
      $('#greeting').textContent = `${current.displayName}，你好`;
      $('#role-label').textContent = current.role === 'MERCHANT' ? '商家工作区' : '客户工作区';
      $('#list-title').textContent = current.role === 'MERCHANT' ? '咨询记录' : '我的咨询';
      $('#merchant').hidden = current.role !== 'MERCHANT';
      $('#workspace').hidden = false; $('#logout').hidden = false; $('#auth').hidden = true;
      notice('登录成功'); await loadSessions();
    } finally { field(form, 'password').value = ''; }
  });
};
$('#logout').onclick = () => { signedOut(); notice('已退出当前页面登录。'); };
async function loadSessions(more = false) {
  const next = more ? page + 1 : 1;
  const result = await api(`/sessions?page=${next}&size=10`);
  if (!more) $('#session-list').replaceChildren();
  page = next;
  if (!result.items.length && !more) { const li = document.createElement('li'); li.textContent = '还没有咨询，先记录一个问题吧。'; $('#session-list').append(li); }
  for (const row of result.items) {
    const li = document.createElement('li'), button = document.createElement('button'), meta = document.createElement('small');
    button.textContent = row.title;
    button.onclick = () => action(button, async () => openSession(await api(`/sessions/${row.id}`)));
    meta.textContent = `${row.status === 'OPEN' ? '进行中' : '已结束'} · ${new Date(row.createdAt + 'Z').toLocaleString('zh-CN')}`;
    li.append(button, meta); $('#session-list').append(li);
  }
  $('#more').hidden = !result.hasMore;
}
$('#refresh').onclick = () => action($('#refresh'), () => loadSessions());
$('#more').onclick = () => action($('#more'), () => loadSessions(true));
$('#create-form').onsubmit = (event) => {
  event.preventDefault(); const form = event.target;
  action(form.querySelector('button'), async () => {
    const row = await api('/sessions', 'POST', { title: field(form, 'title').value, notes: field(form, 'notes').value });
    form.reset(); await loadSessions(); openSession(row); notice('咨询已创建');
  });
};
function openSession(row) {
  selected = row; $('#editor').hidden = false;
  field($('#edit-form'), 'title').value = row.title; field($('#edit-form'), 'notes').value = row.notes;
  $('#session-meta').textContent = row.status === 'OPEN' ? '进行中' : '已结束';
  $('#toggle-status').textContent = row.status === 'OPEN' ? '结束咨询' : '重新开启';
}
$('#close-editor').onclick = () => { selected = null; $('#editor').hidden = true; };
$('#edit-form').onsubmit = (event) => {
  event.preventDefault(); const form = event.target;
  action(form.querySelector('[type=submit]'), async () => {
    const row = await api(`/sessions/${selected.id}`, 'PUT', { title: field(form, 'title').value, notes: field(form, 'notes').value, version: selected.version });
    openSession(row); await loadSessions(); notice('修改已保存');
  });
};
$('#toggle-status').onclick = () => action($('#toggle-status'), async () => {
  const row = await api(`/sessions/${selected.id}/status`, 'PATCH', { status: selected.status === 'OPEN' ? 'CLOSED' : 'OPEN', version: selected.version });
  openSession(row); await loadSessions(); notice('咨询状态已更新');
});
$('#delete-session').onclick = () => {
  if (!window.confirm('确定删除这条咨询？删除后无法恢复。')) return;
  action($('#delete-session'), async () => {
    await api(`/sessions/${selected.id}?version=${selected.version}`, 'DELETE');
    selected = null; $('#editor').hidden = true; await loadSessions(); notice('咨询已删除');
  });
};
$('#load-articles').onclick = () => action($('#load-articles'), async () => {
  const result = await api('/articles?size=20'); $('#article-list').replaceChildren();
  for (const article of result.items) { const li = document.createElement('li'); li.textContent = `${article.title} · ${article.status === 'PUBLISHED' ? '已发布' : '草稿'}`; $('#article-list').append(li); }
  if (!result.items.length) { const li = document.createElement('li'); li.textContent = '暂无知识文章'; $('#article-list').append(li); }
});
