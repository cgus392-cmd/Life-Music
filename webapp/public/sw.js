/* Guarda la carcasa de Life Music Web para que abra al instante y sin red.
   Las respuestas de /api y todo lo de YouTube van siempre a la red. */
const CAJA = "lm-web-1";
const CARCASA = ["/", "/app.css", "/app.js", "/manifest.webmanifest", "/img/favicon.png", "/img/apple-touch-icon.png", "/img/icono-192.png"];

self.addEventListener("install", (e) => {
  e.waitUntil(caches.open(CAJA).then((c) => c.addAll(CARCASA)).then(() => self.skipWaiting()));
});

self.addEventListener("activate", (e) => {
  e.waitUntil(
    caches.keys().then((ks) => Promise.all(ks.filter((k) => k !== CAJA).map((k) => caches.delete(k)))).then(() => self.clients.claim()),
  );
});

// Primero la red (para estrenar cambios enseguida); si no hay, lo guardado.
self.addEventListener("fetch", (e) => {
  const url = new URL(e.request.url);
  if (e.request.method !== "GET" || url.origin !== location.origin || url.pathname.startsWith("/api/")) return;
  e.respondWith(
    fetch(e.request)
      .then((r) => {
        if (r.ok) {
          const copia = r.clone();
          caches.open(CAJA).then((c) => c.put(e.request.mode === "navigate" ? "/" : e.request, copia));
        }
        return r;
      })
      .catch(() => caches.match(e.request.mode === "navigate" ? "/" : e.request)),
  );
});
