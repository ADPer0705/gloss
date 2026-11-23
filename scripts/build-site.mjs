import { execFileSync } from "node:child_process";
import fs from "node:fs";
import path from "node:path";

const rootDir = process.cwd();
const siteDir = path.join(rootDir, "site");
const openwikiDir = path.join(rootDir, "openwiki");
const distDir = path.join(rootDir, "dist-site");
const wikiOutDir = path.join(distDir, "wiki");

function assert(condition, message) {
  if (!condition) {
    throw new Error(message);
  }
}

console.log("Preparing dist-site/...");
fs.rmSync(distDir, { recursive: true, force: true });
fs.cpSync(siteDir, distDir, { recursive: true });

console.log("Exporting OpenWiki static visualizer to dist-site/wiki/...");
execFileSync(
  "npx",
  ["--yes", "openwiki@0.9.0", "visualize", openwikiDir, "--export", wikiOutDir],
  { stdio: "inherit" }
);

const wikiIndexPath = path.join(wikiOutDir, "index.html");
let wikiHtml = fs.readFileSync(wikiIndexPath, "utf8");

assert(
  wikiHtml.includes("<title>OpenWiki visualizer</title>"),
  "Expected OpenWiki <title> placeholder in exported wiki/index.html"
);
wikiHtml = wikiHtml.replace(
  "<title>OpenWiki visualizer</title>",
  '<title>Gloss Wiki — Interactive Architecture & Docs</title>\n<link rel="icon" type="image/png" href="../assets/favicon.png" />'
);

const brandSvgRegex = /<svg class="lc-logo-full"[\s\S]*?<\/svg>/;
assert(
  brandSvgRegex.test(wikiHtml),
  "Expected LangChain logo SVG inside OpenWiki topbar"
);
wikiHtml = wikiHtml.replace(
  brandSvgRegex,
  '<img class="gloss-wiki-icon" src="../assets/favicon.png" alt="Gloss" width="24" height="24" />'
);

const oldTitleTag = '<div class="title">OpenWiki<small id="wiki-name">wiki visualizer</small></div>';
assert(
  wikiHtml.includes(oldTitleTag),
  "Expected #wiki-name title tag in exported wiki/index.html"
);
wikiHtml = wikiHtml.replace(
  oldTitleTag,
  '<div class="title">Gloss<small id="wiki-name">OpenWiki · Architecture Docs</small></div>'
);

const spacerTag = '<div class="spacer"></div>';
assert(
  wikiHtml.includes(spacerTag),
  "Expected topbar .spacer element in exported wiki/index.html"
);
wikiHtml = wikiHtml.replace(
  spacerTag,
  '<a class="gloss-home-link" href="../">&larr; gloss.dearanayji.rocks</a>\n  <div class="spacer"></div>'
);

fs.writeFileSync(wikiIndexPath, wikiHtml, "utf8");

const wikiCssPath = path.join(wikiOutDir, "styles.css");
const glossWikiOverrides = `
/* Gloss 60-30-10 Theme Alignment */
:root {
  --lc-dark: #0b0e14;
  --lc-card: #131823;
  --lc-surface: #f7f8fc;
  --lc-border-dark: #222c40;
  --lc-border: #dde2ee;
  --lc-muted: #94a0b8;
  --lc-body: #d8dfec;
  --lc-white: #f2f4f8;
  --lc-dark-text: #0f172a;
  --lc-blue: #7c73ff;
  --lc-blue-hover: #928aff;
  --lc-blue-bg: rgba(86, 76, 230, 0.1);
  --bg: #0b0e14;
  --panel: #131823;
  --edge: #222c40;
  --text: #d8dfec;
  --heading: #f2f4f8;
  --muted: #94a0b8;
  --tag-bg: rgba(124, 115, 255, 0.14);
  --tag-text: #a59eff;
  --graph-bg: #090c12;
  --node-label: #94a0b8;
  --code-bg: #171e2d;
}

[data-theme="light"] {
  --bg: #f7f8fc;
  --panel: #ffffff;
  --edge: #dde2ee;
  --text: #364154;
  --heading: #0f172a;
  --muted: #556075;
  --lc-blue: #564ce6;
  --lc-blue-hover: #4338ca;
  --tag-bg: rgba(86, 76, 230, 0.1);
  --tag-text: #564ce6;
  --graph-bg: #eef1f8;
  --node-label: #364154;
  --code-bg: #eef1f8;
}

.gloss-wiki-icon {
  border-radius: 6px;
  display: block;
}

.gloss-home-link {
  display: inline-flex;
  align-items: center;
  padding: 5px 11px;
  border-radius: 6px;
  border: 1px solid var(--edge);
  background: var(--panel);
  color: var(--heading);
  font-size: 12px;
  font-weight: 500;
  text-decoration: none;
  transition: border-color 0.16s ease, color 0.16s ease;
}

.gloss-home-link:hover {
  border-color: var(--lc-blue);
  color: var(--lc-blue);
  text-decoration: none;
}

@media (max-width: 680px) {
  .gloss-home-link {
    display: none;
  }
}
`;
fs.appendFileSync(wikiCssPath, glossWikiOverrides, "utf8");

const requiredOutputs = [
  "index.html",
  "styles.css",
  "app.js",
  "CNAME",
  ".nojekyll",
  "assets/gloss-icon.png",
  "assets/favicon.png",
  "wiki/index.html",
  "wiki/graph.json",
  "wiki/styles.css"
];

for (const rel of requiredOutputs) {
  const target = path.join(distDir, rel);
  assert(fs.existsSync(target), `Missing required build artifact: ${rel}`);
}

console.log("Successfully built dist-site/ with Gloss landing page + themed OpenWiki docs.");
