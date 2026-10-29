/* WiFiVault 观看端：全部为本地资源，不请求任何外部网络地址，断网可用。 */
(function () {
  'use strict';

  var $ = function (id) { return document.getElementById(id); };

  var state = {
    libs: [],
    lib: null,
    path: '',
    items: [],
    view: 'grid',
    query: ''
  };

  // 播放器配置，由手机端设置下发（/api/config）
  var cfg = { seekStep: 5, longPressRate: 2, autoPlayNext: true, playerMode: 'auto' };

  var MODE_LABELS = { auto: '自动', full: '完整', compact: '精简', native: '原生' };
  var MODE_ORDER = ['auto', 'full', 'compact', 'native'];
  var modeOverride = null;

  var ICONS = {
    dir: '\u{1F4C1}', image: '\u{1F5BC}', video: '\u{1F3AC}',
    audio: '\u{1F3B5}', text: '\u{1F4C4}', pdf: '\u{1F4D5}', other: '\u{1F4E6}'
  };

  var content = $('content');
  var video = $('video');

  // ---------------------------------------------------------------- 工具

  function esc(s) {
    return String(s).replace(/[&<>"']/g, function (c) {
      return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c];
    });
  }

  function size(n) {
    if (!n || n < 0) return '';
    var u = ['B', 'KB', 'MB', 'GB', 'TB'], i = 0;
    while (n >= 1024 && i < u.length - 1) { n /= 1024; i++; }
    return (i === 0 ? n : n.toFixed(1)) + ' ' + u[i];
  }

  function fmt(sec) {
    if (!isFinite(sec) || sec < 0) sec = 0;
    var h = Math.floor(sec / 3600), m = Math.floor(sec % 3600 / 60), s = Math.floor(sec % 60);
    var mm = h > 0 ? String(m).padStart(2, '0') : String(m);
    return (h > 0 ? h + ':' : '') + mm + ':' + String(s).padStart(2, '0');
  }

  var toastTimer;
  function toast(msg) {
    var el = $('toast');
    el.textContent = msg;
    el.classList.remove('hidden');
    clearTimeout(toastTimer);
    toastTimer = setTimeout(function () { el.classList.add('hidden'); }, 2200);
  }

  function loading(on) { $('loading').classList.toggle('hidden', !on); }

  function rawUrl(p, download) {
    return '/api/' + (download ? 'download' : 'raw') +
      '?lib=' + encodeURIComponent(state.lib) + '&path=' + encodeURIComponent(p);
  }

  function thumbUrl(p) {
    return '/api/thumb?lib=' + encodeURIComponent(state.lib) + '&path=' + encodeURIComponent(p);
  }

  function joinPath(parent, name) { return parent ? parent + '/' + name : name; }

  // ---------------------------------------------------------------- 网络

  async function jget(path, params) {
    loading(true);
    try {
      var url = path + '?' + new URLSearchParams(params).toString();
      var res = await fetch(url, { credentials: 'same-origin' });
      if (res.status === 401) { $('pinModal').classList.remove('hidden'); $('pinInput').focus(); return null; }
      if (!res.ok) throw new Error('HTTP ' + res.status);
      return await res.json();
    } catch (e) {
      toast('加载失败：' + e.message);
      return null;
    } finally {
      loading(false);
    }
  }

  // ---------------------------------------------------------------- 渲染

  function visibleItems() {
    var q = state.query.trim().toLowerCase();
    var list = state.items.filter(function (it) {
      return !q || it.name.toLowerCase().indexOf(q) >= 0;
    });
    return list.sort(function (a, b) {
      if (a.type === 'dir' && b.type !== 'dir') return -1;
      if (b.type === 'dir' && a.type !== 'dir') return 1;
      return a.name.localeCompare(b.name, 'zh-Hans-CN', { numeric: true });
    });
  }

  function render() {
    var items = visibleItems();
    content.className = state.view === 'grid' ? 'grid' : 'list';

    var empty = $('empty');
    if (!items.length) {
      content.innerHTML = '';
      empty.classList.remove('hidden');
      empty.textContent = state.items.length ? '没有匹配的内容' : '这个文件夹是空的';
      return;
    }
    empty.classList.add('hidden');

    content.innerHTML = items.map(function (it, i) {
      var hasThumb = it.type === 'image' || it.type === 'video';
      var bg = hasThumb ? ' style="background-image:url(' + esc(thumbUrl(it.path)) + ')"' : '';
      var icon = hasThumb ? '' : '<span>' + (ICONS[it.type] || ICONS.other) + '</span>';
      var ext = it.type === 'dir' ? '' : (it.name.split('.').pop() || '').toUpperCase().slice(0, 4);
      return '<div class="tile" data-i="' + i + '" tabindex="0">' +
        '<div class="thumb"' + bg + '>' + icon +
        (ext ? '<span class="badge">' + esc(ext) + '</span>' : '') + '</div>' +
        '<div class="meta"><div class="name">' + esc(it.name) + '</div>' +
        '<div class="sub">' + (it.type === 'dir' ? '文件夹' : size(it.size)) + '</div></div></div>';
    }).join('');
  }

  function renderCrumbs() {
    var parts = state.path ? state.path.split('/') : [];
    var html = '<button class="crumb" data-p="">' + esc(libName()) + '</button>';
    var acc = '';
    parts.forEach(function (p, idx) {
      acc = joinPath(acc, p);
      var isLast = idx === parts.length - 1;
      html += '<span class="muted">/</span><button class="crumb' +
        (isLast ? ' current' : '') + '" data-p="' + esc(acc) + '">' + esc(p) + '</button>';
    });
    $('crumbs').innerHTML = html;
  }

  function libName() {
    var lib = state.libs.find(function (l) { return l.id === state.lib; });
    return lib ? lib.name : '共享';
  }

  // ---------------------------------------------------------------- 导航

  async function load(path) {
    var data = await jget('/api/list', { lib: state.lib, path: path });
    if (!data) return;
    state.path = data.path || '';
    state.items = data.items || [];
    renderCrumbs();
    render();
    var h = '#/' + encodeURIComponent(state.lib) +
      (state.path ? '/' + state.path.split('/').map(encodeURIComponent).join('/') : '');
    if (location.hash !== h) location.hash = h;
  }

  function open(i) {
    var items = visibleItems();
    var it = items[i];
    if (!it) return;
    if (it.type === 'dir') { load(it.path); return; }
    if (it.type === 'image') { openLightbox(items.filter(isImage), items.filter(isImage).indexOf(it)); return; }
    if (it.type === 'video') { var v = items.filter(isVideo); openPlayer(v, v.indexOf(it)); return; }
    if (it.type === 'audio') { playAudio(it); return; }
    // 其余全部走通用预览：文档、PDF、文本、代码、压缩包等
    var files = items.filter(isFile);
    openPreview(files, files.indexOf(it));
  }

  function isImage(it) { return it.type === 'image'; }
  function isVideo(it) { return it.type === 'video'; }
  function isFile(it) { return it.type !== 'dir'; }

  // ---------------------------------------------------------------- 图片查看器

  var lbList = [], lbIndex = 0, lbScale = 1, lbX = 0, lbY = 0;
  var lbTouch = null;

  function openLightbox(list, index) {
    lbList = list; lbIndex = index;
    resetLbView();
    $('lightbox').classList.remove('hidden');
    showLb();
  }

  function applyLbTransform() {
    $('lbImg').style.transform =
      'translate(' + lbX + 'px,' + lbY + 'px) scale(' + lbScale + ')';
  }

  function resetLbView() {
    lbScale = 1;
    lbX = 0;
    lbY = 0;
    applyLbTransform();
  }

  function clampLbOffset() {
    var img = $('lbImg');
    var w = img.clientWidth || 0;
    var h = img.clientHeight || 0;
    var maxX = Math.max(0, (w * lbScale - w) / 2);
    var maxY = Math.max(0, (h * lbScale - h) / 2);
    lbX = clamp(lbX, -maxX, maxX);
    lbY = clamp(lbY, -maxY, maxY);
  }

  function setLbScale(next) {
    lbScale = clamp(next, 1, 5);
    if (lbScale <= 1.001) {
      lbScale = 1;
      lbX = 0;
      lbY = 0;
    }
    clampLbOffset();
    applyLbTransform();
  }

  function showLb() {
    var it = lbList[lbIndex];
    if (!it) return;
    $('lbImg').src = rawUrl(it.path);
    applyLbTransform();
    $('lbName').textContent = (lbIndex + 1) + ' / ' + lbList.length + '  ' + it.name;
  }

  function lbStep(d) {
    if (!lbList.length) return;
    lbIndex = (lbIndex + d + lbList.length) % lbList.length;
    resetLbView();
    showLb();
  }

  function closeLb() {
    $('lightbox').classList.add('hidden');
    $('lbImg').src = '';
    lbTouch = null;
    resetLbView();
  }

  function lbDistance(a, b) {
    var dx = a.clientX - b.clientX;
    var dy = a.clientY - b.clientY;
    return Math.sqrt(dx * dx + dy * dy);
  }

  function lbMid(a, b) {
    return {
      x: (a.clientX + b.clientX) / 2,
      y: (a.clientY + b.clientY) / 2
    };
  }

  // ---------------------------------------------------------------- 视频播放器

  var vList = [], vIndex = 0, hideTimer;
  var plGroups = [], plOpen = false;
  var playerEl = $('player');
  var gesture = null, longPressTimer = null, hintTimer = null;
  var fastPrevRate = 1, mouseLong = false;

  function clamp(v, lo, hi) { return v < lo ? lo : (v > hi ? hi : v); }

  function isNativeMode() { return playerEl.dataset.mode === 'native'; }

  /** 风险机型 / 内置 WebView / 社交 App：系统媒体控件更容易压在页面上。 */
  function riskyPlayerHost() {
    var ua = navigator.userAgent || '';
    if (/(^|[;\s])wv([;\s)]|$)|Version\/4\.0/i.test(ua)) return true;
    return /MIUI|HyperOS|ColorOS|EMUI|HarmonyOS|HuaweiBrowser|MQQBrowser|UCBrowser|Quark|baidubrowser|MicroMessenger|WeChat|QQ\//i.test(ua);
  }

  function requestedMode() {
    return modeOverride || cfg.playerMode || 'auto';
  }

  function resolvedMode() {
    var mode = requestedMode();
    if (mode === 'auto') mode = riskyPlayerHost() ? 'compact' : 'full';
    return MODE_LABELS[mode] && mode !== 'auto' ? mode : 'full';
  }

  function applyPlayerMode() {
    var requested = requestedMode();
    var mode = resolvedMode();

    playerEl.dataset.mode = mode;
    video.controls = mode === 'native';
    $('nativeBar').classList.toggle('hidden', mode !== 'native');
    if (mode === 'native') {
      video.setAttribute('controlslist', 'nodownload nofullscreen');
      $('nvTitle').textContent = $('vTitle').textContent;
      $('nvRate').value = $('vRate').value;
    } else {
      video.removeAttribute('controlslist');
    }
    if (mode !== 'full') closePlaylist();

    var base = MODE_LABELS[requested] || MODE_LABELS.auto;
    var actual = MODE_LABELS[mode];
    $('vMode').textContent = requested === 'auto' ? base + '→' + actual : base;
    $('nvMode').textContent = requested === 'auto' ? base + '→' + actual : base;
    $('vMode').title = '播放器显示模式：当前 ' + actual + '，点击切换';
    $('nvMode').title = $('vMode').title;
    if (mode !== 'native') autoHide();
    syncPlayerLayout();
  }

  /** 浮动按钮贴在控制条上方，避免遮住标题和进度条。 */
  function syncPlayerLayout() {
    var ctrl = $('ctrl');
    playerEl.style.setProperty('--ctrl-h', (ctrl ? ctrl.offsetHeight : 0) + 'px');
  }

  window.addEventListener('resize', syncPlayerLayout);
  document.addEventListener('fullscreenchange', syncPlayerLayout);

  function cyclePlayerMode() {
    var cur = requestedMode();
    var idx = MODE_ORDER.indexOf(cur);
    modeOverride = MODE_ORDER[(idx + 1) % MODE_ORDER.length];
    applyPlayerMode();
    toast('播放器模式：' + MODE_LABELS[modeOverride] + (modeOverride === 'auto' ? '（自动选择）' : ''));
  }

  function setPlaybackRate(value) {
    video.playbackRate = parseFloat(value);
    $('vRate').value = String(value);
    $('nvRate').value = String(value);
  }

  function toggleSubtitle() {
    var t = video.textTracks[0];
    if (!t) { toast('该视频没有找到同名字幕'); return; }
    t.mode = t.mode === 'showing' ? 'hidden' : 'showing';
    toast(t.mode === 'showing' ? '字幕已开启' : '字幕已关闭');
  }

  function toggleFullscreen() {
    var el = $('player');
    if (document.fullscreenElement) document.exitFullscreen();
    else if (el.requestFullscreen) el.requestFullscreen();
    else if (video.webkitEnterFullscreen) video.webkitEnterFullscreen();
  }

  function showHint(text) {
    var el = $('hint');
    el.textContent = text;
    el.classList.remove('hidden');
    clearTimeout(hintTimer);
    hintTimer = setTimeout(function () { el.classList.add('hidden'); }, 1000);
  }

  function setVolume(v) {
    video.volume = clamp(v, 0, 1);
    video.muted = video.volume === 0;
    $('vVol').value = String(video.volume);
    updateMute();
  }

  function changeVolume(delta) {
    setVolume(video.volume + delta);
    showHint('音量 ' + Math.round(video.volume * 100) + '%');
  }

  function updateMute() {
    var el = $('vMute');
    var silent = video.muted || video.volume === 0;
    el.textContent = silent ? '\u{1F507}' : '\u{1F50A}';
    el.classList.toggle('active', silent);
  }

  function updateSeekUi(t) {
    if (video.duration) $('seek').value = String(Math.round(t / video.duration * 1000));
    $('vCur').textContent = fmt(t);
  }

  function jump(sec) {
    if (!video.duration) return;
    var t = clamp(video.currentTime + sec, 0, video.duration - 0.3);
    video.currentTime = t;
    updateSeekUi(t);
    showHint((sec >= 0 ? '+' : '') + sec + 's   ' + fmt(t));
  }

  function startFast() {
    if (video.paused) return;
    fastPrevRate = video.playbackRate;
    video.playbackRate = cfg.longPressRate;
    showHint(cfg.longPressRate + 'x 倍速播放中');
  }

  function stopFast() {
    if (video.playbackRate === cfg.longPressRate) video.playbackRate = fastPrevRate || 1;
  }

  function openPlayer(list, index) {
    vList = list.slice(); vIndex = index;
    plGroups = [];
    $('player').classList.remove('hidden');
    loadVideo();
    applyPlayerMode();
    loadPlaylist();
  }

  // ---------------------------------------------------------------- 播放目录

  function dirOf(p) {
    var s = String(p || '');
    var i = s.lastIndexOf('/');
    return i >= 0 ? s.slice(0, i) : '';
  }

  /** 拉取当前目录 + 一层子目录里的视频，让播放器可以跨目录切换和连播。 */
  async function loadPlaylist() {
    var cur = vList[vIndex];
    var dir = cur ? dirOf(cur.path) : state.path;
    try {
      var res = await fetch('/api/videos?lib=' + encodeURIComponent(state.lib) +
        '&path=' + encodeURIComponent(dir), { credentials: 'same-origin' });
      if (!res.ok) return;
      var data = await res.json();
      applyPlaylist(data.groups || []);
    } catch (e) { /* 接口不可用时保留当前目录的列表 */ }
  }

  function applyPlaylist(groups) {
    plGroups = groups;
    var flat = [];
    groups.forEach(function (g) {
      (g.items || []).forEach(function (it) {
        flat.push({ name: it.name, path: it.path, size: it.size, type: 'video' });
      });
    });
    if (!flat.length) return;
    var cur = vList[vIndex];
    vList = flat;
    var idx = -1;
    if (cur) {
      for (var i = 0; i < flat.length; i++) {
        if (flat[i].path === cur.path) { idx = i; break; }
      }
    }
    if (idx >= 0) vIndex = idx;
    else if (vIndex >= flat.length) vIndex = 0;
    renderPlaylist();
  }

  function renderPlaylist() {
    var body = $('plBody');
    if (!plGroups.length) {
      body.innerHTML = '<div class="pl-empty">正在读取目录…</div>';
      return;
    }
    var n = 0;
    body.innerHTML = plGroups.map(function (g) {
      var items = g.items || [];
      var rows = items.map(function (it) {
        var idx = n++;
        return '<div class="pl-item' + (idx === vIndex ? ' active' : '') + '" data-i="' + idx +
          '" title="' + esc(it.name) + '">' +
          '<span class="pl-no">' + (idx + 1) + '</span>' +
          '<span class="pl-name">' + esc(it.name) + '</span></div>';
      }).join('');
      return '<div class="pl-group"><div class="pl-gname">' + esc(g.name) +
        '<span class="pl-count">' + items.length + '</span></div>' + rows + '</div>';
    }).join('');
  }

  function openPlaylist() {
    plOpen = true;
    $('plPanel').classList.remove('hidden');
    renderPlaylist();
    var el = $('plBody').querySelector('.pl-item.active');
    if (el && el.scrollIntoView) el.scrollIntoView({ block: 'center' });
  }

  function closePlaylist() {
    plOpen = false;
    $('plPanel').classList.add('hidden');
  }

  function togglePlaylist() { plOpen ? closePlaylist() : openPlaylist(); }

  function playIndex(i) {
    if (i < 0 || i >= vList.length) return;
    saveProgress();
    vIndex = i;
    closePlaylist();
    loadVideo();
    renderPlaylist();
  }

  function progressKey(it) { return 'wifivault:progress:' + state.lib + ':' + it.path; }

  function loadVideo() {
    var it = vList[vIndex];
    if (!it) return;
    $('vTitle').textContent = it.name;
    $('nvTitle').textContent = it.name;
    video.src = rawUrl(it.path);
    video.playbackRate = parseFloat($('vRate').value);
    $('nvRate').value = $('vRate').value;
    $('vVol').value = String(video.volume);
    updateMute();
    loadSubtitle(it);
    video.play().catch(function () { /* 需要用户交互 */ });
    video.addEventListener('loadedmetadata', function onMeta() {
      var saved = parseFloat(localStorage.getItem(progressKey(it)) || '0');
      if (saved > 0 && saved < video.duration - 5) video.currentTime = saved;
      video.removeEventListener('loadedmetadata', onMeta);
    });
    autoHide();
  }

  async function loadSubtitle(it) {
    Array.prototype.forEach.call(video.querySelectorAll('track'), function (t) { t.remove(); });
    try {
      var res = await fetch('/api/subtitle?lib=' + encodeURIComponent(state.lib) +
        '&path=' + encodeURIComponent(it.path), { credentials: 'same-origin' });
      if (!res.ok) return;
      var text = await res.text();
      var url = URL.createObjectURL(new Blob([text], { type: 'text/vtt' }));
      var track = document.createElement('track');
      track.kind = 'subtitles';
      track.label = '中文字幕';
      track.srclang = 'zh';
      track.default = true;
      track.src = url;
      video.appendChild(track);
    } catch (e) { /* 无字幕 */ }
  }

  function vStep(d) {
    if (!vList.length) return;
    saveProgress();
    vIndex = (vIndex + d + vList.length) % vList.length;
    loadVideo();
    renderPlaylist();
  }

  function saveProgress() {
    var it = vList[vIndex];
    if (it && video.currentTime > 5) {
      try { localStorage.setItem(progressKey(it), String(video.currentTime)); } catch (e) { /* ignore */ }
    }
  }

  function closePlayer() {
    saveProgress();
    closePlaylist();
    video.pause();
    video.removeAttribute('src');
    $('player').classList.add('hidden');
  }

  function autoHide() {
    var ctrl = $('ctrl');
    ctrl.classList.remove('fade');
    clearTimeout(hideTimer);
    hideTimer = setTimeout(function () {
      if (!video.paused) ctrl.classList.add('fade');
    }, 2800);
  }

  // ---------------------------------------------------------------- 通用预览

  var pvList = [], pvIndex = 0, pvItem = null;

  var TEXT_EXT = ['txt', 'log', 'md', 'markdown', 'json', 'csv', 'xml', 'yml', 'yaml', 'ini',
    'conf', 'properties', 'srt', 'vtt', 'lrc', 'nfo', 'html', 'htm', 'css', 'kt', 'java', 'py',
    'js', 'ts', 'jsx', 'tsx', 'c', 'h', 'cpp', 'hpp', 'cs', 'go', 'rs', 'rb', 'php', 'sh',
    'bat', 'ps1', 'sql', 'gradle', 'toml', 'gitignore'];
  var OFFICE_EXT = ['doc', 'docx', 'xls', 'xlsx', 'ppt', 'pptx'];

  function extOf(name) { return String(name || '').split('.').pop().toLowerCase(); }

  function openPreview(list, index) {
    pvList = list; pvIndex = index;
    $('preview').classList.remove('hidden');
    showPreview();
  }

  function closePreview() {
    $('preview').classList.add('hidden');
    $('pvBody').innerHTML = '';
    pvItem = null;
  }

  function pvStep(d) {
    if (!pvList.length) return;
    pvIndex = (pvIndex + d + pvList.length) % pvList.length;
    showPreview();
  }

  async function showPreview() {
    var it = pvList[pvIndex];
    if (!it) return;
    pvItem = it;
    $('pvName').textContent = (pvIndex + 1) + ' / ' + pvList.length + '  ' + it.name;
    var ext = extOf(it.name);

    if (ext === 'pdf') {
      $('pvBody').innerHTML = '<iframe class="pv-frame" src="' + esc(rawUrl(it.path)) +
        '#view=FitH" title="PDF 预览"></iframe>';
      return;
    }
    if (ext === 'svg') {
      $('pvBody').innerHTML = '<div class="pv-center"><img src="' + esc(rawUrl(it.path)) + '" alt=""></div>';
      return;
    }
    if (OFFICE_EXT.indexOf(ext) >= 0) { renderUnsupported(it, ext, true); return; }
    if (TEXT_EXT.indexOf(ext) >= 0 || it.type === 'text') { await renderText(it); return; }

    // 未知扩展名：先探测是否为纯文本，是则按文本预览，否则给出下载卡片
    $('pvBody').innerHTML = '<div class="pv-loading">读取中…</div>';
    var text = await fetchText(it);
    if (text !== null && isTextLike(text)) renderTextBody(text);
    else renderUnsupported(it, ext, false);
  }

  async function fetchText(it) {
    try {
      var res = await fetch(rawUrl(it.path), {
        credentials: 'same-origin',
        headers: { Range: 'bytes=0-262143' }
      });
      if (!res.ok && res.status !== 206) return null;
      return await res.text();
    } catch (e) { return null; }
  }

  async function renderText(it) {
    $('pvBody').innerHTML = '<div class="pv-loading">读取中…</div>';
    var text = await fetchText(it);
    if (text === null) { renderUnsupported(it, extOf(it.name), false); return; }
    renderTextBody(text);
  }

  function renderTextBody(text) {
    var truncated = text.length >= 262144;
    $('pvBody').innerHTML = '<pre class="pv-text">' + esc(text.slice(0, 262144)) + '</pre>' +
      (truncated ? '<div class="pv-note">文件较大，仅显示前 256 KB，可下载查看完整内容</div>' : '');
  }

  function isTextLike(t) {
    var s = t.slice(0, 2000);
    if (!s) return false;
    var bad = 0;
    for (var i = 0; i < s.length; i++) {
      var c = s.charCodeAt(i);
      if (c === 9 || c === 10 || c === 13) continue;
      if (c < 32 || (c > 126 && c < 8192)) bad++;
    }
    return bad / s.length < 0.05;
  }

  function renderUnsupported(it, ext, isOffice) {
    $('pvBody').innerHTML =
      '<div class="pv-card">' +
      '<div class="pv-ext">' + esc((ext || 'FILE').toUpperCase().slice(0, 6)) + '</div>' +
      '<div class="pv-t">' + esc(it.name) + '</div>' +
      '<div class="pv-d">' + size(it.size) + '</div>' +
      '<div class="pv-d">' + (isOffice
        ? 'Office 文档无法在浏览器里直接渲染，可下载后用本机应用打开'
        : '该格式没有内置预览，可下载后查看') + '</div>' +
      '<button class="primary" id="pvDl2">下载到本机</button>' +
      '</div>';
    var btn = $('pvDl2');
    if (btn) btn.addEventListener('click', function () { downloadCurrent(); });
  }

  function downloadCurrent() {
    if (pvItem) window.location.href = rawUrl(pvItem.path, true);
  }

  // ---------------------------------------------------------------- 音频

  function playAudio(it) {
    $('audioBar').classList.remove('hidden');
    $('aName').textContent = it.name;
    var audio = $('audio');
    audio.src = rawUrl(it.path);
    audio.play().catch(function () { /* ignore */ });
  }

  // ---------------------------------------------------------------- 事件绑定

  content.addEventListener('click', function (e) {
    var tile = e.target.closest('.tile');
    if (tile) open(parseInt(tile.dataset.i, 10));
  });

  content.addEventListener('keydown', function (e) {
    var tile = e.target.closest('.tile');
    if (tile && (e.key === 'Enter' || e.key === ' ')) {
      e.preventDefault();
      open(parseInt(tile.dataset.i, 10));
    }
  });

  $('crumbs').addEventListener('click', function (e) {
    var crumb = e.target.closest('.crumb');
    if (crumb) load(crumb.dataset.p || '');
  });

  $('search').addEventListener('input', function (e) {
    state.query = e.target.value;
    render();
  });

  $('viewToggle').addEventListener('click', function () {
    state.view = state.view === 'grid' ? 'list' : 'grid';
    render();
  });

  $('libSelect').addEventListener('change', function (e) {
    state.lib = e.target.value;
    load('');
  });

  // 图片查看器
  $('lbPrev').addEventListener('click', function () { lbStep(-1); });
  $('lbNext').addEventListener('click', function () { lbStep(1); });
  $('lbClose').addEventListener('click', closeLb);
  $('lbZoomIn').addEventListener('click', function () { setLbScale(lbScale + 0.25); });
  $('lbZoomOut').addEventListener('click', function () { setLbScale(lbScale - 0.25); });
  $('lightbox').addEventListener('click', function (e) {
    if (e.target === $('lightbox')) closeLb();
  });
  $('lbImg').addEventListener('wheel', function (e) {
    e.preventDefault();
    setLbScale(lbScale + (e.deltaY < 0 ? 0.2 : -0.2));
  }, { passive: false });

  // 图片手势：单指切换 / 放大后拖动，双指缩放
  $('lightbox').addEventListener('touchstart', function (e) {
    if (e.target.closest('button')) return;
    if (e.touches.length === 2) {
      var a = e.touches[0], b = e.touches[1];
      lbTouch = {
        mode: 'pinch',
        dist: lbDistance(a, b) || 1,
        scale: lbScale,
        x: lbX,
        y: lbY,
        mid: lbMid(a, b)
      };
      e.preventDefault();
    } else if (e.touches.length === 1) {
      var t = e.touches[0];
      lbTouch = {
        mode: lbScale > 1.001 ? 'pan' : 'swipe',
        startX: t.clientX,
        startY: t.clientY,
        x: lbX,
        y: lbY
      };
    } else {
      lbTouch = null;
    }
  }, { passive: false });

  $('lightbox').addEventListener('touchmove', function (e) {
    if (!lbTouch || e.touches.length === 0) return;
    if (lbTouch.mode === 'pinch') {
      if (e.touches.length < 2) return;
      var a = e.touches[0], b = e.touches[1];
      var mid = lbMid(a, b);
      lbScale = clamp(lbTouch.scale * (lbDistance(a, b) / lbTouch.dist), 1, 5);
      lbX = lbTouch.x + (mid.x - lbTouch.mid.x);
      lbY = lbTouch.y + (mid.y - lbTouch.mid.y);
      if (lbScale <= 1.001) {
        lbScale = 1;
        lbX = 0;
        lbY = 0;
      }
      clampLbOffset();
      applyLbTransform();
      e.preventDefault();
    } else if (lbTouch.mode === 'pan') {
      var t = e.touches[0];
      lbX = lbTouch.x + (t.clientX - lbTouch.startX);
      lbY = lbTouch.y + (t.clientY - lbTouch.startY);
      clampLbOffset();
      applyLbTransform();
      e.preventDefault();
    }
  }, { passive: false });

  function endLbTouch(e) {
    if (!lbTouch) return;
    var touch = lbTouch;
    if (touch.mode === 'swipe' && e.changedTouches.length) {
      var dx = e.changedTouches[0].clientX - touch.startX;
      if (Math.abs(dx) > 60 && lbScale <= 1.001) {
        lbStep(dx < 0 ? 1 : -1);
        lbTouch = null;
        return;
      }
    }
    if (touch.mode === 'pinch' && e.touches.length === 1) {
      var t = e.touches[0];
      lbTouch = {
        mode: lbScale > 1.001 ? 'pan' : 'swipe',
        startX: t.clientX,
        startY: t.clientY,
        x: lbX,
        y: lbY
      };
      return;
    }
    lbTouch = null;
  }

  $('lightbox').addEventListener('touchend', endLbTouch);
  $('lightbox').addEventListener('touchcancel', function () { lbTouch = null; });

  // 预览面板
  $('pvClose').addEventListener('click', closePreview);
  $('pvPrevFile').addEventListener('click', function () { pvStep(-1); });
  $('pvNextFile').addEventListener('click', function () { pvStep(1); });
  $('pvRaw').addEventListener('click', function () {
    if (pvItem) window.open(rawUrl(pvItem.path), '_blank');
  });
  $('pvDl').addEventListener('click', downloadCurrent);
  $('preview').addEventListener('click', function (e) {
    if (e.target === $('preview')) closePreview();
  });

  // 视频控制
  $('vClose').addEventListener('click', closePlayer);
  $('nvClose').addEventListener('click', closePlayer);
  $('vMode').addEventListener('click', cyclePlayerMode);
  $('nvMode').addEventListener('click', cyclePlayerMode);
  $('vfPrev').addEventListener('click', function () { vStep(-1); });
  $('vfNext').addEventListener('click', function () { vStep(1); });
  $('vfSwitch').addEventListener('click', togglePlaylist);
  $('vfList').addEventListener('click', togglePlaylist);
  $('plClose').addEventListener('click', closePlaylist);
  $('plBody').addEventListener('click', function (e) {
    var el = e.target.closest('.pl-item');
    if (el) playIndex(parseInt(el.dataset.i, 10));
  });
  $('vPlay').addEventListener('click', function () { video.paused ? video.play() : video.pause(); });
  $('vPrev').addEventListener('click', function () { vStep(-1); });
  $('vNext').addEventListener('click', function () { vStep(1); });
  $('vBack').addEventListener('click', function () { jump(-cfg.seekStep); });
  $('vFwd').addEventListener('click', function () { jump(cfg.seekStep); });
  $('vRate').addEventListener('change', function (e) { setPlaybackRate(e.target.value); });
  $('nvRate').addEventListener('change', function (e) { setPlaybackRate(e.target.value); });
  $('vMute').addEventListener('click', function () {
    video.muted = !video.muted;
    updateMute();
    showHint(video.muted ? '已静音' : '取消静音');
  });
  $('vVolDown').addEventListener('click', function () { changeVolume(-0.1); });
  $('vVolUp').addEventListener('click', function () { changeVolume(0.1); });
  $('vVol').addEventListener('input', function (e) {
    video.volume = parseFloat(e.target.value);
    video.muted = video.volume === 0;
    updateMute();
  });
  $('vSub').addEventListener('click', toggleSubtitle);
  $('nvSub').addEventListener('click', toggleSubtitle);
  $('vFull').addEventListener('click', toggleFullscreen);
  $('nvFull').addEventListener('click', toggleFullscreen);
  $('player').addEventListener('mousemove', autoHide);
  $('player').addEventListener('click', function (e) {
    if (isNativeMode()) return;
    autoHide();
    if (mouseLong) { mouseLong = false; return; }
    if (e.target === video) { video.paused ? video.play() : video.pause(); }
  });

  // 触屏手势：中部左右滑拖进度、右侧上下滑调音量、中央长按倍速
  playerEl.addEventListener('touchstart', function (e) {
    if (isNativeMode()) return;
    if (e.target.closest('.ctrl') || e.target.closest('.native-bar')) return;
    if (e.touches.length !== 1) { gesture = null; return; }
    autoHide();
    var t = e.touches[0];
    var box = playerEl.getBoundingClientRect();
    gesture = {
      x: t.clientX, y: t.clientY,
      base: video.currentTime || 0,
      vol: video.volume,
      zone: t.clientX - box.left > box.width * 0.72 ? 'right' : 'center',
      mode: null, moved: false, fast: false, lastApply: 0
    };
    clearTimeout(longPressTimer);
    longPressTimer = setTimeout(function () {
      if (gesture && !gesture.moved && gesture.zone === 'center') {
        gesture.fast = true;
        startFast();
      }
    }, 350);
  }, { passive: true });

  playerEl.addEventListener('touchmove', function (e) {
    if (isNativeMode() || !gesture || e.touches.length !== 1) return;
    var t = e.touches[0];
    var dx = t.clientX - gesture.x;
    var dy = t.clientY - gesture.y;
    var box = playerEl.getBoundingClientRect();
    if (!gesture.mode) {
      if (Math.abs(dx) < 10 && Math.abs(dy) < 10) return;
      if (Math.abs(dx) > Math.abs(dy)) {
        gesture.mode = 'seek';
      } else if (gesture.zone === 'right') {
        gesture.mode = 'vol';
      } else {
        clearTimeout(longPressTimer);
        gesture.moved = true;
        return;
      }
      clearTimeout(longPressTimer);
    }
    gesture.moved = true;
    if (gesture.mode === 'vol') {
      var v = clamp(gesture.vol - dy / (box.height * 0.7), 0, 1);
      setVolume(v);
      showHint('音量 ' + Math.round(v * 100) + '%');
    } else if (video.duration) {
      var target = clamp(gesture.base + dx / box.width * cfg.seekStep * 40, 0, video.duration - 0.3);
      updateSeekUi(target);
      var now = Date.now();
      if (now - gesture.lastApply > 120) { video.currentTime = target; gesture.lastApply = now; }
      var diff = Math.round(target - gesture.base);
      showHint(fmt(target) + ' / ' + fmt(video.duration) + '  ' + (diff >= 0 ? '+' : '') + diff + 's');
    }
    e.preventDefault();
  }, { passive: false });

  function endGesture() {
    clearTimeout(longPressTimer);
    if (!gesture) return;
    if (gesture.mode === 'seek' && video.duration) {
      video.currentTime = clamp(parseFloat($('seek').value) / 1000 * video.duration, 0, video.duration - 0.3);
    }
    if (gesture.fast) stopFast();
    gesture = null;
    autoHide();
  }

  playerEl.addEventListener('touchend', endGesture);
  playerEl.addEventListener('touchcancel', endGesture);

  // 桌面端：长按画面启用倍速
  video.addEventListener('mousedown', function (e) {
    if (e.button !== 0 || isNativeMode()) return;
    clearTimeout(longPressTimer);
    longPressTimer = setTimeout(function () { mouseLong = true; startFast(); }, 350);
  });
  window.addEventListener('mouseup', function () {
    clearTimeout(longPressTimer);
    if (mouseLong) { stopFast(); mouseLong = false; }
  });

  video.addEventListener('play', function () { $('vPlay').textContent = '⏸'; autoHide(); });
  video.addEventListener('pause', function () { $('vPlay').textContent = '▶'; $('ctrl').classList.remove('fade'); });
  video.addEventListener('waiting', function () { $('vSpinner').classList.remove('hidden'); });
  video.addEventListener('playing', function () { $('vSpinner').classList.add('hidden'); });
  video.addEventListener('ended', function () {
    if (!cfg.autoPlayNext) return;
    if (vIndex < vList.length - 1) {
      toast('即将播放：' + vList[vIndex + 1].name);
      vStep(1);
    } else if (vList.length > 1) {
      toast('已经是目录里的最后一个视频了');
    }
  });

  video.addEventListener('timeupdate', function () {
    if (!video.duration) return;
    $('seek').value = String(Math.round(video.currentTime / video.duration * 1000));
    $('vCur').textContent = fmt(video.currentTime);
    if (Math.floor(video.currentTime) % 5 === 0) saveProgress();
  });
  video.addEventListener('loadedmetadata', function () { $('vDur').textContent = fmt(video.duration); });
  $('seek').addEventListener('input', function (e) {
    if (!video.duration) return;
    video.currentTime = parseFloat(e.target.value) / 1000 * video.duration;
  });

  $('aClose').addEventListener('click', function () {
    $('audio').pause();
    $('audioBar').classList.add('hidden');
  });

  // 全局键盘（电视遥控器 / 桌面）
  document.addEventListener('keydown', function (e) {
    var lbOpen = !$('lightbox').classList.contains('hidden');
    var pOpen = !$('player').classList.contains('hidden');
    var pvOpen = !$('preview').classList.contains('hidden');

    if (e.key === 'Escape') {
      if (plOpen) closePlaylist();
      else if (pvOpen) closePreview();
      else if (pOpen) closePlayer();
      else if (lbOpen) closeLb();
      return;
    }
    if (pvOpen) {
      if (e.key === 'ArrowLeft') pvStep(-1);
      if (e.key === 'ArrowRight') pvStep(1);
      return;
    }
    if (lbOpen) {
      if (e.key === 'ArrowLeft') lbStep(-1);
      if (e.key === 'ArrowRight') lbStep(1);
      return;
    }
    if (pOpen) {
      if (e.key === ' ') { e.preventDefault(); video.paused ? video.play() : video.pause(); }
      if (e.key === 'ArrowLeft') { e.preventDefault(); jump(-cfg.seekStep); }
      if (e.key === 'ArrowRight') { e.preventDefault(); jump(cfg.seekStep); }
      if (e.key === 'ArrowUp') { e.preventDefault(); changeVolume(0.1); }
      if (e.key === 'ArrowDown') { e.preventDefault(); changeVolume(-0.1); }
      if (e.key === 'f' || e.key === 'F') $('vFull').click();
      if (e.key === 'm' || e.key === 'M') $('vMute').click();
      if (e.key === '[') vStep(-1);
      if (e.key === ']') vStep(1);
      if (e.key === 'l' || e.key === 'L') togglePlaylist();
    }
  });

  // 密码
  $('pinForm').addEventListener('submit', async function (e) {
    e.preventDefault();
    var pin = $('pinInput').value;
    var res = await fetch('/api/login', {
      method: 'POST',
      credentials: 'same-origin',
      body: JSON.stringify({ pin: pin })
    });
    if (res.ok) {
      $('pinModal').classList.add('hidden');
      boot();
    } else {
      $('pinErr').textContent = '密码不正确';
    }
  });

  window.addEventListener('hashchange', function () { applyHash(); });

  function applyHash() {
    var raw = location.hash.replace(/^#\/?/, '');
    if (!raw) return;
    var segs = raw.split('/').map(decodeURIComponent);
    var lib = segs[0];
    var path = segs.slice(1).join('/');
    // 由 load() 自身写回 hash 引起的回调无需重复加载
    if (lib === state.lib && path === state.path) return;
    state.lib = lib;
    $('libSelect').value = lib;
    load(path);
  }

  async function loadConfig() {
    try {
      var res = await fetch('/api/config', { credentials: 'same-origin' });
      if (res.ok) {
        var d = await res.json();
        if (d.seekStep) cfg.seekStep = clamp(parseInt(d.seekStep, 10) || 5, 1, 120);
        if (d.longPressRate) cfg.longPressRate = clamp(parseFloat(d.longPressRate) || 2, 1.25, 4);
        if (typeof d.autoPlayNext === 'boolean') cfg.autoPlayNext = d.autoPlayNext;
        if (typeof d.playerMode === 'string' && MODE_LABELS[d.playerMode]) cfg.playerMode = d.playerMode;
      }
    } catch (e) { /* 保持默认值 */ }
    applyConfig();
  }

  function applyConfig() {
    var back = $('vBack'), fwd = $('vFwd');
    back.textContent = '«' + cfg.seekStep + 's';
    fwd.textContent = cfg.seekStep + 's»';
    back.title = '快退 ' + cfg.seekStep + ' 秒（←）';
    fwd.title = '快进 ' + cfg.seekStep + ' 秒（→）';
    applyPlayerMode();
  }

  async function boot() {
    await loadConfig();
    var meta = await jget('/api/libraries');
    if (!meta) return;
    state.libs = meta.libraries || [];
    if (!state.libs.length) {
      content.innerHTML = '';
      $('empty').classList.remove('hidden');
      $('empty').textContent = '手机端还没有选择要共享的文件夹';
      return;
    }
    $('libSelect').innerHTML = state.libs.map(function (l) {
      return '<option value="' + esc(l.id) + '">' + esc(l.name) + '</option>';
    }).join('');

    var raw = location.hash.replace(/^#\/?/, '');
    var segs = raw ? raw.split('/').map(decodeURIComponent) : [];
    state.lib = segs[0] && state.libs.some(function (l) { return l.id === segs[0]; })
      ? segs[0] : state.libs[0].id;
    $('libSelect').value = state.lib;
    load(segs.slice(1).join('/'));
  }

  boot();
})();
