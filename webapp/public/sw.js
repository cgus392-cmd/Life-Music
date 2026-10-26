// Service worker de Life Music Web: guarda el armazón de la app para que abra rápido y pueda mostrar
// «Sin conexión». Nunca guarda /api (lo que dice YouTube Music cambia) ni nada de YouTube.
const CACHE = "lifemusic-v1";
const ARMAZON = ["/", "/manifest.webmanifest", "/icono-192.png", "/icono-512.png", "/favicon.svg", "/marca/cglabs_lockup_negro.webp"];

self.addEventListener("install", (e) => {
  e.waitUntil(caches.open(CACHE).then((c) => c.addAll(ARMAZON)).then(() => self.skipWaiting()));
});

self.addEventListener("activate", (e) => {
  e.waitUntil(
    caches
      .keys()
      .then((ks) => Promise.all(ks.filter((k) => k !== CACHE).map((k) => caches.delete(k))))
      .then(() => self.clients.claim()),
  );
});

self.addEventListener("fetch", (e) => {
  const url = new URL(e.request.url);
  if (e.request.method !== "GET" || url.origin !== location.origin || url.pathname.startsWith("/api/")) return;

  // Las páginas: primero la red; sin red, el armazón guardado.
  if (e.request.mode === "navigate") {
    e.respondWith(fetch(e.request).catch(() => caches.match("/")));
    return;
  }

  // Los archivos de la app (llevan su huella en el nombre): primero lo guardado.
  if (url.pathname.startsWith("/assets/") || ARMAZON.includes(url.pathname)) {
    e.respondWith(
      caches.match(e.request).then(
        (guardado) =>
          guardado ||
          fetch(e.request).then((r) => {
            const copia = r.clone();
            caches.open(CACHE).then((c) => c.put(e.request, copia));
            return r;
          }),
      ),
    );
  }
});
