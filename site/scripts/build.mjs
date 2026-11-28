import { execSync } from "node:child_process";
import { createHash } from "node:crypto";
import { cpSync, existsSync, mkdirSync, readdirSync, readFileSync, rmSync, writeFileSync } from "node:fs";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const root = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const assets = join(root, "assets");
const dist = join(root, "dist");
const fonts = join(assets, "fonts");

mkdirSync(fonts, { recursive: true });
rmSync(join(assets, "styles.css"), { force: true });
rmSync(dist, { recursive: true, force: true });

execSync(
  'npx --no-install @tailwindcss/cli -i ./src/input.css -o ./assets/styles.css --minify',
  { cwd: root, stdio: "inherit" },
);

for (const [packageName, prefix] of [
  ["@fontsource-variable/onest", "onest"],
  ["@fontsource-variable/unbounded", "unbounded"],
]) {
  const source = join(root, "node_modules", packageName, "files");
  if (!existsSync(source)) {
    throw new Error(`Font package not found: ${packageName}`);
  }
  for (const file of readdirSync(source)) {
    if (file.endsWith(".woff2") && (file.includes("cyrillic") || file.includes("latin"))) {
      cpSync(join(source, file), join(fonts, file));
    }
  }
  cpSync(join(root, "node_modules", packageName, "LICENSE"), join(fonts, `${prefix}-OFL.txt`));
}

const stylesPath = join(assets, "styles.css");
const styles = readFileSync(stylesPath, "utf8");
if (!styles.endsWith("\n")) writeFileSync(stylesPath, `${styles}\n`);
const stylesVersion = createHash("sha256").update(styles).digest("hex").slice(0, 12);

writeFileSync(join(root, ".nojekyll"), "");
mkdirSync(dist, { recursive: true });
for (const file of [
  "index.html",
  "install.html",
  "privacy.html",
  "manifest.webmanifest",
  "robots.txt",
  "sitemap.xml",
  ".nojekyll",
]) {
  if (file.endsWith(".html")) {
    const html = readFileSync(join(root, file), "utf8");
    writeFileSync(
      join(dist, file),
      html.replaceAll("./assets/styles.css", `./assets/styles.css?v=${stylesVersion}`),
    );
  } else {
    cpSync(join(root, file), join(dist, file));
  }
}
cpSync(assets, join(dist, "assets"), { recursive: true });
console.log("Site built into site/dist/");
