# Revisión de lifemusic.app · 22-10-2026

Tarea programada que pidió CG el 22-10 a la 1 a. m.: revisar qué le falta a la página, arreglar lo pequeño,
hacer pruebas de seguridad y de calidad, y esperar su «publica». **No se publicó nada**: ni deploy, ni push,
ni tag, y no se tocaron las bases remotas.

## Lo que no se pudo hacer

- **Las pruebas en el navegador no se hicieron.** La vista previa «sala-web» no arrancó: el permiso salió
  denegado en esta corrida automática y no lo reintenté. Por eso **no probé** a Nova, la música, la mezcla, los
  temas del TV, el contador, el QR ni la consola. Tampoco hice capturas a 375 y 1280 px, ni en claro, oscuro o con
  movimiento reducido. Hay que hacer esa pasada con la vista previa antes de publicar (lista abajo).
- En su lugar probé el servidor de la comunidad sin servidor: un banco de pruebas en node con una D1 falsa
  sobre SQLite (`scratchpad/prueba-comunidad.mjs` de esta sesión). Lo demás fue revisión del código.

## Lo que se revisó

| Área | Cómo | Resultado |
|---|---|---|
| Sintaxis | `node --check` de los 10 .js de web/, comunidad/ y equipo/ | Todos bien |
| Pruebas del equipo | `node --test equipo/test/logica.test.mjs` | 16 de 16 bien. Ojo: `node --test equipo/test/` (la carpeta) falla en Windows; hay que darle el archivo |
| Worker de la comunidad | 15 casos contra D1 falsa | 15 de 15 bien: reseña válida, trampa para robots (responde que sí y no guarda), «muy rápido», notas fuera de rango, nombre corto o con control, correo malo, comentario largo, cuerpo de más de 4 KB, **freno de 3 por hora** (la cuarta da 429 y otra conexión sí entra), origen ajeno da 403, las escrituras de la app no aceptan Origin, el GET no muestra pendientes ni correos, la tabla `limites` no guarda la IP en claro, marcas invisibles en el nombre y borrado de huellas viejas |
| XSS | Todos los `innerHTML` de las páginas | Sin riesgo. Las novedades escapan todo antes del markdown y solo enlazan `https:`. La Sala escapa todo con su plantilla `h`. Calificar, estado y la portada usan `textContent` o SVG fijo |
| Correos | Rutas públicas y Sala | El correo nunca sale en público. En la Sala, `/sala` tiene la guarda `if (!duenio)`: solo CG ve los correos |
| Secretos | Búsqueda de claves, tokens, `.jks`, `.dev.vars` y `google-services.json` en lo versionado | Nada. `.wrangler/` y `keystore.properties` están en `.gitignore` |
| Enlaces | Guion sobre todos los `href` y `src` de las 8 páginas, con `_redirects` | Sin enlaces ni anclas rotas |
| Contraste (WCAG 1.4.3 y 1.4.11) | Cálculo con la fórmula de WCAG | Gris sobre hueso 5,9:1; gris oscuro 7,5:1. El «tenue» (3,2:1) solo se usa en piezas que piden 3:1 (bordes de las notas, números grandes de los pasos) o en el botón apagado del carro (exento) |
| Patrones prohibidos | Búsqueda de punticos, viñetas redondas y animaciones que titilan | Nada. Las animaciones que hay son el vinilo que gira, el salto de las notas y la respiración de Nova |
| SEO | Títulos, descripción, canonical, og, sitemap y robots | Todo en su sitio. `og.jpg` sigue siendo la imagen vieja (ya se sabía) |

## Lo que se arregló (commits locales, sin subir)

| Commit | Arreglo |
|---|---|
| `7bc9c7f9` | Las cinco notas caben en teléfonos de 320 px: cada nota se encoge hasta 44 px |
| `14d670e2` | «Versión ligera para arm64» y «Todas las novedades» miden 44 px de alto |
| `d627801f` | /novedades usaba `aria-live` en toda la lista: el lector de pantalla leía todas las versiones de golpe. Ahora usa `aria-busy` |
| `a5fdfec3` | Todas las páginas piden la misma versión de los estilos (`landing.css?v=7`, `paginas.css?v=3`); las de adentro pedían `?v=4` |
| `097b1e13` | **Cabeceras de seguridad** en `/`, `/califica`, `/calificar`, `/novedades`, `/estado` y `/privacidad`: CSP estricta (solo lo propio, más GitHub, la comunidad y el relevo del TV), sin marcos, `Permissions-Policy` y HSTS en todo el sitio. `/cast`, `/tv` y `/concurso` quedan como estaban |
| `92935541` | Los nombres no aceptan marcas invisibles de dirección (U+202E y parecidas), que voltean el texto en la Sala |
| `4df170e8` | La huella del freno se borra a las 24 horas aunque nadie vuelva a calificar. Antes solo se limpiaba con la siguiente reseña y la política prometía otra cosa |
| `dc6cc859` | La política decía que GitHub se guarda 10 minutos; /novedades lo guarda 30. Ahora dice las dos cosas (web y PRIVACY_POLICY.md) |

Nota sobre la CSP: además de proteger, **bloquea la analítica de Cloudflare si se inyecta sola** (la memoria
dice que el 10-10 se inyectaba en todo el sitio). Así «sin analítica» se cumple de verdad en esas páginas. Lo
mejor es apagarla también en el panel: Pages → lifemusic → Web Analytics.

## Lo que falta (sin decisión de diseño)

- **La pasada en el navegador** (ver «Antes de publicar»).
- **El bucle de animación no se duerme con el movimiento normal.** En `landing.js` (línea ~655) el comentario
  dice que se duerme cuando todo está quieto, pero la condición solo deja dormir con movimiento reducido. Con
  movimiento normal dibuja a 60 cuadros por segundo mientras la pestaña esté abierta, y eso gasta batería en el
  teléfono. No lo toqué porque Nova parpadea y respira. Propuesta: dormir cuando Nova y las notas estén fuera de
  la pantalla, o bajar a 30 cuadros cuando no suena nada.
- **Las tres fotos del TV se precargan al abrir la página** (unos 190 KB), aunque la persona nunca baje hasta el
  Puente. Se podrían pedir cuando el Puente se acerque.
- **El freno cuenta y luego escribe, en dos pasos.** Diez envíos al mismo tiempo podrían pasar de 3. Es raro y las
  reseñas igual esperan la aprobación de CG.
- **La comunidad acepta el origen `localhost` en producción.** El riesgo es bajo (solo un navegador en ese mismo
  equipo lo manda), pero se podría limitar a `wrangler dev`.
- **En el teléfono, las páginas de adentro esconden su menú** (`.nav-paginas` con `display:none`). Queda el pie con
  todos los enlaces. ¿Está bien así?
- **La foto del receptor aún muestra el ícono viejo** en la esquina (ya se sabía).

## Lo que necesita a CG (decisiones)

1. **/concurso y /concurso/bases tienen el diseño viejo.** Además cargan las letras de Google Fonts, y la política
   dice que lifemusic.app sirve sus fuentes desde aquí mismo. Con el concurso en pausa: ¿se rediseñan, se
   cambian por un aviso de pausa con el diseño nuevo o se sacan del mapa del sitio?
2. **/tv carga el SDK de Cast desde gstatic.com (Google)**, y la política no lo menciona en «Life Music TV». ¿Se
   agrega una línea?
3. **El promedio con piso de 4,2 y el contador desde 7.000** son decisión tuya y están como pediste. Solo un aviso
   con cariño: en /calificar, las barras de reparto muestran los votos reales. Si un día el promedio real baja de
   4,2, el número grande y las barras no van a cuadrar, y alguien lo puede notar. El Estatuto del Consumidor
   (Ley 1480) pide información veraz. ¿Lo dejamos así, o el número grande dice el promedio real?
4. **Robots que mandan reseñas sin pasar por el navegador:** el freno es por conexión. Si un día llegan muchas
   pendientes, Cloudflare Turnstile es gratis y no tiene cookies de rastreo. ¿Lo dejamos para cuando haga falta?
5. **og.jpg** sigue siendo la imagen vieja (hoja verde). ¿Se le pide una nueva a «Diseño y marca»?

## Antes de publicar: la pasada en el navegador

Con «sala-web» (8766) y «comunidad-api» (8795), abrir `http://localhost:8766/?api=http://localhost:8795`:

- 375 px y 1280 px, claro y oscuro, y con movimiento reducido.
- Nova aparece, habla y acompaña al bajar; la música y la mezcla con las carátulas.
- Los temas del TV, el contador (+1 al tocar descargar; se puede cancelar la descarga) y el QR en el escritorio.
- El botón del carro, apagado.
- Calificar rápido: enviar, los errores y la cuarta en la hora («muchas»).
- /calificar con `?api=`, /novedades, /estado y /privacidad.
- La consola sin errores. La CSP no aplica en local: revisar la consola otra vez en `lifemusic.pages.dev`
  justo después de publicar.

## Pasos para publicar (solo con el «publica» de CG, en este orden)

Desde `Echo-Music/`:

1. `npx wrangler d1 execute lifemusic-comunidad --remote --file comunidad/schema.sql` (crea `resenas` y
   `limites`; el resto usa `if not exists` y `insert or ignore`).
2. `cd comunidad && npx wrangler deploy` (la comunidad con las reseñas y los arreglos de hoy).
3. `cd equipo && npx wrangler deploy` (la Sala con el bloque «Reseñas de la web» y el binding COMUNIDAD).
4. `npx wrangler pages deploy web --project-name lifemusic --branch main --commit-dirty=true`.
5. Comprobar en `https://lifemusic.app` la portada, /califica, /calificar (enviar una de prueba y verla en la
   Sala), /novedades, /estado, /privacidad, /tv y /equipo, con la consola abierta.

El push a GitHub es aparte. Ojo: `origin/main..HEAD` ya tiene más de 60 commits, incluidos los del carro.
