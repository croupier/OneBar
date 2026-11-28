import { execSync } from "node:child_process";
import { existsSync, readFileSync, readdirSync } from "node:fs";
import { dirname, extname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const root = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const pages = ["index.html", "install.html", "privacy.html"];

for (const file of ["dist/index.html", "dist/install.html", "dist/privacy.html", "dist/assets/styles.css"]) {
  if (!existsSync(join(root, file))) {
    console.error(`Missing dist asset: ${file}`);
    process.exit(1);
  }
}
if (existsSync(join(root, "dist", "node_modules"))) {
  console.error("dist must not contain node_modules");
  process.exit(1);
}

execSync('npx --no-install html-validate ' + pages.join(" "), { cwd: root, stdio: "inherit" });

const missing = [];
for (const page of pages) {
  const html = readFileSync(join(root, page), "utf8");
  for (const match of html.matchAll(/(?:src|href)="(\.\/[^"#?]+)"/g)) {
    const target = match[1].split(/[?#]/)[0];
    if (!existsSync(join(root, target))) missing.push(`${page}: ${target}`);
  }
}

for (const font of [
  "onest-cyrillic-wght-normal.woff2",
  "onest-latin-wght-normal.woff2",
  "unbounded-cyrillic-wght-normal.woff2",
  "unbounded-latin-wght-normal.woff2",
]) {
  if (!existsSync(join(root, "assets", "fonts", font))) missing.push(`font: ${font}`);
}

if (missing.length) {
  console.error("Missing local assets:\n" + missing.join("\n"));
  process.exit(1);
}

const walk = (directory) => readdirSync(directory, { withFileTypes: true }).flatMap((entry) => {
  const path = join(directory, entry.name);
  return entry.isDirectory() ? walk(path) : [path];
});
const sourceFiles = walk(root).filter((path) =>
  !path.includes(`${join(root, "node_modules")}`) &&
  !path.includes(`${join(root, ".tmp")}`) &&
  [".html", ".css", ".js", ".mjs", ".json", ".xml", ".txt", ".webmanifest"].includes(extname(path)),
);
for (const path of sourceFiles) {
  const content = readFileSync(path, "utf8");
  if (content && !content.endsWith("\n")) {
    console.error(`Missing trailing newline: ${path}`);
    process.exit(1);
  }
}

console.log(`Checked ${pages.length} pages and ${sourceFiles.length} text files.`);
