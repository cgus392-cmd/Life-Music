/* lifemusic.app/estado (1.3.2; diseño nuevo 22-10-2026). Cada servicio tiene su /salud y se consulta en vivo
   desde este navegador: sin monitores de pago ni datos guardados. El estado se dice con palabras, con la cara de
   Nova y con una linea de pulso; nunca con un punto de color (regla de CG, memoria nada-de-patrones-ia). */
(() => {
  "use strict";

  const SERVICIOS = [
    { nombre: "TV con navegador", detalle: "El relevo que enlaza el teléfono con el TV", url: "https://lifemusic-enlace.cho--usic.workers.dev/salud", ok: (j) => j && j.estado === "ok" },
    { nombre: "Comunidad", detalle: "Calificaciones, reseñas y prerregistro", url: "https://lifemusic-comunidad.cho--usic.workers.dev/salud", ok: (j) => j && j.estado === "ok" },
    { nombre: "Descargas", detalle: "La última versión en GitHub", url: "https://api.github.com/repos/cgus392-cmd/Life-Music/releases/latest", ok: (j) => j && !!j.tag_name },
    { nombre: "Web", detalle: "lifemusic.app y la pantalla del TV", url: "/novedades/1.3.2.json", ok: (j) => !!j },
  ];
  const LENTO_MS = 1500;
  const $ = (id) => document.getElementById(id);
  const PULSO = "M0 20 H58 L66 7 L76 33 L84 20 H100 L104 15 L108 20 H160";
  const PLANO = "M0 20 H160";

  function fila(s) {
    const li = document.createElement("li");
    li.className = "servicio";
    li.innerHTML = '<div><h3></h3><p></p></div><svg class="pulso-s" viewBox="0 0 160 40" aria-hidden="true"><path pathLength="1"/></svg><p class="lectura"><b>…</b><span>revisando</span></p>';
    li.querySelector("h3").textContent = s.nombre;
    li.querySelector("div p").textContent = s.detalle;
    return li;
  }
  function trazar(li, d) {
    const p = li.querySelector("path");
    p.setAttribute("d", d);
    p.animate ? p.animate([{ strokeDasharray: "1", strokeDashoffset: "1" }, { strokeDasharray: "1", strokeDashoffset: "0" }],
      { duration: matchMedia("(prefers-reduced-motion: reduce)").matches ? 0 : 900, easing: "cubic-bezier(.2,.8,.2,1)" }) : null;
  }
  function veredicto(estado, malos) {
    const nova = $("nova-estado");
    nova.className = "nova-estado " + estado;
    const textos = {
      ok: ["Todo funciona sin problemas.", "Los cuatro servicios respondieron rápido."],
      lento: ["Todo funciona, aunque algo va lento.", "Responde, pero se demora más de lo normal. Puede ser tu conexión."],
      mal: [malos === 1 ? "Un servicio no responde ahora." : malos + " servicios no responden ahora.", "Se vuelve a revisar solo en un minuto. La música en tu teléfono sigue sonando."],
    }[estado];
    $("veredicto").textContent = textos[0];
    $("veredicto-detalle").textContent = textos[1];
  }
  function revisar() {
    const lista = $("servicios");
    lista.textContent = "";
    $("nova-estado").className = "nova-estado revisando";
    $("veredicto").textContent = "Revisando los servicios…";
    $("veredicto-detalle").textContent = "Tu navegador le pregunta a cada uno si está en línea.";
    let listos = 0, malos = 0, lentos = 0;
    SERVICIOS.forEach((s) => {
      const li = fila(s);
      lista.appendChild(li);
      const t0 = performance.now();
      const control = new AbortController();
      const limite = setTimeout(() => control.abort(), 8000);
      fetch(s.url, { cache: "no-store", signal: control.signal })
        .then((r) => (r.ok ? r.json() : null))
        .catch(() => null)
        .then((j) => {
          clearTimeout(limite);
          const ms = Math.round(performance.now() - t0);
          const bien = s.ok(j), lento = bien && ms > LENTO_MS;
          li.classList.add(bien ? "ok" : "mal");
          li.querySelector(".lectura b").textContent = bien ? ms + " ms" : "Sin respuesta";
          li.querySelector(".lectura span").textContent = !bien ? "no responde" : lento ? "en línea, lento" : "en línea";
          trazar(li, bien ? PULSO : PLANO);
          if (!bien) malos++; else if (lento) lentos++;
          if (++listos === SERVICIOS.length) veredicto(malos ? "mal" : lentos ? "lento" : "ok", malos);
        });
    });
    $("hora").textContent = new Date().toLocaleTimeString("es-CO", { hour: "numeric", minute: "2-digit", second: "2-digit" });
  }
  $("otra").addEventListener("click", revisar);
  revisar();
  setInterval(() => { if (!document.hidden) revisar(); }, 60000);
})();
