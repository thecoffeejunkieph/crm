// Inlines the compiled mail CSS into mail/*.html and writes the Thymeleaf templates HtmlMail sends.
// Email clients drop <style>, CSS variables and logical properties, so all three are resolved here.
import juice from 'juice';
import { readFileSync, readdirSync, writeFileSync } from 'node:fs';

const OUT = '../src/main/resources/templates/mail';
const css = readFileSync('build/mail.css', 'utf8');

// Defaults for Tailwind's --tw-* helpers come from their @property rules.
const defaults = Object.fromEntries(
  [...css.matchAll(/@property (--[\w-]+)\{[^}]*?initial-value:([^;}]+)/g)].map(m => [m[1], m[2].trim()]));

const LOGICAL = {
  'padding-inline': ['padding-left', 'padding-right'], 'padding-block': ['padding-top', 'padding-bottom'],
  'margin-inline': ['margin-left', 'margin-right'], 'margin-block': ['margin-top', 'margin-bottom'],
};

function resolve(value, vars) {
  // Innermost var() first, so nested fallbacks collapse one level per pass.
  for (let prev; prev !== value;) {
    prev = value;
    value = value.replace(/var\((--[\w-]+)(?:,([^()]*))?\)/, (_, name, fallback) => {
      const v = vars[name] ?? defaults[name];
      return v !== undefined && v !== 'initial' ? v : (fallback ?? '').trim();
    });
  }
  return value;
}

function fixStyle(style) {
  const decls = style.split(';').map(d => d.trim()).filter(Boolean).map(d => {
    const i = d.indexOf(':');
    return [d.slice(0, i).trim(), d.slice(i + 1).trim()];
  });
  const vars = Object.fromEntries(decls.filter(([p]) => p.startsWith('--')));
  return decls.filter(([p]) => !p.startsWith('--')).flatMap(([p, v]) => {
    v = resolve(v, vars);
    if (!LOGICAL[p]) return [[p, v]];
    const [a, b = a] = v.split(/\s+/);
    return [[LOGICAL[p][0], a], [LOGICAL[p][1], b]];
  }).map(([p, v]) => `${p}: ${v}`).join('; ');
}

for (const file of readdirSync('mail').filter(f => f.endsWith('.html'))) {
  const html = juice.inlineContent(readFileSync(`mail/${file}`, 'utf8'), css, {
    applyWidthAttributes: true, applyHeightAttributes: false, xmlMode: false, removeStyleTags: true,
  }).replace(/ class="[^"]*"/g, '')
    .replace(/style="([^"]*)"/g, (_, s) => `style="${fixStyle(s.replaceAll('&quot;', '"')).replaceAll('"', '&quot;')}"`);
  writeFileSync(`${OUT}/${file}`,
    `<!--/* Generated from tailwind/mail/${file} by \`npm run build\` in tailwind/. Edit that file, not this one. */-->\n${html}`);
}
