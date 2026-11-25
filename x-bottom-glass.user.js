// ==UserScript==
// @name         X — Bottom Bar Glass
// @namespace    https://github.com/urlittledollstacy-web/Hh
// @version      1.0.0
// @description  Frosted-glass blur on the x.com bottom navigation bar, live tunable
// @author       OpenHands (AI agent) for urlittledollstacy-web
// @match        https://x.com/*
// @match        https://twitter.com/*
// @run-at       document-idle
// @grant        none
// ==/UserScript==

/*
 * The site already ships `backdrop-blur-md` (12 px) on the top header. This
 * script applies the same recipe to the bottom bar, with a stronger blur and a
 * translucent tint computed from the current theme.
 *
 * Tuning without editing the file: open the console and run
 *   __xbar.set({ blur: 28, alphaDark: 0.5 })
 */

(function () {
  'use strict';

  const CFG = {
    blur: 20,          // px of backdrop blur
    saturate: 180,     // % saturation boost
    alphaDark: 0.58,   // tint opacity, dark theme (lower = glassier)
    alphaLight: 0.72,  // tint opacity, light theme
    hairline: true,    // 1px separator along the top edge
    radius: 0,         // top corner radius in px, 0 = square
    extra: '',         // any extra CSS, injected last
  };

  const STYLE_ID = 'x-bottom-glass';
  const SEL = '[data-bottom-bar]';
  let styleEl = null;

  function rgbOf(el, fallback) {
    try {
      const c = getComputedStyle(el).backgroundColor || '';
      const m = c.match(/rgba?\(([^)]+)\)/);
      if (!m) return fallback;
      const p = m[1].split(',').map(function (s) { return parseFloat(s); });
      if (p.length < 3 || isNaN(p[0])) return fallback;
      return [p[0], p[1], p[2]];
    } catch (e) {
      return fallback;
    }
  }

  function isDark() {
    const cs = (getComputedStyle(document.documentElement).colorScheme || '') + ' ' +
               (getComputedStyle(document.body).colorScheme || '');
    if (cs.indexOf('dark') !== -1) return true;
    if (cs.indexOf('light') !== -1) return false;
    try { return matchMedia('(prefers-color-scheme: dark)').matches; } catch (e) { return true; }
  }

  function build() {
    const dark = isDark();
    const bg = rgbOf(document.body, dark ? [0, 0, 0] : [255, 255, 255]);
    const a = dark ? CFG.alphaDark : CFG.alphaLight;
    const tint = 'rgba(' + bg[0] + ',' + bg[1] + ',' + bg[2] + ',' + a + ')';
    const line = dark ? 'rgba(255,255,255,0.10)' : 'rgba(0,0,0,0.08)';
    const radius = CFG.radius > 0 ? 'border-top-left-radius:' + CFG.radius + 'px !important;' +
                                    'border-top-right-radius:' + CFG.radius + 'px !important;' : '';

    return SEL + ' {' +
      'backdrop-filter: blur(' + CFG.blur + 'px) saturate(' + CFG.saturate + '%) !important;' +
      '-webkit-backdrop-filter: blur(' + CFG.blur + 'px) saturate(' + CFG.saturate + '%) !important;' +
      'background-color: ' + tint + ' !important;' +
      radius +
      (CFG.hairline ? 'box-shadow: inset 0 1px 0 ' + line + ' !important;' : '') +
      'transition: background-color .2s ease;' +
      '}' +
      (CFG.extra || '');
  }

  function apply() {
    if (!styleEl || !styleEl.isConnected) {
      styleEl = document.getElementById(STYLE_ID) || document.createElement('style');
      styleEl.id = STYLE_ID;
      (document.head || document.documentElement).appendChild(styleEl);
    }
    styleEl.textContent = build();
  }

  apply();

  // theme flips and SPA re-renders
  try {
    new MutationObserver(apply).observe(document.documentElement, {
      attributes: true,
      attributeFilter: ['class', 'style', 'data-theme', 'data-color-mode'],
    });
    new MutationObserver(apply).observe(document.body, { attributes: true, attributeFilter: ['class', 'style'] });
  } catch (e) { /* ignore */ }
  try {
    matchMedia('(prefers-color-scheme: dark)').addEventListener('change', apply);
  } catch (e) { /* ignore */ }

  // live tuning: __xbar.set({ blur: 28, alphaDark: 0.45 })
  window.__xbar = {
    cfg: CFG,
    set: function (patch) {
      if (patch) {
        for (const k in patch) {
          if (Object.prototype.hasOwnProperty.call(CFG, k)) CFG[k] = patch[k];
        }
      }
      apply();
      return JSON.parse(JSON.stringify(CFG));
    },
    find: function () {
      const el = document.querySelector(SEL);
      return el ? el.outerHTML.slice(0, 400) : 'no [data-bottom-bar] found';
    },
  };
})();
