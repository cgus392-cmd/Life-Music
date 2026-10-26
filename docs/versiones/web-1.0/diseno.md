# Plan de diseño · Life Music Web 1.0

**Fase:** brief de diseño · **Estado:** aprobado por CG el 24-10 (madrugada) y enviado a Diseño y marca para las tres
láminas · **Fecha:** 24-10-2026
**Hace la maqueta:** la sesión «Life Music · Diseño y marca» · **La programa:** la sesión «Life Music · iPhone»
**Va con:** [`plan.md`](plan.md), el plan técnico.

## 1. El principio

> **Es una app, no una página.** Instalada desde Safari («Agregar a inicio»), en el iPhone tiene que sentirse como
> una app nativa: abre a pantalla completa, sin barras del navegador, con su ícono, su pantalla de arranque, su barra
> de pestañas y un «Ahora suena» que sube y baja con el dedo. En el computador se instala igual desde Chrome o Edge
> y se ve como un reproductor de escritorio.

Todo lo que se diseñe se mide con una pregunta: **¿esto se vería así en una app nativa de iPhone?** Si parece una web
(enlaces subrayados, menús de hover, desplazamiento de página entera, barras de cookies), no va.

## 2. Lo que no se negocia

- **Nunca se ve YouTube.** El reproductor de YouTube está escondido. Lo que se ve es nuestra carátula, nuestros
  controles y nuestra cola. La única excepción es un anuncio de YouTube (sección 5.9).
- **Identidad Pulso:**
  - **Letra:** Geist y Geist Mono.
  - **Colores:** hueso #F5F5F3, humo #696966 y carbón #111111.
  - **Acento:** el único es el pulso, la cabeza de la nota del logo, en coral #FF6B3D. Puede tomar el color de la
    canción que suena.
  - **Logo:** «La nota es el pulso» (`Brand/logo/2026-10-22-nota-pulso/`). La hoja verde ya no se usa.
- **Nada de patrones de IA:** prohibidos el puntico de color delante de textos y botones, el punto que titila, la
  plantilla de landing de IA, las rejillas de tarjetas iguales y los avisos flotantes que parecen de cookies.
- **Un solo concepto para toda la app:** barra, Inicio, «Ahora suena» y menús salen del mismo objeto o idea. Nada de
  piezas sueltas.
- **El título de marca:** como `BrandTitle` en Android, alterna «Life Music» y el logo de CG LABS. Es intocable.
- **Normas:** WCAG 2.2 AA (contraste, foco visible y objetivos táctiles de 44 × 44 pt como mínimo) e ISO 9241-110.
- **Equilibrio para todos los teléfonos:** todo efecto pesado tiene una versión liviana y respeta «reducir
  movimiento».

## 3. Lo que el iPhone le impone al diseño (para que la maqueta no prometa imposibles)

| Hecho de iOS | Qué significa para el diseño |
|---|---|
| Instalada, abre sin barras de Safari y el contenido pasa por debajo de la isla y la barra de estado | Los márgenes respetan las zonas seguras: arriba la isla, abajo la raya de inicio. La barra de pestañas va sobre la raya, no debajo. |
| En la app instalada no hay gesto de «atrás» del navegador | Cada pantalla interior (álbum, artista, lista, ajustes) lleva su botón de atrás arriba a la izquierda, como en iOS. |
| La web no puede cambiar el volumen en iPhone (iOS ignora el volumen de la página) | **En el iPhone no hay control de volumen**: se usan los botones del teléfono. En el computador sí hay. |
| Safari no hace vibrar el teléfono desde una web | Ningún gesto depende de una vibración para confirmarse. Siempre hay una respuesta visual. |
| Safari no muestra un botón de «Instalar» | La app trae **su propia guía de instalación** (sección 5.11). |
| Al bloquear la pantalla, la música se pausa | Se dice una vez y con calma en la bienvenida. No se esconde. |
| Si un campo de texto tiene letra de menos de 16 px, Safari hace zoom al tocarlo | El buscador y los campos van en 16 px o más. |

## 4. Navegación

- **Teléfono:**
  - **Barra de pestañas abajo:** Inicio, Buscar y Biblioteca, con íconos que representan cada sección.
  - **Encima, el mini reproductor.**
  - **Arriba,** el título grande de cada pantalla, que se encoge al desplazarse, como en iOS.
  - **Ajustes y Acerca de** se abren desde Biblioteca o desde el título de marca.
- **Computador:** columna izquierda con la navegación, centro, «Ahora suena» a la derecha y barra del reproductor
  abajo. Es lo que ya tiene la 0.1.

## 5. Pantallas que tiene que traer la maqueta

Cada una en **teléfono (iPhone)** y **computador**, y en **claro y oscuro** si el concepto usa los dos.

1. **Arranque:** la pantalla que se ve mientras abre la app instalada, con el logo y el nombre.
2. **Bienvenida, en la primera visita:** tres pasos cortos más el aviso legal en español e inglés. Life Music no
   está afiliado a YouTube ni a Google. Incluye el aviso del iPhone, que pausa al bloquear.
3. **Inicio:** filas de YouTube Music del país, más «Seguir escuchando» con lo propio. Las filas se deslizan de lado,
   y no puede ser una rejilla de tarjetas iguales.
4. **Buscar:**
   - Campo grande y búsquedas recientes.
   - Sugerencias mientras se escribe.
   - Resultados por tipo: canciones, artistas, álbumes y listas.
5. **Álbum, Artista y Lista:**
   - Cabecera con la carátula o la foto, y los botones Reproducir y Aleatorio.
   - Debajo, las canciones.
   - Al desplazarse, la cabecera se encoge en una barra.
6. **Biblioteca:** Me gusta, Mis listas e Historial. Más la entrada a Ajustes y a Acerca de.
7. **Mini reproductor:**
   - Carátula, título, artista, play/pausa y siguiente.
   - Un hilo de progreso.
   - Tocarlo o deslizarlo hacia arriba abre «Ahora suena».
8. **Ahora suena, pantalla completa** (la pieza estrella):
   - Carátula grande, título y artista, Me gusta y la barra de tiempo.
   - Anterior, play/pausa y siguiente, aleatorio y repetir.
   - Deslizar hacia abajo cierra la pantalla.
   - Deslizar la carátula de lado cambia de canción.
   - La cola sube desde abajo.
   - El **fondo** sale de los estilos de la app Android (sección 6).
9. **Cuando suena un anuncio de YouTube:** es el único momento en que aparece el reproductor de YouTube, para que se
   pueda ver y saltar. Hay que diseñar cómo aparece sin romper el concepto, por ejemplo en una hoja con el título
   «Anuncio de YouTube», y cómo se va.
10. **Menú de una canción** (hoja que sube desde abajo): Reproducir a continuación, Agregar a la cola, Agregar a
    una lista, Me gusta, Ir al álbum, Ir al artista y Compartir.
11. **Guía «Instálala en tu iPhone»:** solo aparece en Safari, cuando la app no está instalada. Lleva los pasos
    Compartir → «Agregar a inicio» con dibujos propios. En el computador es un botón «Instalar».
12. **Ajustes:**
    - Idioma.
    - Tema claro u oscuro.
    - Estilo de «Ahora suena».
    - Mantener la pantalla encendida.
    - Borrar mis datos.
13. **Acerca de:** la versión, los créditos GPL de toda la cadena, el enlace al código, el wordmark de CG LABS y
    `© 2026 CG LABS`.
14. **Estados:**
    - Cargando: con forma de la pantalla que viene, sin ruedas genéricas.
    - Sin internet.
    - Búsqueda sin resultados.
    - Canción no disponible, que se salta sola y lo dice con palabras.
    - Biblioteca vacía: una invitación, no una disculpa.

## 6. «Ahora suena»: los fondos de la app Android

Como la carátula vuelve a ser nuestra imagen, **se pueden copiar todos los fondos de Android**. Están mapeados en
`PlayerBackgroundStyle` y `ui/player/Player.kt`.

| Estilo | Cómo se ve | Peso |
|---|---|---|
| **Apple Music** (el predeterminado en Android) | La carátula nítida a sangre en el 65 % de arriba, fundida hacia abajo sobre la misma carátula muy desenfocada | Liviano |
| **Live Mesh** | Tres copias de la carátula, saturadas y desenfocadas, que giran a 80, 40 y 60 s | Medio (con versión liviana) |
| **Brillo animado** | Seis manchas de luz con los colores de la carátula en un ciclo de 20 s | Medio |
| **Desenfoque** | La carátula muy desenfocada con un velo oscuro | Liviano |
| **Degradado** | Degradado vertical con los colores de la carátula | Liviano |

**Recomendación:** Apple Music como predeterminado, igual que en Android, y los demás en Ajustes. La maqueta muestra
«Ahora suena» con Apple Music y con Live Mesh, para que CG compare.

## 7. Movimiento

- **Duraciones:**
  - Las hojas suben y bajan en unos 300 ms, con una curva suave tipo iOS.
  - Los cambios de pantalla tardan 200 a 250 ms.
  - El fondo de «Ahora suena» se funde en 1,2 s al cambiar de canción, como en Android.
- **Cada movimiento significa algo** (la regla de la landing): abrir, cerrar, cambiar de canción. No se anima nada
  de adorno.
- **Si el teléfono pide «reducir movimiento»,** todo pasa a fundidos cortos y los fondos se quedan quietos.

## 8. Lo que se le entrega a Diseño y marca

- **Este brief.**
- **Capturas de la 0.1** (estructura actual) y de la demo de fondos.
- **El logo y la lámina de Pulso.**
- **Las pantallas del Android de referencia,** si CG quiere que «Ahora suena» se parezca a la de la app.

## 9. Cómo se decide (en partes pequeñas, como prefiere CG)

1. **El brief:** CG lo corrige y lo aprueba. Es este documento.
2. **Tres láminas pequeñas:** el concepto, «Ahora suena» y el mini reproductor en el iPhone. CG elige una.
3. **La maqueta completa** en iPhone y computador, con la dirección elegida. CG la aprueba entera, no por pedazos.
4. **La programación:** la hace la sesión iPhone, detrás de un interruptor hasta que CG la vea en su teléfono.

**Calendario:** las láminas y la maqueta el sábado 24, se programan el sábado en la noche y el domingo 25, y las
pruebas son el domingo. Si la maqueta no está aprobada el sábado en la noche, CG decide si el lunes sale con el estilo
sobrio de Pulso o si se corre la entrega.
