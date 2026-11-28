const menuButton = document.querySelector("[data-menu-button]");
const mobileMenu = document.querySelector("[data-mobile-menu]");

const closeMenu = () => {
  if (!menuButton || !mobileMenu) return;
  menuButton.setAttribute("aria-expanded", "false");
  mobileMenu.classList.add("hidden");
};

menuButton?.addEventListener("click", () => {
  const expanded = menuButton.getAttribute("aria-expanded") === "true";
  menuButton.setAttribute("aria-expanded", String(!expanded));
  mobileMenu?.classList.toggle("hidden", expanded);
});

mobileMenu?.querySelectorAll("a").forEach((link) => link.addEventListener("click", closeMenu));
document.addEventListener("keydown", (event) => {
  if (event.key === "Escape") closeMenu();
});

const demoInput = document.querySelector("[data-demo-input]");
const demoItems = [...document.querySelectorAll("[data-demo-item]")];
const demoSections = [...document.querySelectorAll("[data-demo-section]")];
const demoEmpty = document.querySelector("[data-demo-empty]");
const demoCount = document.querySelector("[data-demo-count]");

const normalize = (value) => value.toLocaleLowerCase("ru").replaceAll("ё", "е").trim();

demoInput?.addEventListener("input", () => {
  const query = normalize(demoInput.value);
  let visible = 0;

  for (const item of demoItems) {
    const matches = !query || normalize(item.dataset.demoItem || "").includes(query);
    item.classList.toggle("hidden", !matches);
    if (matches) visible += 1;
  }

  for (const section of demoSections) {
    const hasVisibleItem = [...section.querySelectorAll("[data-demo-item]")]
      .some((item) => !item.classList.contains("hidden"));
    section.classList.toggle("hidden", !hasVisibleItem);
  }

  if (demoEmpty) demoEmpty.classList.toggle("hidden", visible !== 0);
  if (demoCount) demoCount.textContent = String(visible);
});

demoInput?.addEventListener("change", () => {
  window.onebarTrack?.("demo_search", { results: Number(demoCount?.textContent || 0) });
});

const revealItems = document.querySelectorAll(".reveal");
if ("IntersectionObserver" in window && !window.matchMedia("(prefers-reduced-motion: reduce)").matches) {
  const observer = new IntersectionObserver((entries) => {
    for (const entry of entries) {
      if (!entry.isIntersecting) continue;
      entry.target.classList.add("is-visible");
      observer.unobserve(entry.target);
    }
  }, { threshold: 0.12 });
  revealItems.forEach((item) => observer.observe(item));
} else {
  revealItems.forEach((item) => item.classList.add("is-visible"));
}

document.querySelectorAll("[data-year]").forEach((element) => {
  element.textContent = String(new Date().getFullYear());
});
