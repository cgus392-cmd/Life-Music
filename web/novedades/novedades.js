/* lifemusic.app/novedades (22-10-2026): todas las versiones publicadas, de la mas reciente a la primera.
   Las versiones vienen de la API publica de GitHub (se guardan 30 min en el navegador). Las que tienen ficha
   propia en /novedades/<version>.json (la misma que muestra la app al actualizar) salen con su imagen y sus
   funciones; las demas, con los titulos de sus notas y las notas completas plegadas. Sin analitica. */
(() => {
  "use strict";

  // Las versiones con ficha en esta carpeta. Al publicar una version con ficha nueva, se agrega aqui.
  const CON_FICHA = ["1.3.2", "1.3.1"];
  const RELEASES = "https://api.github.com/repos/cgus392-cmd/Life-Music/releases?per_page=100";
  const CACHE = "lm-novedades", CACHE_MS = 1800000;
  const lista = document.getElementById("versiones");
  const nodo = (tag, clase, texto) => { const e = document.createElement(tag); if (clase) e.className = clase; if (texto != null) e.textContent = texto; return e; };
  const megas = (b) => Math.round(b / 1048576) + " MB";

  // Un markdown pequeño y seguro: primero se escapa todo; despues titulos, listas, negritas, codigo y enlaces https.
  function markdown(texto) {
    const esc = (s) => s.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;");
    const enLinea = (s) => esc(s)
      .replace(/\*\*(.+?)\*\*/g, "<strong>$1</strong>")
      .replace(/`([^`]+)`/g, "<code>$1</code>")
      .replace(/\[([^\]]+)\]\((https:\/\/[^)\s]+)\)/g, '<a href="$2" rel="noopener">$1</a>');
    let html = "", items = null, parrafo = [];
    const cerrarParrafo = () => { if (parrafo.length) { html += "<p>" + enLinea(parrafo.join(" ")) + "</p>"; parrafo = []; } };
    const cerrarLista = () => { if (items) { html += "<ul>" + items.map((i) => "<li>" + enLinea(i) + "</li>").join("") + "</ul>"; items = null; } };
    for (const crudo of texto.replace(/\r/g, "").split("\n")) {
      const linea = crudo.trimEnd();
      if (/^\s*\|/.test(linea) || /^\s*-{3,}\s*$/.test(linea)) continue; // tablas y rayas: fuera
      const titulo = linea.match(/^#{1,6}\s+(.*)$/);
      const viñeta = linea.match(/^\s{0,1}[-*]\s+(.*)$/);
      if (titulo) { cerrarParrafo(); cerrarLista(); html += "<h4>" + enLinea(titulo[1]) + "</h4>"; }
      else if (viñeta) { cerrarParrafo(); (items = items || []).push(viñeta[1]); }
      else if (!linea.trim()) { cerrarParrafo(); cerrarLista(); }
      else if (items && /^\s{2,}/.test(linea)) items[items.length - 1] += " " + linea.trim();
      else { cerrarLista(); parrafo.push(linea.replace(/^>\s?/, "").trim()); }
    }
    cerrarParrafo(); cerrarLista();
    return html;
  }
  // Los titulos de lo que trajo: las viñetas que empiezan en negrita.
  function titulos(texto) {
    const t = [];
    for (const m of (texto || "").matchAll(/^\s?[-*]\s+\*\*(.+?)\*\*/gm)) t.push(m[1].replace(/[.:]\s*$/, ""));
    return t.slice(0, 8);
  }

  function pintar(versiones, fichas) {
    lista.textContent = "";
    versiones.forEach((v, k) => {
      const f = fichas[v.version];
      const li = nodo("li", "version");
      li.id = "v" + v.version;
      const cuando = nodo("div", "cuando");
      cuando.appendChild(nodo("span", "tc", v.version));
      cuando.appendChild(nodo("span", "fecha", new Date(v.fecha).toLocaleDateString("es-CO", { day: "numeric", month: "long", year: "numeric" }) + (k === 0 ? " · la más reciente" : "")));
      li.appendChild(cuando);
      const der = nodo("div");
      if (f && f.es) {
        der.appendChild(nodo("p", "resumen-v", f.es.resumen));
        if (f.imagen) {
          const fig = nodo("figure"), img = nodo("img");
          img.src = "/novedades/" + v.version + ".webp";
          img.alt = "Lo nuevo en Life Music " + v.version;
          img.loading = "lazy";
          img.width = 1280; img.height = 720;
          fig.appendChild(img);
          der.appendChild(fig);
        }
        const ul = nodo("ul", "funciones-v");
        for (const x of f.es.funciones || []) {
          const i = nodo("li");
          i.appendChild(nodo("b", null, x.titulo));
          i.appendChild(nodo("span", null, x.texto));
          ul.appendChild(i);
        }
        der.appendChild(ul);
        if (f.es.arreglos && f.es.arreglos.length) {
          const d = nodo("details", "arreglos");
          d.appendChild(nodo("summary", null, "Arreglos (" + f.es.arreglos.length + ")"));
          const ul2 = nodo("ul");
          f.es.arreglos.forEach((a) => ul2.appendChild(nodo("li", null, a)));
          d.appendChild(ul2);
          der.appendChild(d);
        }
      } else {
        const t = titulos(v.notas);
        if (t.length) {
          const ul = nodo("ul", "funciones-v");
          t.forEach((x) => { const i = nodo("li"); i.appendChild(nodo("b", null, x)); ul.appendChild(i); });
          der.appendChild(ul);
        } else der.appendChild(nodo("p", "resumen-v", v.nombre || "Life Music " + v.version));
      }
      if (v.notas) {
        const d = nodo("details", "arreglos");
        d.appendChild(nodo("summary", null, "Las notas completas"));
        const div = nodo("div", "notas-md");
        div.innerHTML = markdown(v.notas); // ya escapado dentro de markdown()
        d.appendChild(div);
        der.appendChild(d);
      }
      const bajar = nodo("div", "bajar");
      if (v.apk) {
        const a = nodo("a", "boton chico", k === 0 ? "Descargar la " + v.version : "Bajar la " + v.version);
        a.href = k === 0 ? "/apk" : v.apk;
        bajar.appendChild(a);
        if (v.peso) bajar.appendChild(nodo("span", "fecha", "Para Android 8 o superior, " + megas(v.peso)));
      }
      const gh = nodo("a", null, "Verla en GitHub");
      gh.href = v.url;
      gh.rel = "noopener";
      bajar.appendChild(gh);
      der.appendChild(bajar);
      li.appendChild(der);
      lista.appendChild(li);
    });
    // Si llegaron con #v1.2.0, se va a esa version cuando ya existe.
    if (location.hash) { const el = document.getElementById(location.hash.slice(1)); if (el) el.scrollIntoView(); }
  }

  function leerCache() {
    try { const c = JSON.parse(localStorage.getItem(CACHE) || "null"); return c && Date.now() - c.hora < CACHE_MS ? c.versiones : null; } catch { return null; }
  }
  async function versiones() {
    const guardadas = leerCache();
    if (guardadas) return guardadas;
    const r = await fetch(RELEASES, { headers: { Accept: "application/vnd.github+json" } });
    if (!r.ok) throw new Error("github");
    const todas = await r.json();
    const lasDeLaApp = todas
      .filter((x) => !x.draft && !x.prerelease && /^v\d/.test(x.tag_name))
      .sort((a, b) => Date.parse(b.published_at) - Date.parse(a.published_at))
      .map((x) => {
        const apk = (x.assets || []).find((a) => a.name === "lifemusic.apk");
        return { version: x.tag_name.replace(/^v/, ""), nombre: x.name, fecha: x.published_at, notas: x.body || "", url: x.html_url, apk: apk ? apk.browser_download_url : null, peso: apk ? apk.size : 0 };
      });
    try { localStorage.setItem(CACHE, JSON.stringify({ hora: Date.now(), versiones: lasDeLaApp })); } catch {}
    return lasDeLaApp;
  }
  Promise.all([
    versiones(),
    Promise.all(CON_FICHA.map((v) => fetch("/novedades/" + v + ".json").then((r) => (r.ok ? r.json() : null)).catch(() => null)))
      .then((js) => Object.fromEntries(js.filter(Boolean).map((j) => [j.version, j]))),
  ])
    .then(([vs, fichas]) => pintar(vs, fichas))
    .catch(() => {
      lista.textContent = "";
      const li = nodo("li", "cargando-v");
      li.append("GitHub no respondió ahora. Las versiones también están en ");
      const a = nodo("a", null, "GitHub");
      a.href = "https://github.com/cgus392-cmd/Life-Music/releases";
      li.appendChild(a);
      li.append(".");
      lista.appendChild(li);
    })
    // Sin aria-live en la lista: un lector de pantalla leeria todas las notas de golpe. Solo se avisa que ya cargo.
    .finally(() => lista.setAttribute("aria-busy", "false"));
})();
