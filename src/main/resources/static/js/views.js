/**
 * 子模板加载器。
 * <p>
 * 为避免 index.html 过于臃肿, 各功能页拆分为 partials/*.html,
 * 启动时按顺序 fetch 并注入到 #content, 之后再绑定事件(见 app.js 的 init)。
 * <p>
 * 注意:
 * 1. 这里使用相对路径, 自动适配 context-path(如 /antifraud), 无需硬编码;
 * 2. 这些静态文件必须允许匿名访问 —— 见 SecurityConfig 对 /partials/* 的放行配置,
 *    否则未登录时会因 401 导致子模板加载失败。
 */
window.Views = (function () {

    var PARTIALS = [
        'partials/chat.html',
        'partials/analyze.html',
        'partials/audit.html',
        'partials/data.html',
        'partials/users.html',
        'partials/ingest.html'
    ];

    /**
     * 加载全部子模板
     * @param {string} [rootId='content'] 容器元素 id
     * @returns {Promise<void>}
     */
    async function load(rootId) {
        var root = document.getElementById(rootId || 'content');
        if (!root) {
            throw new Error('未找到模板容器 #' + (rootId || 'content'));
        }
        for (var i = 0; i < PARTIALS.length; i++) {
            var res = await fetch(PARTIALS[i], { cache: 'no-cache' });
            if (!res.ok) {
                throw new Error(PARTIALS[i] + ' (HTTP ' + res.status + ')');
            }
            root.insertAdjacentHTML('beforeend', await res.text());
        }
    }

    return { load: load, PARTIALS: PARTIALS };
})();
