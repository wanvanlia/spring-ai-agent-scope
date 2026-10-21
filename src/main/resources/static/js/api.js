/**
 * 反诈 Agent 前端 API 封装
 * 统一对接 /antifraud 下的 REST 接口，自动附带 Bearer Token，
 * 解析统一响应结构 Result { code, message, data, traceId }。
 */
(function (global) {
    'use strict';

    var CTX = '/antifraud';
    var TOKEN_KEY = 'antifraud_token';
    var USER_KEY = 'antifraud_user';

    function ApiError(message, status, payload) {
        this.message = message || '请求失败';
        this.status = status || -1;
        this.payload = payload || null;
    }
    ApiError.prototype = Object.create(Error.prototype);
    ApiError.prototype.name = 'ApiError';

    function getToken() {
        try { return localStorage.getItem(TOKEN_KEY) || ''; } catch (e) { return ''; }
    }

    function setToken(t) {
        try {
            if (t) { localStorage.setItem(TOKEN_KEY, t); } else { localStorage.removeItem(TOKEN_KEY); }
        } catch (e) { /* ignore */ }
    }

    function getUser() {
        try {
            var raw = localStorage.getItem(USER_KEY);
            return raw ? JSON.parse(raw) : null;
        } catch (e) { return null; }
    }

    function setUser(u) {
        try {
            if (u) { localStorage.setItem(USER_KEY, JSON.stringify(u)); } else { localStorage.removeItem(USER_KEY); }
        } catch (e) { /* ignore */ }
    }

    /**
     * 统一请求入口
     * @param {string} path 如 /api/chat
     * @param {object} [opts] { method, body, query }
     * @returns {Promise<*>} 已解包后的 data
     */
    async function request(path, opts) {
        opts = opts || {};
        var method = (opts.method || 'GET').toUpperCase();
        var url = CTX + path;
        if (opts.query) {
            var qs = Object.keys(opts.query)
                .filter(function (k) { return opts.query[k] != null && opts.query[k] !== ''; })
                .map(function (k) { return encodeURIComponent(k) + '=' + encodeURIComponent(opts.query[k]); })
                .join('&');
            if (qs) { url += (url.indexOf('?') >= 0 ? '&' : '?') + qs; }
        }

        var headers = { 'Content-Type': 'application/json' };
        var token = getToken();
        if (token) { headers['Authorization'] = 'Bearer ' + token; }

        var resp;
        try {
            resp = await fetch(url, {
                method: method,
                headers: headers,
                body: opts.body != null ? JSON.stringify(opts.body) : undefined
            });
        } catch (e) {
            throw new ApiError('网络异常，无法连接服务器：' + e.message, 0);
        }

        var text = '';
        try { text = await resp.text(); } catch (e) { /* ignore */ }

        var data = null;
        if (text) {
            try { data = JSON.parse(text); } catch (e) { data = null; }
        }

        if (resp.status === 401) {
            throw new ApiError((data && data.message) || '登录已失效，请重新登录', 401, data);
        }
        if (data && typeof data.code === 'number' && data.code !== 0) {
            throw new ApiError(data.message || ('业务处理失败(code=' + data.code + ')'), resp.status, data);
        }
        if (!resp.ok) {
            throw new ApiError((data && data.message) || ('请求失败(HTTP ' + resp.status + ')'), resp.status, data);
        }
        return data ? data.data : null;
    }

    global.Api = {
        ApiError: ApiError,
        getToken: getToken,
        getUser: getUser,

        /** 登录 POST /auth/login */
        login: function (username, password) {
            return request('/auth/login', { method: 'POST', body: { username: username, password: password } });
        },

        /** 保存会话 */
        remember: function (token, username, role) {
            setToken(token);
            setUser({ username: username, role: role });
        },
        logout: function () { setToken(null); setUser(null); },

        /** 对话 POST /api/chat */
        chat: function (message) {
            return request('/api/chat', { method: 'POST', body: { message: message } });
        },

        /** 号码研判 POST /api/analyze?phone= */
        analyze: function (phone) {
            return request('/api/analyze', { method: 'POST', query: { phone: phone } });
        },

        /** 查询案例 GET /api/cases/{caseNo} */
        getCase: function (caseNo) {
            return request('/api/cases/' + encodeURIComponent(caseNo), { method: 'GET' });
        },

        /** 人工确认关停 POST /api/cases/{caseNo}/approve */
        approve: function (caseNo) {
            return request('/api/cases/' + encodeURIComponent(caseNo) + '/approve', { method: 'POST' });
        },

        /** 人工驳回 POST /api/cases/{caseNo}/reject?reason= */
        reject: function (caseNo, reason) {
            return request('/api/cases/' + encodeURIComponent(caseNo) + '/reject',
                { method: 'POST', query: { reason: reason || '' } });
        },

        /** 号码库分页查询 GET /api/query/phones */
        queryPhones: function (params) {
            return request('/api/query/phones', { query: params });
        },

        /** 风险案例分页查询 GET /api/query/risk-cases */
        queryRiskCases: function (params) {
            return request('/api/query/risk-cases', { query: params });
        },

        /** 关停工单分页查询 GET /api/query/shutdown-orders */
        queryShutdownOrders: function (params) {
            return request('/api/query/shutdown-orders', { query: params });
        },

        /** 用户列表 GET /api/users */
        queryUsers: function (params) {
            return request('/api/users', { query: params });
        },

        /** 新增用户 POST /api/users */
        createUser: function (body) {
            return request('/api/users', { method: 'POST', body: body });
        },

        /** 修改用户 PUT /api/users/{id} */
        updateUser: function (id, body) {
            return request('/api/users/' + encodeURIComponent(id), { method: 'PUT', body: body });
        },

        /** 管理员重置密码 POST /api/users/{id}/password */
        resetUserPassword: function (id, newPassword) {
            return request('/api/users/' + encodeURIComponent(id) + '/password',
                { method: 'POST', body: { newPassword: newPassword } });
        },

        /** 本人修改密码 POST /api/users/me/password */
        changeMyPassword: function (oldPassword, newPassword) {
            return request('/api/users/me/password',
                { method: 'POST', body: { oldPassword: oldPassword, newPassword: newPassword } });
        },

        /** 单条摄入 POST /api/ingest */
        ingest: function (content, source, tag) {
            return request('/api/ingest', { method: 'POST', body: { content: content, source: source, tag: tag } });
        },

        /** 批量摄入 POST /api/ingest/batch */
        ingestBatch: function (items) {
            return request('/api/ingest/batch', { method: 'POST', body: items });
        }
    };
})(window);
