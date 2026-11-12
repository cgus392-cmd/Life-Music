# Lo que falta: la lista única (09-11-2026, noche)

Pedido de CG: ordenar todo lo propuesto antes de seguir. **Regla desde mañana:** primero se cierra lo abierto. Las
ideas nuevas van directo al banco (sección 4), sin interrumpir lo que está en curso.

## 1. Abierto ahora: probar y cerrar (mañana, primero)

| # | Qué | Estado |
|---|---|---|
| 1 | Tempo medido donde se mezcla (prueba.17) | Compilando. Probar Bad Bunny → Tego en «PRUEBA DJ 3»: el registro debe decir «al golpe = sí». |
| 2 | Estilos de la tanda 5: Fundido, Subida, Corte, Eco | Hechos, pero sin oírlos en una mezcla al golpe. Dependen del punto 1. |
| 3 | Tocadiscos en Apple Music | Funciona según el registro. Falta el visto bueno visual de CG. |
| 4 | Canvas en todos los estilos | Arreglado. Falta probarlo con una colaboración que no se haya abierto antes en Apple Music. |
| 5 | Letra en vertical para estados | Hecha. Falta compartir una de prueba. |
| 6 | Fallos vistos hoy | a) Los botones del encabezado de las listas no caben («Reprod / ucir», «Aleat…», «Mezc…»). b) La pantalla «Analizando» muestra el tempo de la mitad, no el de la mezcla. c) El análisis de una canción que no está en caché se corta a los 45 s y se queda el dato viejo. |

## 1b. En lo que se trabaja ahora (orden de CG, 09-11 noche)

Las pruebas quedan para después. CG: «lo que estamos haciendo ahora es el Automix v2».

| Bloque | Qué | Estado |
|---|---|---|
| A · El mapa de la canción | Escucha la canción entera: energía, batería, graves y medios por compás, secciones y **puntos de mezcla** con su motivo | Motor hecho, con pruebas (`MapaDeCancion.kt`). Falta conectarlo al teléfono (construirlo con el audio en caché o mientras suena) y afinar la batería con canciones limpias: en el audio de TikTok la confunden las consonantes de la voz. |
| B · La mesa del Mixed Editor | Lámina 2 aprobada: dos ondas sobre los compases, **puntos sugeridos como los destacados de Instagram**, imán al compás, largo, estilo, «Escuchar · 10 s», guardar por lista; más adelante cosas de estudio | Pendiente |
| C · Automix por estructura y voces | Sale donde una se queda sin batería, entra donde la otra arranca, capas que no chocan: el mashup | Pendiente (usa A) |
| D · Perfiles por género | Reconocer salsa, reguetón (huella del dembow), vallenato y balada, y reglas por pareja (salsa → vallenato, balada → reguetón…), aprendiendo de los metadatos (BPM y demás) | Pendiente |
| Al final de todo | Voces de Nova y Wrapped | Lo último, según CG |

## 1c. Letras: la mejor, no la primera (pedido de CG, 12-11; prueba.25)

- **Antes:** ganaba la primera letra sincronizada en llegar. Muchas veces venía por línea habiendo otra palabra por
  palabra, estaba en otro idioma o era de otra versión, y la persona tenía que buscar a mano.
- **Ahora** (`lyrics/.../EleccionDeLetra.kt`, 9 pruebas):
  - Se espera hasta 3 s después de la primera letra útil; una perfecta corta la espera.
  - Cada letra recibe un puntaje:
    - sincronía: palabra > línea > ninguna;
    - escritura igual a la del título;
    - duración que cuadre, porque si no es otra versión;
    - si trae palabras del título;
    - si tiene menos de 4 líneas, baja;
    - el orden de proveedores solo desempata.
- **Re-evaluación:** una letra guardada sin sincronizar, vacía o no encontrada se vuelve a buscar una vez por
  sesión, y solo se reemplaza si la nueva es mejor. Lo que la persona eligió o editó a mano
  (`LetrasElegidasAManoKey`) nunca se toca. Registro: `LifeMusicLetra`.
- **Falta saber de CG:** los otros puntos de «suceden varias cosas»; solo contó el primero.

## 2. Para cerrar la 2.1.0

1. Gama baja en el A04s: el tocadiscos quieto, que nada se trabe y el 17 % de contenido del Inicio que quedó pendiente.
2. El ritual, solo con el «publica» de CG:
   - 5 APK con sha256;
   - ficha de novedades con imagen;
   - `indice.json` y CON_FICHA;
   - notas y mappings;
   - la web antes de quitar el borrador.

## 3. La 2.1.1 (planeado)

- **Mixed Editor:**
  - tanda 6, el set;
  - tanda 7, la mesa (el editor con las ondas);
  - tanda 8, los fondos de cada estilo (lámina 4).
- **Nova:** voces naturales (Microsoft en la nube con aviso de privacidad, y las del teléfono sin internet) y
  «Pídele a Nova».
- **Automix v2:** entrada y salida por estructura, voces, mashup por capas. Plan en `automix-v2/plan.md`.
- **Detalle:** en Apple Music, que el canvas siga en video durante el tocadiscos.
- **Cola (pedido de CG, 12-11):** que las canciones del algoritmo se puedan mover de posición.
  - Hoy la cola real ya se mueve con la manija. «Contenido similar» (`automixItems`, las sugerencias de YouTube) no
    se mueve porque esas canciones no están en la cola: solo tienen «reproducir después» y «agregar».
  - **Idea:** darles manija también. Moverlas entre ellas reordena las sugerencias; arrastrar una hacia arriba la
    mete en la cola en esa posición.
- **Mashups, M2 a M4** (ver `automix-v2/mashups-m1.md`):
  - M2 «Preparar canciones», con KARA o Spleeter según la capacidad, en Estudio;
  - M3 el motor, que puede estirar y transponer;
  - M4 el modo Mashup en la mesa.

## 4. Banco de ideas (sin fecha)

- **Mezcla:**
  - la huella del dembow como detector de reguetón;
  - perfiles por género;
  - set ordenado por armonía (Camelot);
  - curva de energía del set;
  - 👍/👎 local por mezcla;
  - memoria de mezclas.
- **Visual:** la carátula que se funde como tinta.
  - El brillo de la barra durante la mezcla salió del banco el 11-11 porque CG lo pidió.
  - Está programado en `ui/player/BrilloDeMezcla.kt` y viaja en la próxima prueba.
- **Alta fidelidad:**
  - Calidad Premium rotulada «256 kbps»;
  - USB bit-perfect;
  - R128;
  - lossless legal (FLAC propio, Navidrome/Subsonic, SMB/WebDAV, Internet Archive, Jamendo);
  - etiqueta hi-fi;
  - documento educativo sobre Tidal y Qobuz.
- **2.2:** Wrapped («Tu año en Life Music», diciembre), y rachas y mascotas de Bandas (en pausa; las mascotas con Flow).
- **Producto:** las dos ediciones (idea guardada).

## 5. Decisiones que esperan a CG

1. Copia de seguridad automática semanal dentro de la app (propuesta después de la pérdida de datos del 09-11).
2. Qué mejoras quiere en Estudio.
3. El corte 2.1.0 / 2.1.1 propuesto hoy.
4. Confirmar si sigue pendiente el «publica» del índice de novedades en la web (anotado el 08-11).
