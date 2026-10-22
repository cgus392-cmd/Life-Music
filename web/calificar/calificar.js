/* lifemusic.app/calificar (22-10-2026). De una a cinco notas, con nombre, correo opcional y comentario.
   Va al servidor de la comunidad (el mismo de las estrellas de la app). Lo publico sale solo si CG lo aprueba
   desde la Sala de control. Sin analitica. En desarrollo, ?api=http://localhost:8787 apunta a un wrangler dev. */
(() => {
  "use strict";

  const $ = (id) => document.getElementById(id);
  const local = /^(localhost|127\.0\.0\.1)$/.test(location.hostname);
  const pedida = new URLSearchParams(location.search).get("api");
  const API = local && pedida && /^http:\/\/(localhost|127\.0\.0\.1):\d+$/.test(pedida) ? pedida : "https://lifemusic-comunidad.cho--usic.workers.dev";
  const reducido = !!(window.matchMedia && matchMedia("(prefers-reduced-motion: reduce)").matches);
  const DICE = ["Elige de una a cinco notas.", "No me gustó.", "Le falta.", "Está bien.", "Me gusta.", "¡Me encanta!"];
  const fmtNum = (n) => n.toLocaleString("es-CO");
  const nodo = (tag, clase, texto) => { const e = document.createElement(tag); if (clase) e.className = clase; if (texto != null) e.textContent = texto; return e; };

  // ── Las cinco notas ───────────────────────────────────────────────────────
  const labels = [...document.querySelectorAll("#fila-notas label")];
  const radios = [...document.querySelectorAll("#fila-notas input")];
  let elegidas = 0;
  function encender(n) {
    labels.forEach((l, i) => l.classList.toggle("encendida", i < n));
    $("notas-dice").textContent = DICE[n] || DICE[0];
  }
  radios.forEach((r, i) => {
    r.addEventListener("change", () => {
      elegidas = i + 1;
      encender(elegidas);
      if (!reducido) labels.slice(0, elegidas).forEach((l, k) => {
        l.classList.remove("salta");
        setTimeout(() => { l.classList.add("salta"); setTimeout(() => l.classList.remove("salta"), 500); }, k * 60);
      });
    });
    labels[i].addEventListener("pointerenter", (e) => { if (e.pointerType !== "touch") encender(i + 1); });
  });
  $("fila-notas").addEventListener("pointerleave", () => encender(elegidas));

  const comentario = $("comentario");
  comentario.addEventListener("input", () => { $("comentario-cuenta").textContent = comentario.value.length + " de 500"; });

  // ── Enviar ────────────────────────────────────────────────────────────────
  const abierta = Date.now();
  const ERRORES = {
    notas: "Elige de una a cinco notas.",
    nombre: "Escribe tu nombre (dos letras o más).",
    correo: "Ese correo no parece válido. Puedes dejarlo vacío.",
    comentario: "El comentario es muy largo o tiene caracteres raros.",
    rapido: "Espera un momentico y vuelve a enviarla.",
    muchas: "Ya enviaste varias en la última hora. Prueba más tarde.",
  };
  const resultado = $("resultado");
  function decir(texto, mal) { resultado.textContent = texto; resultado.classList.toggle("mal", !!mal); }
  $("form-resena").addEventListener("submit", async (e) => {
    e.preventDefault();
    const nombre = $("nombre"), correo = $("correo");
    nombre.removeAttribute("aria-invalid"); correo.removeAttribute("aria-invalid");
    if (!elegidas) { decir(ERRORES.notas, true); radios[0].focus(); return; }
    if (nombre.value.trim().length < 2) { nombre.setAttribute("aria-invalid", "true"); decir(ERRORES.nombre, true); nombre.focus(); return; }
    if (correo.value.trim() && !correo.checkValidity()) { correo.setAttribute("aria-invalid", "true"); decir(ERRORES.correo, true); correo.focus(); return; }
    const boton = $("enviar");
    boton.disabled = true;
    decir("Enviando…");
    try {
      const r = await fetch(API + "/resenas", {
        method: "POST",
        headers: { "content-type": "application/json" },
        body: JSON.stringify({
          notas: elegidas, nombre: nombre.value.trim(), correo: correo.value.trim(), comentario: comentario.value.trim(),
          espera: Date.now() - abierta, sitio: $("sitio").value,
        }),
      });
      const j = await r.json().catch(() => ({}));
      if (!r.ok || !j.ok) {
        if (j.error === "nombre") nombre.setAttribute("aria-invalid", "true");
        if (j.error === "correo") correo.setAttribute("aria-invalid", "true");
        decir(ERRORES[j.error] || "No se pudo enviar. Prueba otra vez en un rato.", true);
        boton.disabled = false;
        return;
      }
      gracias(nombre.value.trim().split(/\s+/)[0]);
    } catch {
      decir("No se pudo enviar. Revisa tu conexión y prueba otra vez.", true);
      boton.disabled = false;
    }
  });
  function gracias(nombre) {
    const t = $("tarjeta-form");
    t.textContent = "";
    const g = nodo("div", "gracias");
    g.setAttribute("role", "status");
    g.appendChild(nodo("h2", null, "¡Gracias, " + nombre + "!"));
    g.appendChild(nodo("p", null, "Tu calificación ya llegó. CG la lee y, si la aprueba, sale en esta página con tu nombre."));
    const a = nodo("a", "boton chico", "Volver al inicio");
    a.href = "/";
    g.appendChild(a);
    t.appendChild(g);
    g.tabIndex = -1;
    g.focus();
  }

  // ── Lo que dicen ──────────────────────────────────────────────────────────
  function notasChicas(n) {
    const s = nodo("span", "notas-chicas");
    s.setAttribute("role", "img");
    s.setAttribute("aria-label", n + " de 5 notas");
    for (let k = 1; k <= 5; k++) {
      s.insertAdjacentHTML("beforeend", '<svg viewBox="0 0 20 16" aria-hidden="true"' + (k <= Math.round(n) ? ' class="llena"' : "") +
        '><ellipse cx="10" cy="8" rx="7.5" ry="5.5" transform="rotate(-22 10 8)"/></svg>');
    }
    return s;
  }
  function pintarPromedio(r) {
    const caja = $("promedio");
    caja.textContent = "";
    // El promedio real desde el primer voto (decision de CG, 22-10). Sin votos, una invitacion.
    r = r || { total: 0 };
    if (!r.total || !r.promedio) {
      caja.appendChild(nodo("p", "sin-promedio", "Todavía no hay calificaciones. La tuya puede ser la primera."));
      return;
    }
    const prom = r.promedio;
    const div = nodo("div", "promedio");
    const grande = nodo("div", "grande", prom.toFixed(1).replace(".", ","));
    const small = nodo("small");
    small.appendChild(notasChicas(prom));
    if (r.total) small.append(" " + fmtNum(r.total) + (r.total === 1 ? " calificación" : " calificaciones") + " entre la app y la web");
    grande.appendChild(small);
    const lista = nodo("ul", "reparto");
    lista.setAttribute("aria-label", "Cuántas calificaciones hay de cada nota");
    for (let k = 5; k >= 1; k--) {
      const n = (r.porEstrella && r.porEstrella[k]) || 0;
      const li = nodo("li");
      li.appendChild(nodo("span", null, String(k)));
      const barra = nodo("span", "barra-r"), i = nodo("i");
      barra.appendChild(i);
      li.appendChild(barra);
      li.appendChild(nodo("span", null, fmtNum(n)));
      li.setAttribute("aria-label", k + (k === 1 ? " nota: " : " notas: ") + n);
      lista.appendChild(li);
      requestAnimationFrame(() => requestAnimationFrame(() => { i.style.width = (r.total ? (n / r.total) * 100 : 0).toFixed(1) + "%"; }));
    }
    div.appendChild(grande);
    div.appendChild(lista);
    caja.appendChild(div);
  }
  function pintarResenas(lista) {
    const ul = $("lista-resenas");
    ul.textContent = "";
    $("lista-vacia").hidden = lista.length > 0;
    for (const x of lista) {
      const li = nodo("li");
      const quien = nodo("div", "quien");
      quien.appendChild(nodo("b", null, x.nombre));
      quien.appendChild(notasChicas(x.notas));
      const t = nodo("time", null, new Date(x.fecha + "T12:00:00").toLocaleDateString("es-CO", { day: "numeric", month: "long", year: "numeric" }));
      t.dateTime = x.fecha;
      quien.appendChild(t);
      li.appendChild(quien);
      if (x.comentario) li.appendChild(nodo("p", null, x.comentario));
      ul.appendChild(li);
    }
  }
  fetch(API + "/resenas")
    .then((r) => (r.ok ? r.json() : null))
    .then((j) => {
      if (!j) throw new Error();
      pintarPromedio(j.resumen);
      pintarResenas(j.resenas || []);
    })
    .catch(() => {
      // Mientras el servidor nuevo no este publicado, al menos el promedio de la app.
      fetch(API + "/calificaciones/resumen").then((r) => (r.ok ? r.json() : null)).then(pintarPromedio).catch(() => pintarPromedio(null));
      pintarResenas([]);
    });
  fetch("https://api.github.com/repos/cgus392-cmd/Life-Music", { headers: { Accept: "application/vnd.github+json" } })
    .then((r) => (r.ok ? r.json() : null))
    .then((j) => {
      if (!j || typeof j.stargazers_count !== "number") return;
      const n = j.stargazers_count;
      $("github-texto").textContent = "El código de Life Music es abierto y ya tiene " + fmtNum(n) + (n === 1 ? " estrella" : " estrellas") +
        ". Una más ayuda a que otra gente la encuentre.";
    })
    .catch(() => {});
})();
