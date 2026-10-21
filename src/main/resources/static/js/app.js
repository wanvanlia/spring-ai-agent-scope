/**
 * 反诈智能 Agent 工作台 - 视图控制
 */
(function () {
    'use strict';

    var $ = function (sel) { return document.querySelector(sel); };

    /* ==================== 通用工具 ==================== */

    function esc(v) {
        return String(v == null ? '' : v)
            .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;').replace(/'/g, '&#39;');
    }

    function toast(message, type) {
        var box = $('#toastBox');
        var el = document.createElement('div');
        el.className = 'toast ' + (type || 'info');
        el.textContent = message;
        box.appendChild(el);
        setTimeout(function () {
            el.style.opacity = '0';
            el.style.transition = 'opacity .4s';
            setTimeout(function () { el.remove(); }, 400);
        }, 3200);
    }

    var loadingCount = 0;
    function loading(on, text) {
        if (on) {
            loadingCount++;
            $('#loadingText').textContent = text || '处理中…';
            $('#loading').classList.remove('hidden');
        } else if (--loadingCount <= 0) {
            loadingCount = 0;
            $('#loading').classList.add('hidden');
        }
    }

    function riskMeta(level) {
        var l = (level || '').toUpperCase();
        var map = {
            HIGH: { cls: 'risk-high', label: '高风险' },
            MEDIUM: { cls: 'risk-medium', label: '中风险' },
            LOW: { cls: 'risk-low', label: '低风险' }
        };
        return map[l] || { cls: 'risk-unknown', label: (level || '未知') };
    }

    var HANDLE_META = {
        SHUTDOWN: { cls: 'shutdown', label: '已关停' },
        ACTIVE: { cls: 'success', label: '正常' },
        NORMAL: { cls: 'success', label: '正常' },
        CONTINUE: { cls: 'observe', label: '继续观察' },
        APPROVED: { cls: 'approved', label: '已通过' },
        REJECTED: { cls: 'rejected', label: '已驳回' },
        CONTINUE_OBSERVE: { cls: 'observe', label: '继续观察' },
        PENDING: { cls: 'pending', label: '待审核' },
        SUBMITTED: { cls: 'submitted', label: '已提交' },
        SUCCESS: { cls: 'success', label: '成功' },
        FAILED: { cls: 'failed', label: '失败' },
        FAIL: { cls: 'failed', label: '失败' },
        SKIP: { cls: 'plain', label: '跳过' },
        CANCELLED: { cls: 'cancelled', label: '已取消' },
        CONFIRMED: { cls: 'confirmed', label: '已人工确认' },
        AUTO: { cls: 'auto', label: '自动' },
        HUMAN_REVIEW: { cls: 'pending', label: '转人工审核' },
        UNKNOWN: { cls: 'plain', label: '未知' }
    };
    function stateTag(v) {
        var m = HANDLE_META[(v || '').toUpperCase()];
        return m
            ? '<span class="state-tag ' + m.cls + '">' + esc(m.label) + '</span>'
            : '<span class="state-tag plain">' + esc(v || '—') + '</span>';
    }

    function renderJson(parse) {
        if (!parse) { return null; }
        if (typeof parse === 'object') { return parse; }
        try { return JSON.parse(parse); } catch (e) { return null; }
    }

    function kvHtml(rows) {
        if (!rows || !rows.length) { return ''; }
        return '<div class="grid2">' + rows.map(function (r) {
            return '<div class="kv"><b>' + esc(r[0]) + '</b>' + esc(r[1]) + '</div>';
        }).join('') + '</div>';
    }

    function jsonPre(title, obj) {
        if (obj == null) { return ''; }
        var txt = (typeof obj === 'string') ? obj : JSON.stringify(obj, null, 2);
        return '<div class="pre-box">' + esc(txt) + '</div>';
    }

    /* ==================== 鉴权 ==================== */

    function showLogin() {
        $('#loginView').classList.remove('hidden');
        $('#appView').classList.add('hidden');
    }
    function showApp() {
        var user = Api.getUser();
        if (!user) { showLogin(); return; }
        $('#loginView').classList.add('hidden');
        $('#appView').classList.remove('hidden');
        $('#userName').textContent = user.username || '';
        $('#userRole').textContent = roleLabel(user.role);
        buildNav(user.role);
    }

    function roleLabel(role) {
        var map = { ADMIN: '管理员', AUDITOR: '审核员', USER: '普通用户' };
        return map[role] || role || '—';
    }

    /** 按角色过滤导航项并跳到第一个可见页签 */
    function buildNav(role) {
        var items = Array.prototype.slice.call(document.querySelectorAll('.nav-item'));
        items.forEach(function (it) {
            var need = (it.getAttribute('data-role') || '').split(',').filter(Boolean);
            it.classList.toggle('hidden', need.length > 0 && need.indexOf(role) < 0);
        });
        var first = items.filter(function (it) { return !it.classList.contains('hidden'); })[0];
        switchTab(first ? first.getAttribute('data-tab') : 'chat');
    }

    function switchTab(tab) {
        document.querySelectorAll('.nav-item').forEach(function (it) {
            it.classList.toggle('active', it.getAttribute('data-tab') === tab);
        });
        document.querySelectorAll('.pane').forEach(function (p) {
            p.classList.toggle('hidden', p.id !== 'pane-' + tab);
        });
        // 进入数据查询页且尚未渲染时, 自动加载一次
        if (tab === 'data' && !document.getElementById('dq_phone')
            && !document.getElementById('dq_caseNo') && !document.getElementById('dq_orderNo')) {
            renderDqFilters();
            loadDq(1);
        }
        // 每次进入用户管理都刷新列表, 保证数据最新
        if (tab === 'users') {
            loadUsers(1);
        }
    }

    function handleApiError(e) {
        if (e instanceof Api.ApiError && e.status === 401) {
            toast(e.message || '请重新登录', 'error');
            Api.logout();
            showLogin();
        } else {
            toast((e && e.message) || '操作失败', 'error');
        }
    }

    /* ==================== 登录 ==================== */

    async function doLogin(username, password) {
        loading(true, '登录中…');
        try {
            var data = await Api.login(username, password);
            Api.remember(data.token, username, data.role);
            toast('登录成功，欢迎 ' + username, 'success');
            showApp();
        } catch (e) {
            toast((e && e.message) || '登录失败', 'error');
        } finally {
            loading(false);
        }
    }

    /* ==================== 对话研判 ==================== */

    function appendChat(msg, role, meta) {
        var box = $('#chatMsg');
        var div = document.createElement('div');
        div.className = 'msg ' + (role === 'user' ? 'msg-user' : 'msg-ai');
        div.innerHTML = esc(msg) + (meta ? '<span class="meta">' + esc(meta) + '</span>' : '');
        box.appendChild(div);
        box.scrollTop = box.scrollHeight;
    }

    async function sendChat(text) {
        var msg = (text || $('#chatInput').value || '').trim();
        if (!msg) { return; }
        $('#chatInput').value = '';
        appendChat(msg, 'user');
        loading(true, 'Agent 研判中…');
        try {
            var d = await Api.chat(msg);
            var metaBits = [];
            if (d.sessionId) { metaBits.push('session ' + d.sessionId); }
            if (d.conversationId) { metaBits.push('conv ' + d.conversationId); }
            appendChat(d.reply || '（无回复）', 'ai', metaBits.join(' ｜ '));
            if (d.needFollowUp) {
                toast('需要补充信息 / 转人工，请按引导继续输入', 'info');
            }
        } catch (e) {
            appendChat('调用失败：' + ((e && e.message) || '未知错误'), 'ai');
        } finally {
            loading(false);
        }
    }

    /* ==================== 号码研判 ==================== */

    function analyzeVoHtml(vo) {
        var risk = riskMeta(vo.riskLevel);
        var evRows = [];
        var ev = vo.evidence;
        if (ev && typeof ev === 'object') {
            Object.keys(ev).forEach(function (k) { evRows.push([k, ev[k]]); });
        }
        var keywords = vo.hitKeywords || [];
        var html = '<div class="result-card">'
            + '<div class="result-head ' + risk.cls + '">'
            + '<span class="badge-risk ' + risk.cls + '">' + esc(risk.label) + '</span>'
            + '<div><div class="result-title">' + esc(vo.phoneMasked || '号码') + ' 风险研判结果</div>'
            + '<div class="result-sub">caseNo: ' + esc(vo.caseNo || '—')
            + (vo.confidence != null ? ' ｜ 置信度 ' + esc(vo.confidence) : '') + '</div></div>'
            + '<div style="margin-left:auto">' + stateTag(vo.handleResult) + '</div>'
            + '</div>'
            + '<div class="result-body">'
            + kvHtml([
                ['诈骗类型', vo.fraudType || '—'],
                ['研判理由', vo.reason || '—'],
                ['处置结果', vo.handleResult || '—'],
                ['处置建议', vo.suggestion || '—'],
                ['关停工单', vo.orderNo || '—'],
                ['是否转人工', vo.needHumanReview ? '是' : '否'],
                ['状态机状态', vo.currentState || '—'],
                ['会话', vo.sessionId || '—']
            ]);
        if (keywords.length) {
            html += '<div class="tag-list"><span class="tags-label">命中关键词：</span>'
                + keywords.map(function (k) { return '<span class="mini-tag">' + esc(k) + '</span>'; }).join('')
                + '</div>';
        }
        if (evRows.length) {
            html += '<div class="tags-label" style="margin-top:14px">证据摘要</div>' + kvHtml(evRows);
        }
        html += '</div></div>';
        return html;
    }

    async function doAnalyze(phone) {
        $('#anResult').innerHTML = '';
        loading(true, '正在执行研判（分析 → 处置）…');
        try {
            var vo = await Api.analyze(phone);
            $('#anResult').innerHTML = analyzeVoHtml(vo);
            toast('研判完成' + (vo.needHumanReview ? '，已转人工审核' : ''), vo.needHumanReview ? 'info' : 'success');
        } catch (e) {
            handleApiError(e);
            $('#anResult').innerHTML = '';
        } finally {
            loading(false);
        }
    }

    /* ==================== 案件审核 ==================== */

    function riskCaseHtml(c) {
        if (!c) {
            return '<div class="result-card"><div class="result-head"><span class="badge-risk risk-unknown">未知</span>'
                + '<div class="result-sub">未查询到案例信息</div></div></div>';
        }
        var risk = riskMeta(c.riskLevel);
        var evidence = renderJson(c.evidenceJson);
        var report = renderJson(c.analysisReport);
        var rows = [
            ['案件编号', c.caseNo || '—'],
            ['号码', (c.phoneMasked || c.phone) || '—'],
            ['风险等级', '<span class="state-tag ' + risk.cls + '">' + esc(risk.label) + '</span>'],
            ['置信度', c.confidence != null ? c.confidence : '—'],
            ['相似度', c.similarityScore != null ? c.similarityScore : '—'],
            ['诈骗类型', c.fraudType || '—'],
            ['研判理由', c.reason || '—'],
            ['处置状态', stateTag(c.handleResult)],
            ['审核状态', stateTag(c.auditStatus)],
            ['创建时间', c.createTime || '—']
        ];
        return '<div class="result-card"><div class="result-head ' + risk.cls + '">'
            + '<span class="badge-risk ' + risk.cls + '">' + esc(risk.label) + '</span>'
            + '<div><div class="result-title">' + esc(c.caseNo || '案件') + '</div>'
            + '<div class="result-sub">' + esc((c.phoneMasked || c.phone) || '') + '</div></div></div>'
            + '<div class="result-body"><table class="detail-table">'
            + rows.map(function (r) { return '<tr><td>' + r[0] + '</td><td>' + r[1] + '</td></tr>'; }).join('')
            + '</table>'
            + jsonPre('证据链', evidence)
            + jsonPre('评估报告', report)
            + '</div></div>';
    }

    function orderHtml(o) {
        var rows = [
            ['工单号', o.orderNo || '—'],
            ['号码', o.phoneMasked || '—'],
            ['状态', stateTag(o.status)],
            ['结果', stateTag(o.notifyStatus)],
            ['是否自动', o.isAuto == null ? '—' : (o.isAuto === 1 ? '自动' : '人工')],
            ['原因', o.reason || '—']
        ];
        return '<div class="result-card"><div class="result-head"><div class="result-title">关停执行结果</div>'
            + '<div class="result-sub">operatorId: ' + esc(o.operatorId || '—') + '</div></div>'
            + '<div class="result-body"><table class="detail-table">'
            + rows.map(function (r) { return '<tr><td>' + r[0] + '</td><td>' + r[1] + '</td></tr>'; }).join('')
            + '</table>' + jsonPre('运营商响应', o.carrierApiResp)
            + '</div></div>';
    }

    function showAuditActions(caseNo) {
        $('#auActions').outerHTML = '<div id="auActions" class="result-actions">'
            + '<button id="btnApprove" class="btn btn-success">确认关停（approve）</button>'
            + '<button id="btnReject" class="btn btn-warn">驳回（reject）</button>'
            + '<input id="rejectReason" class="reason-input" placeholder="驳回原因（可选）">'
            + '</div>';
        $('#btnApprove').addEventListener('click', function () { doApprove(caseNo); });
        $('#btnReject').addEventListener('click', function () {
            doReject(caseNo, $('#rejectReason').value.trim());
        });
    }

    async function doSearchCase() {
        var caseNo = $('#auCaseNo').value.trim();
        if (!caseNo) { toast('请输入案件编号', 'info'); return; }
        $('#auDetail').innerHTML = '';
        loading(true, '查询案例中…');
        try {
            var c = await Api.getCase(caseNo);
            $('#auDetail').innerHTML = riskCaseHtml(c);
            $('#auDetail').insertAdjacentHTML('beforeend',
                '<div id="auActions"></div><div id="auOrder"></div>');
            showAuditActions(caseNo);
        } catch (e) {
            handleApiError(e);
        } finally {
            loading(false);
        }
    }

    async function doApprove(caseNo) {
        loading(true, '执行人工确认关停…');
        try {
            var order = await Api.approve(caseNo);
            $('#auOrder').innerHTML = orderHtml(order);
            toast('已确认关停，工单 ' + (order.orderNo || '') + ' 已生成', 'success');
        } catch (e) {
            handleApiError(e);
        } finally {
            loading(false);
        }
    }

    async function doReject(caseNo, reason) {
        if (!window.confirm('确认驳回该案例的关停处置？' + (reason ? '\n原因：' + reason : ''))) { return; }
        loading(true, '执行驳回…');
        try {
            await Api.reject(caseNo, reason);
            toast('已驳回该案例', 'success');
            doSearchCase();
        } catch (e) {
            handleApiError(e);
        } finally {
            loading(false);
        }
    }

    /* ==================== 通用弹窗 ==================== */

    function openModal(title, bodyHtml, buttons) {
        $('#modalTitle').textContent = title;
        $('#modalBody').innerHTML = bodyHtml;
        $('#modalFoot').innerHTML = (buttons || []).map(function (b, i) {
            return '<button class="btn ' + (b.cls || 'btn-ghost-dark') + '" data-mb="' + i + '">'
                + esc(b.text) + '</button>';
        }).join('');
        Array.prototype.forEach.call($('#modalFoot').querySelectorAll('[data-mb]'), function (el) {
            el.addEventListener('click', function () {
                var b = (buttons || [])[Number(el.getAttribute('data-mb'))];
                if (b && typeof b.onClick === 'function') { b.onClick(); }
            });
        });
        $('#modal').classList.remove('hidden');
    }

    function closeModal() {
        $('#modal').classList.add('hidden');
    }

    function val(id) {
        var el = document.getElementById(id);
        return el ? String(el.value || '').trim() : '';
    }

    /* ==================== 用户管理 ==================== */

    var ROLE_META = { USER: '普通用户', AUDITOR: '审核员', ADMIN: '管理员' };
    var uqState = { page: 1, size: 10 };

    function roleLabel(r) {
        return ROLE_META[r] || r || '—';
    }

    function statusTag(s) {
        return (s == null || s === '') ? '—'
            : (Number(s) === 1
                ? '<span class="state-tag success">启用</span>'
                : '<span class="state-tag rejected">禁用</span>');
    }

    function collectUserQuery() {
        var p = { page: uqState.page, size: uqState.size };
        var username = val('uq_username');
        var role = val('uq_role');
        var status = val('uq_status');
        if (username) { p.username = username; }
        if (role) { p.role = role; }
        if (status !== '') { p.status = Number(status); }
        return p;
    }

    function renderUserTable(pageData) {
        var records = (pageData && pageData.records) || [];
        if (!records.length) {
            $('#uqResult').innerHTML = '<div class="empty">暂无用户数据</div>';
            return;
        }
        var rows = records.map(function (u) {
            return '<tr>'
                + '<td>' + esc(u.id) + '</td>'
                + '<td>' + esc(u.username) + '</td>'
                + '<td>' + esc(u.realName) + '</td>'
                + '<td>' + esc(roleLabel(u.role)) + '</td>'
                + '<td>' + esc(u.phone) + '</td>'
                + '<td>' + statusTag(u.status) + '</td>'
                + '<td>' + esc(u.createTime) + '</td>'
                + '<td><div class="row-actions">'
                + '<button class="btn-mini" data-edit="' + esc(u.id) + '">编辑</button>'
                + '<button class="btn-mini danger" data-pwd="' + esc(u.id) + '">重置密码</button>'
                + '</div></td>'
                + '</tr>';
        }).join('');

        var total = pageData.total || 0;
        var pages = pageData.pages || 1;
        var current = pageData.current || uqState.page;

        $('#uqResult').innerHTML =
            '<div class="table-wrap"><table class="data-table"><thead><tr>'
            + ['ID', '用户名', '姓名', '角色', '手机号', '状态', '创建时间', '操作']
                .map(function (h) { return '<th>' + h + '</th>'; }).join('')
            + '</tr></thead><tbody>' + rows + '</tbody></table></div>'
            + '<div class="pager">'
            + '<span class="pager-info">共 ' + total + ' 条 / ' + pages + ' 页，当前第 ' + current + ' 页</span>'
            + '<button class="btn btn-ghost-dark" id="uqPrev"' + (current <= 1 ? ' disabled' : '') + '>上一页</button>'
            + '<button class="btn btn-ghost-dark" id="uqNext"' + (current >= pages ? ' disabled' : '') + '>下一页</button>'
            + '</div>';

        var prev = document.getElementById('uqPrev');
        var next = document.getElementById('uqNext');
        if (prev) { prev.addEventListener('click', function () { loadUsers(current - 1); }); }
        if (next) { next.addEventListener('click', function () { loadUsers(current + 1); }); }

        Array.prototype.forEach.call($('#uqResult').querySelectorAll('[data-edit]'), function (el) {
            el.addEventListener('click', function () {
                var id = Number(el.getAttribute('data-edit'));
                var u = records.filter(function (x) { return x.id === id; })[0];
                openUserForm(u);
            });
        });
        Array.prototype.forEach.call($('#uqResult').querySelectorAll('[data-pwd]'), function (el) {
            el.addEventListener('click', function () {
                var id = Number(el.getAttribute('data-pwd'));
                var u = records.filter(function (x) { return x.id === id; })[0];
                openResetPwd(u);
            });
        });
    }

    async function loadUsers(page) {
        uqState.page = page && page > 0 ? page : 1;
        loading(true, '查询中…');
        try {
            var data = await Api.queryUsers(collectUserQuery());
            renderUserTable(data);
        } catch (e) {
            handleApiError(e);
            $('#uqResult').innerHTML = '';
        } finally {
            loading(false);
        }
    }

    function roleOptions(selected) {
        return Object.keys(ROLE_META).map(function (k) {
            return '<option value="' + k + '"' + (k === selected ? ' selected' : '') + '>'
                + esc(ROLE_META[k]) + '（' + k + '）</option>';
        }).join('');
    }

    /** 带"显示/隐藏"切换的密码输入框 */
    function pwdField(id, placeholder) {
        return '<div class="pwd-wrap">'
            + '<input id="' + id + '" type="password" placeholder="' + esc(placeholder || '') + '" autocomplete="new-password">'
            + '<button type="button" class="pwd-eye" data-pwd-toggle="' + id + '">显示</button>'
            + '</div>';
    }

    /** 新增 / 编辑用户 */
    function openUserForm(user) {
        var isEdit = !!user;
        var body = ''
            + '<label class="field"><span>用户名</span>'
            + '<input id="uf_username" type="text" value="' + esc(isEdit ? user.username : '') + '"'
            + (isEdit ? ' disabled' : ' placeholder="登录账号"') + '></label>'
            + (isEdit ? '' :
                '<label class="field"><span>初始密码</span>'
                + pwdField('uf_password', '至少 6 位')
                + '<div class="modal-tip">创建后可在列表中通过“重置密码”修改。</div>')
            + '<label class="field"><span>姓名</span>'
            + '<input id="uf_realName" type="text" value="' + esc(isEdit ? user.realName : '') + '"></label>'
            + '<label class="field"><span>角色</span><select id="uf_role">'
            + roleOptions(isEdit ? user.role : 'USER') + '</select></label>'
            + '<label class="field"><span>手机号</span>'
            + '<input id="uf_phone" type="text" value="' + esc(isEdit ? user.phone : '') + '"></label>'
            + '<label class="field"><span>状态</span><select id="uf_status">'
            + '<option value="1"' + (isEdit && Number(user.status) === 0 ? '' : ' selected') + '>启用</option>'
            + '<option value="0"' + (isEdit && Number(user.status) === 0 ? ' selected' : '') + '>禁用</option>'
            + '</select></label>';

        openModal(isEdit ? '编辑用户' : '新增用户', body, [
            { text: '取消', onClick: closeModal },
            { text: '保存', cls: 'btn-primary', onClick: function () { submitUserForm(isEdit, user); } }
        ]);
    }

    async function submitUserForm(isEdit, user) {
        var realName = val('uf_realName');
        var role = val('uf_role');
        var phone = val('uf_phone');
        var status = Number(val('uf_status') || '1');
        loading(true, '保存中…');
        try {
            if (isEdit) {
                await Api.updateUser(user.id, {
                    realName: realName, role: role, phone: phone, status: status
                });
                toast('用户信息已更新', 'success');
                closeModal();
                loadUsers(uqState.page);
            } else {
                var username = val('uf_username');
                var password = val('uf_password');
                if (!username) { toast('请输入用户名', 'info'); return; }
                if (password.length < 6) { toast('初始密码至少 6 位', 'info'); return; }
                await Api.createUser({
                    username: username, password: password,
                    realName: realName, role: role, phone: phone, status: status
                });
                toast('用户已创建', 'success');
                closeModal();
                loadUsers(1);
            }
        } catch (e) {
            handleApiError(e);
        } finally {
            loading(false);
        }
    }

    /** 管理员重置他人密码 */
    function openResetPwd(user) {
        var body = '<div class="modal-tip" style="margin-bottom:12px">'
            + '正在重置用户 <b>' + esc(user.username) + '</b> 的密码，无需原密码。</div>'
            + '<label class="field"><span>新密码</span>'
            + pwdField('rp_password', '至少 6 位') + '</label>';
        openModal('重置密码', body, [
            { text: '取消', onClick: closeModal },
            {
                text: '确定重置', cls: 'btn-primary', onClick: async function () {
                    var pwd = val('rp_password');
                    if (pwd.length < 6) { toast('新密码至少 6 位', 'info'); return; }
                    loading(true, '重置中…');
                    try {
                        await Api.resetUserPassword(user.id, pwd);
                        toast('密码已重置', 'success');
                        closeModal();
                    } catch (e) {
                        handleApiError(e);
                    } finally {
                        loading(false);
                    }
                }
            }
        ]);
    }

    /** 本人修改密码 */
    function openChangeMyPwd() {
        var body = ''
            + '<label class="field"><span>原密码</span>'
            + '<input id="cp_old" type="password"></label>'
            + '<label class="field"><span>新密码</span>'
            + '<input id="cp_new" type="password" placeholder="至少 6 位"></label>'
            + '<label class="field"><span>确认新密码</span>'
            + '<input id="cp_new2" type="password"></label>';
        openModal('修改密码', body, [
            { text: '取消', onClick: closeModal },
            {
                text: '确定修改', cls: 'btn-primary', onClick: async function () {
                    var oldPwd = val('cp_old');
                    var newPwd = val('cp_new');
                    var newPwd2 = val('cp_new2');
                    if (!oldPwd) { toast('请输入原密码', 'info'); return; }
                    if (newPwd.length < 6) { toast('新密码至少 6 位', 'info'); return; }
                    if (newPwd !== newPwd2) { toast('两次输入的新密码不一致', 'info'); return; }
                    loading(true, '提交中…');
                    try {
                        await Api.changeMyPassword(oldPwd, newPwd);
                        toast('密码修改成功，下次登录请使用新密码', 'success');
                        closeModal();
                    } catch (e) {
                        handleApiError(e);
                    } finally {
                        loading(false);
                    }
                }
            }
        ]);
    }

    /* ==================== 数据查询 ==================== */

    /** 三个数据集的配置: 筛选字段 + 表格列 + 请求方法 */
    var DATASETS = {
        phone: {
            label: '号码库',
            load: function (p) { return Api.queryPhones(p); },
            filters: [
                { key: 'phone', label: '号码', type: 'text', ph: '支持模糊匹配' },
                { key: 'riskLevel', label: '风险等级', type: 'select', options: [
                    { v: '', l: '全部' },
                    { v: 'LOW', l: '低风险' },
                    { v: 'MEDIUM', l: '中风险' },
                    { v: 'HIGH', l: '高风险' }
                ] },
                { key: 'status', label: '号码状态', type: 'select', options: [
                    { v: '', l: '全部' },
                    { v: 'ACTIVE', l: '正常' },
                    { v: 'SHUTDOWN', l: '已关停' }
                ] }
            ],
            columns: [
                ['ID', 'id'], ['号码', 'phone'], ['掩码', 'phoneMasked'], ['运营商', 'carrier'],
                ['归属地', 'region'], ['24h呼出', 'callCount24h'], ['平均时长(s)', 'avgDurationSec'],
                ['被标记', 'markedCount'], ['黑名单', 'blacklistFlag'],
                ['风险', 'riskLevel', 'risk'], ['状态', 'status', 'state'], ['更新时间', 'updateTime']
            ]
        },
        case: {
            label: '风险案例',
            load: function (p) { return Api.queryRiskCases(p); },
            filters: [
                { key: 'caseNo', label: '案件编号', type: 'text', ph: '支持模糊匹配' },
                { key: 'phone', label: '号码', type: 'text', ph: '支持模糊匹配' },
                { key: 'riskLevel', label: '风险等级', type: 'select', options: [
                    { v: '', l: '全部' },
                    { v: 'LOW', l: '低风险' },
                    { v: 'MEDIUM', l: '中风险' },
                    { v: 'HIGH', l: '高风险' }
                ] },
                { key: 'handleResult', label: '处置结果', type: 'select', options: [
                    { v: '', l: '全部' },
                    { v: 'PENDING', l: '待审核' },
                    { v: 'APPROVED', l: '已通过' },
                    { v: 'REJECTED', l: '已驳回' },
                    { v: 'SHUTDOWN', l: '已关停' },
                    { v: 'CONTINUE_OBSERVE', l: '继续观察' }
                ] }
            ],
            columns: [
                ['ID', 'id'], ['案件编号', 'caseNo'], ['号码', 'phoneMasked'],
                ['风险', 'riskLevel', 'risk'], ['置信度', 'confidence'], ['诈骗类型', 'fraudType'],
                ['处置结果', 'handleResult', 'state'], ['审核状态', 'auditStatus', 'state'],
                ['研判理由', 'reason', 'ellipsis'], ['创建时间', 'createTime']
            ]
        },
        order: {
            label: '关停工单',
            load: function (p) { return Api.queryShutdownOrders(p); },
            filters: [
                { key: 'orderNo', label: '工单编号', type: 'text', ph: '支持模糊匹配' },
                { key: 'phone', label: '号码', type: 'text', ph: '支持模糊匹配' },
                { key: 'status', label: '工单状态', type: 'select', options: [
                    { v: '', l: '全部' },
                    { v: 'PENDING', l: '待审核' },
                    { v: 'SUBMITTED', l: '已提交' },
                    { v: 'SUCCESS', l: '成功' },
                    { v: 'FAILED', l: '失败' },
                    { v: 'CANCELLED', l: '已取消' }
                ] }
            ],
            columns: [
                ['ID', 'id'], ['工单编号', 'orderNo'], ['号码', 'phoneMasked'], ['关联案例ID', 'caseId'],
                ['状态', 'status', 'state'], ['通知状态', 'notifyStatus', 'state'],
                ['是否自动', 'isAuto', 'auto'], ['原因', 'reason', 'ellipsis'], ['创建时间', 'createTime']
            ]
        }
    };

    var dqState = { ds: 'phone', page: 1, size: 10 };

    /** 渲染当前数据集的筛选控件 */
    function renderDqFilters() {
        var cfg = DATASETS[dqState.ds];
        $('#dqFilters').innerHTML = cfg.filters.map(function (f) {
            var id = 'dq_' + f.key;
            if (f.type === 'select') {
                return '<label class="field-inline"><span>' + esc(f.label) + '</span><select id="' + id + '">'
                    + f.options.map(function (o) {
                        // 选项支持两种写法: 字符串(值即显示文本) 或 {v: 值, l: 显示文本}
                        var isObj = (typeof o === 'object' && o !== null);
                        var v = isObj ? o.v : o;
                        var l = isObj ? o.l : (o === '' ? '全部' : o);
                        return '<option value="' + esc(v) + '">' + esc(l) + '</option>';
                    }).join('') + '</select></label>';
            }
            return '<label class="field-inline"><span>' + esc(f.label) + '</span>'
                + '<input id="' + id + '" type="text" placeholder="' + esc(f.ph || '') + '"></label>';
        }).join('');
    }

    /** 收集筛选条件 */
    function collectDqParams() {
        var cfg = DATASETS[dqState.ds];
        var params = { page: dqState.page, size: dqState.size };
        cfg.filters.forEach(function (f) {
            var el = document.getElementById('dq_' + f.key);
            if (el && el.value) { params[f.key] = el.value.trim(); }
        });
        return params;
    }

    /** 单元格渲染: 按列类型做样式化 */
    function renderCell(col, row) {
        var key = col[1];
        var kind = col[2];
        var val = row[key];
        if (kind === 'risk') {
            var rm = val ? riskMeta(val) : null;
            return rm ? '<span class="state-tag ' + rm.cls + '">' + esc(rm.label) + '</span>' : '—';
        }
        if (kind === 'state') {
            return val ? stateTag(val) : '—';
        }
        if (kind === 'auto') {
            return val == null ? '—' : (val === 1 ? '<span class="state-tag observe">自动</span>'
                : '<span class="state-tag plain">人工</span>');
        }
        if (kind === 'ellipsis') {
            if (val == null || val === '') { return '—'; }
            var s = String(val);
            return '<span class="ellipsis" title="' + esc(s) + '">'
                + esc(s.length > 30 ? s.substring(0, 30) + '…' : s) + '</span>';
        }
        return (val == null || val === '') ? '—' : esc(val);
    }

    function renderDqTable(pageData) {
        var cfg = DATASETS[dqState.ds];
        var records = (pageData && pageData.records) || [];
        if (!records.length) {
            $('#dqResult').innerHTML = '<div class="empty">暂无数据</div>';
            return;
        }
        var head = cfg.columns.map(function (c) { return '<th>' + esc(c[0]) + '</th>'; }).join('');
        var body = records.map(function (r) {
            return '<tr>' + cfg.columns.map(function (c) {
                return '<td>' + renderCell(c, r) + '</td>';
            }).join('') + '</tr>';
        }).join('');

        var total = pageData.total || 0;
        var pages = pageData.pages || 1;
        var current = pageData.current || dqState.page;

        $('#dqResult').innerHTML =
            '<div class="table-wrap"><table class="data-table"><thead><tr>' + head
            + '</tr></thead><tbody>' + body + '</tbody></table></div>'
            + '<div class="pager">'
            + '<span class="pager-info">共 ' + total + ' 条 / ' + pages + ' 页，当前第 ' + current + ' 页</span>'
            + '<button class="btn btn-ghost btn-ghost-dark" id="dqPrev"' + (current <= 1 ? ' disabled' : '') + '>上一页</button>'
            + '<button class="btn btn-ghost btn-ghost-dark" id="dqNext"' + (current >= pages ? ' disabled' : '') + '>下一页</button>'
            + '</div>';

        var prev = document.getElementById('dqPrev');
        var next = document.getElementById('dqNext');
        if (prev) { prev.addEventListener('click', function () { loadDq(current - 1); }); }
        if (next) { next.addEventListener('click', function () { loadDq(current + 1); }); }
    }

    async function loadDq(page) {
        dqState.page = page && page > 0 ? page : 1;
        loading(true, '查询中…');
        try {
            var data = await DATASETS[dqState.ds].load(collectDqParams());
            renderDqTable(data);
        } catch (e) {
            handleApiError(e);
            $('#dqResult').innerHTML = '';
        } finally {
            loading(false);
        }
    }

    /* ==================== 知识库摄入 ==================== */

    async function doIngest(content, source, tag) {
        loading(true, '向量化并写入知识库…');
        try {
            await Api.ingest(content, source, tag);
            toast('单条摄入成功', 'success');
            return true;
        } catch (e) {
            handleApiError(e);
            return false;
        } finally {
            loading(false);
        }
    }

    async function doIngestBatch(lines, source, tag) {
        var items = lines.map(function (l) {
            return { content: l, source: source || '', tag: tag || '' };
        });
        loading(true, '批量写入 ' + items.length + ' 条…');
        try {
            await Api.ingestBatch(items);
            toast('批量摄入成功：' + items.length + ' 条', 'success');
            return true;
        } catch (e) {
            handleApiError(e);
            return false;
        } finally {
            loading(false);
        }
    }

    /* ==================== 事件绑定 ==================== */

    function bindEvents() {
        // 登录
        $('#loginForm').addEventListener('submit', function (e) {
            e.preventDefault();
            doLogin($('#username').value.trim(), $('#password').value);
        });
        $('#btnLogout').addEventListener('click', function () {
            Api.logout();
            toast('已退出登录', 'info');
            showLogin();
        });

        // 页签
        document.querySelectorAll('.nav-item').forEach(function (it) {
            it.addEventListener('click', function () { switchTab(it.getAttribute('data-tab')); });
        });

        // 对话
        $('#chatForm').addEventListener('submit', function (e) {
            e.preventDefault();
            sendChat();
        });
        document.querySelectorAll('.chip[data-q]').forEach(function (it) {
            it.addEventListener('click', function () {
                $('#chatInput').value = it.getAttribute('data-q');
                sendChat();
            });
        });

        // 号码研判
        $('#anForm').addEventListener('submit', function (e) {
            e.preventDefault();
            var phone = $('#anPhone').value.trim();
            if (!/^1\d{10}$/.test(phone)) { toast('请输入 11 位有效手机号', 'info'); return; }
            doAnalyze(phone);
        });

        // 案件审核
        $('#auForm').addEventListener('submit', function (e) {
            e.preventDefault();
            doSearchCase();
        });

        // 用户管理
        $('#btnAddUser').addEventListener('click', function () { openUserForm(null); });
        $('#uqForm').addEventListener('submit', function (e) {
            e.preventDefault();
            loadUsers(1);
        });
        $('#uqReset').addEventListener('click', function () {
            $('#uq_username').value = '';
            $('#uq_role').value = '';
            $('#uq_status').value = '';
            loadUsers(1);
        });
        $('#btnChangePwd').addEventListener('click', function () { openChangeMyPwd(); });

        // 弹窗关闭
        $('#modalClose').addEventListener('click', closeModal);
        $('#modalMask').addEventListener('click', closeModal);

        // 密码"显示/隐藏"切换（事件委托，兼容动态注入的弹窗内容）
        document.addEventListener('click', function (e) {
            var btn = e.target.closest && e.target.closest('[data-pwd-toggle]');
            if (!btn) return;
            var input = document.getElementById(btn.getAttribute('data-pwd-toggle'));
            if (!input) return;
            var show = input.type === 'password';
            input.type = show ? 'text' : 'password';
            btn.textContent = show ? '隐藏' : '显示';
            input.focus();
        });
        document.addEventListener('keydown', function (e) {
            if (e.key === 'Escape' && !$('#modal').classList.contains('hidden')) { closeModal(); }
        });

        // 数据查询: 子数据集切换
        document.querySelectorAll('.sub-tab').forEach(function (it) {
            it.addEventListener('click', function () {
                document.querySelectorAll('.sub-tab').forEach(function (x) { x.classList.remove('active'); });
                it.classList.add('active');
                dqState.ds = it.getAttribute('data-ds');
                dqState.page = 1;
                renderDqFilters();
                $('#dqResult').innerHTML = '';
                loadDq(1);
            });
        });
        $('#dqForm').addEventListener('submit', function (e) {
            e.preventDefault();
            loadDq(1);
        });
        $('#dqReset').addEventListener('click', function () {
            renderDqFilters();
            loadDq(1);
        });

        // 知识库摄入
        $('#inForm').addEventListener('submit', async function (e) {
            e.preventDefault();
            var content = $('#inContent').value.trim();
            if (!content) { toast('请输入内容', 'info'); return; }
            var ok = await doIngest(content, $('#inSource').value.trim(), $('#inTag').value.trim());
            if (ok) { $('#inContent').value = ''; }
        });
        $('#inBatchForm').addEventListener('submit', async function (e) {
            e.preventDefault();
            var lines = $('#inBatchContent').value.split(/\r?\n/).map(function (s) { return s.trim(); })
                .filter(function (s) { return s.length > 0; });
            if (!lines.length) { toast('请输入至少一条内容', 'info'); return; }
            var ok = await doIngestBatch(lines, $('#inBatchSource').value.trim(), $('#inBatchTag').value.trim());
            if (ok) { $('#inBatchContent').value = ''; }
        });
    }

    /* ==================== 启动 ==================== */

    async function init() {
        // 先注入 partials/*.html 子模板, 再绑定事件(否则取不到页面元素)
        try {
            await Views.load('content');
        } catch (e) {
            var box = document.getElementById('content');
            if (box) {
                box.innerHTML = '<div class="panel">子模板加载失败，请检查静态资源是否可访问：'
                    + esc((e && e.message) || e) + '</div>';
            }
            toast('页面子模板加载失败', 'error');
            return;
        }
        bindEvents();
        if (Api.getToken() && Api.getUser()) {
            showApp();
        } else {
            showLogin();
        }
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }
})();
