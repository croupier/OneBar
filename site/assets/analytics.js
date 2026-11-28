const UMAMI_WEBSITE_ID = "";
const UMAMI_HOST = "https://cloud.umami.is";
const STORAGE_KEY = "onebar-site-analytics";
const banner = document.querySelector("[data-analytics-banner]");

const track = (name, data = {}) => {
  if (typeof window.umami?.track === "function") {
    window.umami.track(name, data);
  }
};
window.onebarTrack = track;

const loadTracker = () => {
  if (!UMAMI_WEBSITE_ID || document.querySelector("[data-umami-script]")) return;
  const script = document.createElement("script");
  script.async = true;
  script.src = `${UMAMI_HOST}/script.js`;
  script.dataset.websiteId = UMAMI_WEBSITE_ID;
  script.dataset.hostUrl = UMAMI_HOST;
  script.dataset.umamiScript = "";
  document.head.appendChild(script);
};

const showBanner = () => banner?.classList.remove("hidden");
const hideBanner = () => banner?.classList.add("hidden");

const initialize = () => {
  const doNotTrack = navigator.doNotTrack === "1" || navigator.globalPrivacyControl === true;
  if (!UMAMI_WEBSITE_ID || doNotTrack) {
    hideBanner();
    return;
  }

  const consent = localStorage.getItem(STORAGE_KEY);
  if (consent === "accepted") {
    loadTracker();
  } else {
    showBanner();
  }
};

document.querySelector("[data-analytics-accept]")?.addEventListener("click", () => {
  localStorage.setItem(STORAGE_KEY, "accepted");
  hideBanner();
  loadTracker();
});

document.querySelector("[data-analytics-decline]")?.addEventListener("click", () => {
  localStorage.setItem(STORAGE_KEY, "declined");
  hideBanner();
});

document.querySelector("[data-analytics-settings]")?.addEventListener("click", () => {
  localStorage.removeItem(STORAGE_KEY);
  window.location.reload();
});

document.querySelectorAll("[data-analytics-event]").forEach((element) => {
  element.addEventListener("click", () => {
    track(element.dataset.analyticsEvent || "click");
  });
});

initialize();
