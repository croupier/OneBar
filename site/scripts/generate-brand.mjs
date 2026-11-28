import { mkdirSync } from "node:fs";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import sharp from "sharp";

const root = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const repo = resolve(root, "..");
const assets = join(root, "assets");
const icon = join(repo, "brand", "png", "icon-512.png");

mkdirSync(assets, { recursive: true });

for (const size of [96, 180, 192, 512]) {
  await sharp(icon).resize(size, size, { fit: "contain", background: "#0b5d31" }).png().toFile(join(assets, `icon-${size}.png`));
}

const svg = (width, height, body) => `<svg xmlns="http://www.w3.org/2000/svg" width="${width}" height="${height}" viewBox="0 0 ${width} ${height}">${body}</svg>`;
const iconData = `data:image/png;base64,${(await sharp(icon).resize(220, 220).png().toBuffer()).toString("base64")}`;

await sharp(Buffer.from(svg(1200, 630, `
  <defs>
    <radialGradient id="glow"><stop stop-color="#c9f56a" stop-opacity=".22"/><stop offset="1" stop-color="#c9f56a" stop-opacity="0"/></radialGradient>
    <pattern id="grid" width="48" height="48" patternUnits="userSpaceOnUse"><path d="M48 0H0V48" fill="none" stroke="#c9f56a" stroke-opacity=".08"/></pattern>
  </defs>
  <rect width="1200" height="630" fill="#0c1712"/>
  <rect width="1200" height="630" fill="url(#grid)"/>
  <circle cx="1010" cy="80" r="360" fill="url(#glow)"/>
  <rect x="70" y="74" width="1060" height="482" rx="42" fill="#123c2b" stroke="#c9f56a" stroke-opacity=".18"/>
  <image href="${iconData}" x="126" y="204" width="220" height="220"/>
  <text x="392" y="285" fill="#fffef8" font-family="Arial, sans-serif" font-size="108" font-weight="700">OneBar</text>
  <text x="396" y="356" fill="#c9f56a" font-family="Arial, sans-serif" font-size="38" font-weight="700">Один поиск. Всё нужное.</text>
  <text x="396" y="410" fill="#ffffff" fill-opacity=".58" font-family="Arial, sans-serif" font-size="25">Приложения · Контакты · Веб-поиск</text>
  <text x="126" y="505" fill="#ffffff" fill-opacity=".48" font-family="Arial, sans-serif" font-size="21">Открытый код · Без рекламы · Бесплатно</text>
`))).png().toFile(join(assets, "og-image.png"));

await sharp(Buffer.from(svg(1024, 500, `
  <defs><radialGradient id="glow"><stop stop-color="#c9f56a" stop-opacity=".2"/><stop offset="1" stop-color="#c9f56a" stop-opacity="0"/></radialGradient></defs>
  <rect width="1024" height="500" fill="#0c1712"/>
  <circle cx="930" cy="20" r="350" fill="url(#glow)"/>
  <image href="${iconData}" x="70" y="140" width="220" height="220"/>
  <text x="340" y="235" fill="#fffef8" font-family="Arial, sans-serif" font-size="88" font-weight="700">OneBar</text>
  <text x="344" y="300" fill="#c9f56a" font-family="Arial, sans-serif" font-size="32" font-weight="700">Один поиск. Всё нужное.</text>
  <text x="344" y="350" fill="#ffffff" fill-opacity=".55" font-family="Arial, sans-serif" font-size="22">Приложения · Контакты · Веб-поиск</text>
`))).png().toFile(join(assets, "feature-graphic.png"));

console.log("Brand assets generated.");
