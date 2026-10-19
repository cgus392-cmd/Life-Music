# Ficha técnica · La Cabina pasa a ser la Sala de control de Life Music

**Versión:** 2.0 · **Capa:** 2 (desarrollo) · **Fase:** 4 (ejecución) · **Estado:** **APROBADA por CG el 18-10**
(«procede»), con la hoja de pruebas del supervisor (§3b)
**Idea de:** CG, 2026-10-18. Dijo que la cabina «es un poco extraña en cuanto a funcionamiento y propósito» y que
quiere «un cambio radical de verdad». Eligió el camino **A · Sala de control** y **quitar de la vista** lo de
recursos humanos.

## 1. El problema de hoy

La cabina (`lifemusic.app/equipo`, lanzada el 10-10) mezcla tres cosas que no tienen que ver entre sí:
- **Recursos humanos:** pases, acta con firma, jornada de 8 a 5 y política legal.
- **Un tablero de trabajo:** ideas por fases, incidencias y notas.
- **Un control de accesos.**

Además, está hecha para un equipo que todavía no existe. Y **no muestra Life Music**: no se ve qué versión va, qué
APK hay para probar, cuántos la bajaron ni cómo va la 2.0.

## 2. Qué es ahora

Es **el lugar donde se ve y se mueve todo Life Music**, como la sala de control de un estudio de grabación.
- La usa primero CG, como dueño.
- Cuando llegue el equipo, cada persona ve la sala y mueve lo suyo.

## 3. Lo que se ve: cinco piezas del mismo objeto

| Pieza | Qué muestra | De dónde sale |
|---|---|---|
| **1. Al aire** | Lo que se está haciendo hoy y en qué sesión (General, TV, Carro o Diseño). También la **toma para probar**: qué APK es, a qué hora salió, qué trae y qué hay que probar. | La escribo yo al cerrar cada tanda. |
| **2. La consola** | Un **canal por frente**: App del teléfono (2.0), TV/Cast, Carro, Web y Diseño y marca. Cada canal tiene su versión, su estado (en marcha, espera a CG o en pausa), el último avance con fecha y qué espera a quién. | La actualiza cada sesión al cerrar su tanda. |
| **3. La cinta de la 2.0** | Las ideas en **4 pistas, una por capa** (funciones, desarrollo, optimización y mantenimiento). Cada idea avanza por la cinta: idea → planteamiento → luz verde → ejecución → pruebas → lanzada. **Solo CG la pasa a luz verde.** | El tablero que ya existe, con las 19 ideas más las nuevas (Nova beta, karaoke beta, Receptor hi-fi y Cast en las salas). Se le agrega la capa. |
| **4. Medidores** | Lo que pasa afuera: **descargas por versión** (hoy la 1.3.2 va en 108), el promedio de calificaciones, los prerregistros y el estado de los servicios (las barras de señal que ya existen). | Automático: el servidor lo consulta en GitHub y en la comunidad. |
| **5. Bitácora** | Lo que se terminó, con fecha; las incidencias abiertas; y más adelante, los reportes de los probadores. | Lo escribimos CG y yo. |

## 3b. El trabajo del supervisor: la hoja de pruebas de cada versión

**Pedido de CG (18-10):** las personas del equipo, como Steven, Samuel o Ian, solo tienen **una lista de tareas por
versión**, con cada cosa que trae esa versión para probarla. Completarla es su trabajo. El puesto se llama
**«Supervisor de ejecución»**.

**Cómo se arma la hoja de una versión:**
1. **Cada toma tiene su hoja.** Una toma es un APK para probar.
2. **Lo nuevo:** cada idea marcada «Hecha · falta probar» se vuelve de 1 a 3 puntos de la hoja. Por ejemplo, el
   karaoke: activarlo en Ajustes, prepararlo en una canción descargada, ver que sale la notificación y que la letra se
   ilumina.
3. **La ronda de siempre:** 5 o 6 puntos que se repiten en cada versión, para pillar lo que se rompió sin querer.
   Son abrir, que suene, buscar, descargar, la letra y el Cast si hay un TV.
4. **Cómo es cada punto:** qué probar, los pasos (cortos) y lo que debe pasar.
5. **Quién la escribe:** yo, al cerrar la tanda, junto con el aviso de «APK para probar».

**Qué hace el supervisor:**
- **Al entrar por primera vez,** dice qué teléfono tiene: marca, modelo y Android. Vale oro, porque la regla de CG es
  que todo funcione en **todos** los teléfonos, no solo en un S25.
- **En cada punto marca una de tres:**
  - **Funciona.**
  - **Falla:** con una nota corta de qué pasó y en qué paso.
  - **No pude:** por ejemplo, no tengo TV, no tengo carro o no tengo la canción.
- **Su avance se guarda solo.** Puede seguir otro día.

**Qué pasa con lo que marca:**
- **Una falla abre sola una incidencia** en la bitácora de la Sala, ligada a la versión y al punto. La veo, la
  arreglamos, y en la toma siguiente ese punto vuelve a la hoja como **«Volver a probar»**.
- **En la Sala, CG ve el avance en vivo** de cada supervisor, por ejemplo «Samuel · 12 de 18 · 2 fallas».
- **Cuando todo queda en verde,** la toma pasa a **«Lista para publicar»**. **Publicar sigue siendo solo de CG.**
- **Al publicar la versión,** sus ideas pasan de «Hecha · falta probar» a «Lanzada».
- **Cada versión guarda su hoja** con quién probó, en qué teléfono y cuándo.

**Qué ve el supervisor y qué no:**
- **Ve solo «Mi hoja de pruebas»** de la toma al aire y su historial.
- **Ya no ve** el tablero, las notas, los accesos ni la jornada.
- **Lo que firmaron Samuel y Steven se guarda.**
- **Quien entre nuevo** usa su pase y acepta la política de datos con una casilla, que es lo mínimo legal (Ley 1581).
  Sin acta firmada, salvo que CG encienda «Equipo y contratación».
- **Un solo puesto:** «Operaciones» y «Supervisor» quedan en uno, «Supervisor de ejecución». Steven, que está en
  pausa, pasa a ese puesto si CG lo reactiva.

**Cómo le llega el APK al supervisor:**
- Hoy los APK de prueba solo se instalan por cable en el S25 de CG.
- Para el supervisor, cada toma necesita un enlace de descarga: **una versión de prueba en GitHub**, como la del carro
  (`carro-v0.3.0-prueba.1`).
- Subirla es publicar, así que va con el «publica» de CG en cada toma.
- La otra opción es que CG le pase el APK directamente.
- **Decidido por CG (18-10): versión de prueba en GitHub,** con su «publica» en cada toma. La hoja muestra el botón
  de descarga.

**Horario, decidido por CG (18-10):** el supervisor **marca a cualquier hora**. Para la hoja de pruebas no hay jornada.

## 4. Lo que sale de la vista (sin borrar nada)

Salen de la vista los pases para contratar, el acta con firma, la jornada, los accesos y la revisión semanal.
- **Las tablas y los datos se quedan guardados.** Un interruptor del dueño, «Equipo y contratación», los vuelve a
  mostrar el día que CG contrate.
- **La entrada sigue igual:** tu sesión de dueño y «enlazar otro aparato».
- **La política de datos sigue en el pie,** porque la sesión usa una cookie.

## 5. Cómo se mantiene al día sin trabajo extra

- **Lo automático** (descargas, calificaciones y servicios) lo trae el servidor y lo guarda 10 minutos, para no
  gastar.
- **«Al aire», la toma, la consola y la bitácora** los escribo yo al cerrar cada tanda. Es la misma regla que ya
  existe de cerrar con «APK para probar», solo que ahora también queda en la sala.
  - Se escribe directo en la base de la cabina con `wrangler`, como cuando se cargaron las 19 ideas el 13-10.
  - El plan es gratis y no cuesta nada.

## 6. Por dentro

- **Base (D1):**
  - tablas nuevas: `frentes` (el canal de cada frente), `tomas` (los APK para probar) y `avances` (lo terminado);
  - para la hoja de pruebas: `pruebas` (los puntos de cada toma, con su idea, pasos y resultado esperado),
    `resultados` (por persona y punto: funciona, falla o no pude, más la nota) y `telefonos` (el aparato de cada
    supervisor);
  - en `ideas`, una columna nueva `capa`;
  - `version_objetivo` pasa a 2.0.
  - Nada se borra.
- **Servidor (Worker):**
  - `GET /sala` lo entrega todo junto;
  - `GET /medidores` consulta las descargas en la API pública de GitHub, sin clave, y las calificaciones. Lo guarda 10
    minutos, así que hace como mucho 6 consultas por hora; el límite es 60.
- **Página:**
  - la vista nueva «Sala» es lo primero que ve el dueño;
  - las vistas de recursos humanos quedan detrás del interruptor;
  - se mantienen la CSP estricta, cero analítica y la cookie única.

## 7. Diseño

**Se mantiene el concepto que CG ya aprobó: la «cabina de producción».** Se conservan las letras (Archivo, Instrument
Sans e IBM Plex Mono), la luz AL AIRE y las barras de señal.

Lo nuevo sale del mismo mundo:
- la **consola de mezcla**, con un canal por frente;
- la **cinta multipista** para la 2.0;
- la **toma**, como la claqueta de lo que hay que probar.

**Nada de rejillas de tarjetas iguales** ([[diseno-no-generico]]). La maqueta la hace la sesión «Life Music · Diseño y
marca», según la regla del 14-10.

## 8. Tandas

1. **Base y servidor, en local:** las tablas nuevas, `/sala` y `/medidores`, con pruebas.
2. **Página «Sala», en local**, con capturas para CG. Se hace cuando CG apruebe la maqueta.
3. **Datos reales:** los cinco frentes, la toma de hoy, las capas de las 19 ideas y las 4 nuevas.
4. **Publicar en `lifemusic.app/equipo`**, solo con el «publica» de CG. **El orden importa**, porque el servidor nuevo
   consulta tablas que la base de hoy no tiene:
   1. `wrangler d1 execute lifemusic-equipo --remote --file migraciones/2026-10-18-sala-de-control.sql`;
   2. `… --file datos/2026-10-18-sala.sql`;
   3. `wrangler deploy` (el Worker);
   4. la página.

**Avance (18-10):** la tanda 1 está **HECHA** (commit 68c0eb2f, sin publicar). Pasaron 16 pruebas de lógica y 36 de
punta a punta contra `wrangler dev`, con el guion `scratchpad/prueba-sala.mjs`. Los datos reales están en
`equipo/datos/2026-10-18-sala.sql`: 5 frentes, la toma 1 con 16 puntos, las capas, «al aire» y 8 avances.

Ese mismo día ya se pasaron a la base real las ideas hechas: Nova (19) quedó en fase 5, y se agregaron de la 20 a la
25. La tanda 2 (la página) espera la maqueta de Diseño y marca.

## 9. Lo que necesito de CG

1. **Aprobar esta ficha** o pedir cambios.
2. **Permiso** para que yo escriba «Al aire», la toma, la consola y la bitácora en la base de la cabina al cerrar cada
   tanda.
3. **La maqueta:** ¿se la pido a la sesión de Diseño y marca, como dice la regla, o la hago aquí esta vez?
