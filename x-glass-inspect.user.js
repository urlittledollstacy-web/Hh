// ==UserScript==
// @name         X Glass Inspector
// @namespace    https://github.com/urlittledollstacy-web/Hh
// @version      1.0.0
// @description  Captures the x.com bottom bar element + computed CSS + backdrop-filter rules, as copyable text
// @author       OpenHands (AI agent) for urlittledollstacy-web
// @match        https://x.com/*
// @match        https://twitter.com/*
// @run-at       document-idle
// @grant        none
// ==/UserScript==

/*
 * Tap the small "glass" button that appears bottom-left.
 * A report opens: element markup, computed styles, ancestors, every
 * backdrop-filter rule in the stylesheets, and the --x-* CSS variables.
 * Copy it and send it back — that is everything needed to port the effect.
 */

(function () {
  'use strict';

  const PROPS = [
    'position', 'top', 'bottom', 'left', 'right', 'z-index',
    'width', 'height', 'padding', 'margin',
    'background-color', 'background-image',
    'backdrop-filter', '-webkit-backdrop-filter',
    'filter', 'opacity', 'mix-blend-mode', 'isolation',
    'border', 'border-radius', 'box-shadow', 'outline',
    'transform', 'will-change', 'contain', 'overflow', 'color-scheme',
  ];

  function path(el) {
    if (!el || el.nodeType !== 1) return '?';
    const parts = [];
    let cur = el;
    let guard = 0;
    while (cur && cur.nodeType === 1 && guard++ < 6) {
      let s = cur.tagName.toLowerCase();
      if (cur.id) s += '#' + cur.id;
      const cls = (cur.getAttribute('class') || '').trim().split(/\s+/).filter(Boolean).slice(0, 4);
      if (cls.length) s += '.' + cls.join('.');
      for (const a of Array.from(cur.attributes || [])) {
        if (a.name.startsWith('data-') && a.value && a.value.length < 30) s += '[' + a.name + '=' + a.value + ']';
      }
      parts.unshift(s);
      cur = cur.parentElement;
    }
    return parts.join(' > ');
  }

  function rect(el) {
    const r = el.getBoundingClientRect();
    return Math.round(r.left) + ',' + Math.round(r.top) + ' ' +
      Math.round(r.width) + 'x' + Math.round(r.height);
  }

  function styles(el) {
    const cs = getComputedStyle(el);
    const out = [];
    for (const p of PROPS) {
      let v;
      try { v = cs.getPropertyValue(p); } catch (e) { continue; }
      if (!v) continue;
      if (v === 'none' || v === 'normal' || v === 'auto' || v === '0px' || v === 'visible') continue;
      out.push(p + ': ' + v);
    }
    return out;
  }

  function cssRulesForBackdrop() {
    const hits = [];
    for (const sheet of Array.from(document.styleSheets)) {
      let rules;
      try { rules = sheet.cssRules; } catch (e) { continue; }
      if (!rules) continue;
      const walk = (list, depth) => {
        if (depth > 4) return;
        for (const r of Array.from(list)) {
          if (r.cssRules) { walk(r.cssRules, depth + 1); continue; }
          const txt = r.cssText || '';
          if (txt.indexOf('backdrop-filter') === -1) continue;
          hits.push(txt.length > 400 ? txt.slice(0, 400) + '…' : txt);
          if (hits.length > 40) return;
        }
      };
      walk(rules, 0);
    }
    return hits;
  }

  function cssVariables() {
    const vars = new Map();
    for (const sheet of Array.from(document.styleSheets)) {
      let rules;
      try { rules = sheet.cssRules; } catch (e) { continue; }
      if (!rules) continue;
      for (const r of Array.from(rules)) {
        if (!r.style) continue;
        const sel = r.selectorText || '';
        if (sel !== ':root' && sel !== 'html' && sel !== 'body' && sel.indexOf(':root') === -1) continue;
        for (const p of Array.from(r.style)) {
          if (p.startsWith('--')) vars.set(p, r.style.getPropertyValue(p).trim());
        }
      }
    }
    return vars;
  }

  function elementBlock(el, index) {
    const lines = [];
    lines.push('### [' + index + '] ' + path(el));
    lines.push('rect: ' + rect(el));
    lines.push('styles:');
    for (const s of styles(el)) lines.push('  ' + s);
    const html = el.outerHTML.replace(/\s+/g, ' ');
    lines.push('markup: ' + (html.length > 900 ? html.slice(0, 900) + '…' : html));
    const anc = [];
    let cur = el.parentElement;
    let i = 0;
    while (cur && i++ < 5) {
      const s = styles(cur);
      if (s.length) anc.push('  ^ ' + path(cur) + '\n' + s.map(function (x) { return '      ' + x; }).join('\n'));
      cur = cur.parentElement;
    }
    if (anc.length) lines.push('ancestors:', anc.join('\n'));
    return lines.join('\n');
  }

  function report() {
    const out = [];
    out.push('# X Glass Inspector');
    out.push('url: ' + location.href);
    out.push('viewport: ' + window.innerWidth + 'x' + window.innerHeight +
      ' dpr=' + (window.devicePixelRatio || 1));
    out.push('scheme: ' + (getComputedStyle(document.documentElement).colorScheme || '?') +
      ' prefers-dark=' + (matchMedia('(prefers-color-scheme: dark)').matches));
    out.push('ua: ' + navigator.userAgent);
    out.push('');

    const cands = [];
    const add = (el) => { if (el && cands.indexOf(el) === -1) cands.push(el); };
    document.querySelectorAll('[data-bottom-bar]').forEach(add);
    document.querySelectorAll('[data-top-chrome]').forEach(add);
    document.querySelectorAll('[class*="backdrop-blur"]').forEach(add);
    document.querySelectorAll('[class*="backdrop-"]').forEach(add);

    out.push('## candidates: ' + cands.length);
    out.push('');
    cands.slice(0, 12).forEach(function (el, i) { out.push(elementBlock(el, i)); out.push(''); });

    const rules = cssRulesForBackdrop();
    out.push('## stylesheet rules with backdrop-filter: ' + rules.length);
    for (const r of rules) out.push('  ' + r);
    out.push('');

    const vars = cssVariables();
    out.push('## css variables on :root: ' + vars.size);
    for (const [k, v] of vars) out.push('  ' + k + ': ' + v);

    return out.join('\n');
  }

  function overlay(text) {
    const wrap = document.createElement('div');
    wrap.style.cssText = 'position:fixed;inset:0;z-index:2147483647;background:rgba(0,0,0,.85);' +
      'display:flex;flex-direction:column;padding:12px;gap:8px;font:12px/1.35 monospace;color:#eee;';
    const ta = document.createElement('textarea');
    ta.value = text;
    ta.style.cssText = 'flex:1;width:100%;background:#111;color:#ddd;border:1px solid #444;' +
      'border-radius:8px;padding:8px;font:11px/1.35 monospace;';
    const row = document.createElement('div');
    row.style.cssText = 'display:flex;gap:8px;';
    const mk = (label, fn) => {
      const b = document.createElement('button');
      b.textContent = label;
      b.style.cssText = 'flex:1;padding:12px;border-radius:8px;border:0;background:#2f6feb;color:#fff;font:14px sans-serif;';
      b.onclick = fn;
      return b;
    };
    row.appendChild(mk('Copy', () => {
      ta.select();
      try { document.execCommand('copy'); } catch (e) {}
      if (navigator.clipboard) navigator.clipboard.writeText(ta.value).catch(function () {});
      row.firstChild.textContent = 'Copied';
    }));
    row.appendChild(mk('Download', () => {
      const blob = new Blob([ta.value], { type: 'text/plain' });
      const a = document.createElement('a');
      a.href = URL.createObjectURL(blob);
      a.download = 'x-glass-report.txt';
      a.click();
    }));
    row.appendChild(mk('Close', () => wrap.remove()));
    wrap.appendChild(ta);
    wrap.appendChild(row);
    document.body.appendChild(wrap);
  }

  function button() {
    const b = document.createElement('button');
    b.textContent = 'glass';
    b.style.cssText = 'position:fixed;left:8px;bottom:96px;z-index:2147483646;' +
      'padding:10px 12px;border-radius:999px;border:1px solid rgba(255,255,255,.25);' +
      'background:rgba(0,0,0,.6);color:#fff;font:12px sans-serif;opacity:.55;';
    b.onclick = () => { b.style.opacity = '1'; try { overlay(report()); } catch (e) { alert('failed: ' + e); } };
    document.body.appendChild(b);
  }

  window.__xglass = report;
  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', button);
  } else {
    button();
  }
})();
