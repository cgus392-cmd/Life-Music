# «Estudio» · ficha técnica (bloque B1, 27-10-2026)

**Qué es:** la sección de las funciones avanzadas de Life Music, las que usan la potencia del teléfono o bajan
modelos. Ahí la persona ve qué hace cada una, cómo le va a ir en su teléfono, y decide cuáles prende. También es
**el centro de control de los modelos**: el único lugar donde se bajan, se ven y se borran.

**Decisiones de CG:**
- **23-10:** el nombre «Estudio». En los teléfonos que no dan, una «versión ligera».
- **27-10:** un menú con todo lo que usa hardware y el control en manos de la persona. Estudio también es el centro
  de control de los modelos y las descargas.

Estado: **ficha cerrada con las respuestas de CG (27-10).** Sigue la maqueta con Diseño (B2) y luego el código (B3 y B4).

---

## 1. Lo que entra

Cada preferencia vive en **un solo lugar**: lo que se muda a Estudio sale de donde estaba (ver
`listas-y-defectos-duplicados`). Se reusan las mismas claves, así que nadie pierde lo que ya tenía.

| # | Función | Qué usa | Descarga | Dónde vive hoy | Versión ligera |
|---|---|---|---|---|---|
| 1 | **Karaoke propio** (palabra por palabra) | procesador y memoria; separar la voz es lo pesado | reconocedor de 103 MB y separador de 29 MB | Ajustes › Reproductor (`KaraokePropioKey`) | sin separar la voz (camino rápido, ~10 s) |
| 2 | **Voces HD de Nova** | procesador al hablar | 60 a 334 MB por voz | Nova › Personalizar (las descargas) | la voz del teléfono |
| 3 | **Golpes reales para el TV** (tema Escenario) | analiza la canción entera, unos segundos | — | automático con Escenario | Escenario quieto, sin saltos |
| 4 | **Liquid Glass** | tarjeta gráfica | — | Ajustes › Liquid Glass (`GlassEffectSettings`) | vidrio sin lente (Android 12) |
| 5 | **Canvas animado** (video de la carátula) | tarjeta gráfica, batería y datos | lo baja al sonar | Apariencia y modo ambiente (`CanvasThumbnailAnimationKey`) | carátula fija |
| 6 | **Fondo reactivo del modo ambiente** | tarjeta gráfica y batería | — | modo ambiente (`AmbienteFondoReactivoKey`) | fondo quieto |
| 7 | **«De oído»** | lo mismo que el karaoke | lo que salga de la prueba del bloque C | — | — |

El 7 entra solo si pasa la prueba del bloque C.

**Lo que NO entra** (no es pesado o es una elección de estilo):
- encender Nova;
- el fondo del reproductor (Apple Music, Live Mesh, Glow…);
- la Automix: su análisis es de 18 s y liviano.

Dentro de Estudio, cada función enlaza a su pantalla de detalles cuando la tiene: Personalizar de Nova, los
deslizadores de Liquid Glass y la pantalla del karaoke.

## 2. Cómo se ve cada función (lo dibuja Diseño)

- **El nombre y una línea** de qué hace.
- **Qué usa,** con íconos propios: procesador, gráfica, batería o descarga.
- **Cómo le va en este teléfono:** **Lista**, **Va a tardar** o **No da**.
  - Si no da y hay versión ligera, se ofrece la ligera con su nombre.
  - Si no hay versión ligera, la función no aparece. Según lo decidido el 23-10: lo que no aguanta, no se muestra.
- **Su interruptor.**
- **Si baja modelo:** tamaño, estado (sin bajar, bajando con %, listo), y los botones Bajar y Borrar.

## 3. La medición del teléfono

- **Una sola vez,** al abrir Estudio por primera vez, en unos 3 segundos. Se repite solo si se toca «Volver a
  medir».
- **Reusa `nova/NovaCapacidad.kt`:**
  - lo que mide el teléfono: clase de rendimiento, memoria, núcleos y si es de memoria baja;
  - lo que exige cada función: ligera, media o pesada;
  - la previsión: disponible, puede tardar o no disponible.
- **Las voces HD ya tienen su prueba real,** con el tiempo por segundo de voz. Estudio la muestra tal cual.

## 4. El centro de modelos

- **Una lista arriba** con todos los modelos bajados: nombre, para qué sirve, tamaño y fecha. Abajo, el **espacio
  total que ocupan** y cuánto queda libre en el teléfono.
- **Borrar** pide confirmación y dice qué función se apaga con ello.
- **Las licencias y los créditos** de cada modelo (NVIDIA NeMo, UVR, Piper, Kokoro…) quedan a un toque. Es la base
  de la sección de créditos de la 2.0.
- **Los modelos se siguen bajando de donde se bajan hoy:** las publicaciones de sherpa-onnx en GitHub. Nada pasa por
  servidores de CG LABS.

## 5. Lo común

- **Un aviso que se acepta una vez:** «Estas funciones usan la potencia de tu teléfono: pueden tardar, calentarlo o
  gastar batería».
- **«Solo mientras carga»,** para los trabajos largos: karaoke y «De oído».
- **El avance va en la notificación,** con Cancelar, como ya hace `KaraokeService`.
- **Todo pasa en el teléfono.** Nada sale a servidores.

## 6. Dónde vive

**En el menú de la foto de perfil** (CG, 27-10): es el óvalo de arriba a la derecha, que abre `MenuDeLaFoto` en
`ui/component/Aurora.kt`. Entra como una opción más, «Estudio», con la línea «Funciones avanzadas y modelos», entre
Escuchar juntos y Ajustes. Lo nuevo y visual va **detrás de un interruptor** mientras CG lo prueba, según la
regla 6 de PARA-RETOMAR. Con el interruptor apagado, todo queda donde está hoy.

## 7. Tandas

1. **B3a:** la pantalla de Estudio con la medición y la lista de funciones, sin mudar nada todavía.
2. **B3b:** el centro de modelos, con bajar, borrar y el espacio.
3. **B4:** mudar los interruptores y dejar en el lugar viejo un acceso que diga «Ahora está en Estudio».
4. **B5:** «solo mientras carga» y el aviso de aceptación.

Cada tanda se instala y la prueba CG.

## 8. Decisiones de CG (27-10)

1. **Encender Nova se queda en Reproductor.** A Estudio solo van sus voces HD.
2. **El fondo del reproductor se queda en Apariencia.**
3. **A Estudio se entra desde el menú de la foto de perfil.**
