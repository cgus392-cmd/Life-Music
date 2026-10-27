# Life Music 2.0 · Plan por bloques (27-10-2026)

Este plan sale de las pruebas de CG entre el 23 y el 27-10. Sigue la regla del 26-10: primero el plan priorizado,
después **un bloque a la vez**. Cada punto pasa por cuatro pasos: propuesta corta, tandas instalables, prueba de CG y
CG lo cierra. Lo que llegue en medio va a «Después». **Nada de la 2.0 se publica:** son versiones de prueba por USB.

Estado: **esperando el sí de CG al orden y a la decisión de «De oído».**

---

## Bloque A · Que a Nova se le entienda (1 o 2 tandas)

**La queja (CG, 27-10):** «Nova habla muy rápido… a veces no se le entiende lo que dice».

**Por qué pasa (visto en el código):**
- La voz HD (Piper o Kokoro, `nova/NovaVozHD.kt:47`) se genera con `m.generate(texto, hablante, 1.0f)`: velocidad 1,0.
  Para locución en español es rápida, sobre todo con música sonando por debajo.
- La voz del teléfono (`nova/NovaVoz.kt`) tampoco fija la velocidad: va a 1,0.
- Los subtítulos de Life Line siguen la duración del audio. Si la voz va rápida, el texto pasa rápido. En «solo
  subtítulos» van 380 ms por palabra (`NovaLocutor.MS_POR_PALABRA`).

**Qué cambia:**
1. **Más pausada de fábrica:** la velocidad baja a 0,88 en la voz HD y a 0,9 en la voz del teléfono.
2. **Respiros entre frases:** silencio entre oraciones en las voces que lo permiten y comas donde la frase es larga.
3. **Un ajuste nuevo en Personalizar, «Ritmo de Nova»,** con tres opciones: Pausado (0,8), Normal (0,88) y Ágil (1,0).
4. **Subtítulos:** salen al ritmo de la voz nueva. En «solo subtítulos» suben a unos 450 ms por palabra.
5. **Revisar las frases largas** de `NovaFrases.kt`: partirlas en dos cuando pasen de unas 18 palabras.

**Cómo se prueba:** CG escucha 4 o 5 intervenciones con música (inicio, cambio de onda y entre canciones) y dice si
se entiende.

---

## Bloque B · «Estudio»: el módulo de funciones avanzadas

**La idea (CG, 27-10):** como varias funciones usan el hardware del teléfono, se avisa que dependen de la capacidad
del aparato y la persona elige cuáles quiere: un menú con todas ellas y el control en sus manos. **El nombre
«Estudio» lo eligió CG el 23-10, y también la «versión ligera» para los teléfonos que no dan.**

**Qué entra (inventario inicial, a confirmar en la ficha):**

| Función | Qué usa | Descarga |
|---|---|---|
| Karaoke propio (palabra por palabra) | procesador y memoria; separar la voz es lo pesado | ~160 MB (modelo de voz y separador) |
| «De oído» (si sigue, ver bloque C) | lo mismo que el karaoke | lo mismo |
| Nova con voz HD | procesador al hablar | 60–100 MB por voz |
| Golpes reales para el TV (Escenario) | analiza la canción entera | — |
| Liquid Glass | tarjeta gráfica (Android 12+, lente en 13+) | — |
| Fondos animados (Live Mesh, Glow, Canvas animado) | tarjeta gráfica y batería | — |

**Cómo funciona:**
1. **Medir el teléfono una sola vez,** unos 3 segundos: memoria, procesador y clase de rendimiento. Ya existe la base
   en `nova/NovaCapacidad.kt`, con sus niveles de exigencia y la previsión «disponible», «puede tardar» y «no
   disponible».
2. **Cada función muestra:**
   - qué hace;
   - qué usa;
   - cuánto descarga;
   - cómo le va a ir en este teléfono: **Lista**, **Va a tardar** o **No da**. Cuando no da, se ofrece la versión
     ligera si existe, y si no existe, la función no aparece;
   - su interruptor.
3. **La persona acepta una vez** el aviso de que usan la potencia del teléfono.
4. **Opciones comunes:**
   - «solo mientras carga»;
   - el avance en la notificación, con botón para cancelar;
   - borrar los modelos descargados para recuperar espacio.
5. **Todo pasa en el teléfono y nada sale a servidores,** igual que hoy.
6. **Los interruptores que hoy están regados por Ajustes se mudan a Estudio,** con el mismo ajuste guardado: nadie
   pierde lo que ya tenía.

**Pasos:**
1. **B1 · Ficha técnica:** el inventario confirmado y qué interruptor se muda. La aprueba CG.
2. **B2 · Maqueta en la sesión «Diseño y marca»:** es visual nuevo, así que va con el estilo de la app y nada
   genérico. La aprueba CG.
3. **B3 · Programar** la pantalla y la medición.
4. **B4 · Mudar los interruptores** y probar en el S25.

---

## Bloque C · «De oído»: decisión

**El resultado:**
- **Prueba técnica (23-10):** con la voz separada acierta entre el 41 y el 66 % de las palabras.
- **Prueba de oído de CG con «Urge el Amor»:** **el 70 % salió mal transcrito.** No sirve como letra, ni siquiera como
  borrador cómodo.

**Opciones (elige CG):**
1. **Probar un modelo más fuerte,** de la familia Whisper, que se lleva mejor con el canto. Es una prueba acotada de
   1 día con las mismas 6 canciones. **Pide descargar un modelo de varios cientos de MB, con permiso de CG.** Si no
   pasa del 75–80 %, se archiva.
2. **Archivarla ya:** «De oído» sale de la 2.0 hasta que haya un modelo mejor. El código de la prueba queda guardado.
3. **Dejarla como borrador en Estudio,** con un editor para corregir. No se recomienda con este acierto.

**Mi recomendación:** la 1, con la vara clara. Si no la pasa, la 2.

---

## Bloque D · Ideas de la 2.0 (por recibir)

- Las ideas nuevas que CG va a contar.
- **Las que trae la sesión iPhone para Android** (se le pidieron el 27-10).
- **Lo que ya estaba en la 2.0 y sigue pendiente:**
  - la maqueta v2 «Receptor hi-fi» (barra e Inicio), sin aprobar;
  - la Automix fase B;
  - el QR del TV hacia lifemusic.app;
  - los créditos de tecnologías;
  - quitar el código de la copa, el prerregistro y los retos;
  - el ícono nuevo de la app, cuando CG diga.

Se ordenan cuando lleguen todas, con el mismo criterio: lo que más se nota primero.

---

## Orden propuesto

1. **A · Nova se entiende.** Es rápido y se nota enseguida.
2. **C · Decidir «De oído».** Si es la opción 1, la prueba va aquí.
3. **B · Estudio.** Es el más grande y reúne todo lo pesado, incluido lo que quede de C.
4. **D · Las ideas nuevas,** ya ordenadas.

## Después

(Aquí va lo que llegue mientras se trabaja en un bloque.)
