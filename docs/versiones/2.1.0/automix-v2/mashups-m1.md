# Mashups · M1: qué modelo separa la voz (12-11-2026)

## Decisiones de CG (12-11)

- **Estirar y transponer:** sí se permite, solo en mashups. Las transiciones siguen con el 5 % y sin transponer.
- **Modelo principal:** el de 28 MB.
- **Respaldo:** Spleeter, para los teléfonos que no dan para el principal.
- **Por capacidad:** los dos quedan clasificados en Estudio según lo que aguante el teléfono, con la misma filosofía
  del centro de modelos.

## Cómo se midió

- **Canciones:** las 3 locales del S25 (Tití Me Preguntó, Hecha Pa' Mí y Que Vuelvas), 30 s desde el segundo 60.
- **Referencia:** Demucs htdemucs, el mejor separador abierto. Es muy pesado para un teléfono, así que se usa como
  vara en el PC. El SDR es en dB contra su voz: más alto es mejor. No es una verdad absoluta, pero ordena bien.
- **Velocidad (RTF):** tiempo de separar ÷ duración del audio, en el PC con 4 hilos. En el S25, el KARA midió
  0,67× en la app; da unas 2,7 veces el número del PC.
- **Los parámetros de cada modelo:** n_fft se probó con todos los valores posibles. Los metadatos de sherpa-onnx
  no siempre coinciden con los de UVR (KARA dice 4096 y UVR usa 5120); el valor equivocado cuesta de 1 a 3 dB.

## Resultados

| Modelo | Tamaño | SDR de la voz (promedio de 3) | RTF en el PC | Estimado en el S25 (canción de 3:30) |
|---|---|---|---|---|
| **UVR MDX-Net KARA** (n_fft 5120) | 28 MB | **11,9 dB** | 0,25 | ~2,5 min (medido: 0,67×) |
| UVR MDX-Net 1_9703 (n_fft 5120) | 28 MB | 11,2 dB, irregular entre canciones | 0,20 | ~2 min |
| UVR MDX-Net Voc FT (n_fft 6144) | 63 MB | 12,6 dB | 0,60 | ~5,5 min |
| UVR MDX-Net Inst HQ 4 (n_fft 6144) | 56 MB | 11,0 dB | 0,48 | ~4,5 min |
| **Spleeter 2 pistas fp16** | 33 MB | **9,7 dB** | **0,025** | **~15 s** |

**Lectura:**
- **KARA es el de 28 MB que gana.** Es el mismo modelo del karaoke de la 2.0, así que quien ya lo bajó para el
  karaoke no baja nada más. Separó bien la voz entera en las 3 canciones: el miedo de que dejara los coros pegados
  a la música no se vio aquí. Falta confirmarlo con una canción de muchos coros.
- **Voc FT** gana solo 0,7 dB y cuesta 2,4 veces más. No vale la pena en un teléfono promedio.
- **Spleeter** es unas **10 veces más rápido** que KARA y pierde unos 2 dB. Sirve de respaldo de verdad.

## Lo que hay que saber de Spleeter

- El empaquetado de **sherpa-onnx devuelve basura** con Spleeter, en fp16 y en fp32. Las pistas salen con un
  volumen 30 veces más alto y se cancelan entre ellas.
- **Corrido a mano funciona:** STFT 4096/1024, magnitudes de 1024 bins en tramos de 512, máscara de Wiener, y arriba
  de 11 kHz se repite la última máscara. Script: `scratchpad/m1/scripts/spleeter_propio.py`.
- En la app hay que usar ese camino propio, no la API de separación de sherpa-onnx. O al menos verificar primero
  que la versión de Android no tenga el mismo problema.

## Para escuchar

`Life Music/Pruebas/mashups-m1/escuchar.html` tiene 20 s de cada canción: original, principal (voz y música) y
respaldo (voz y música). No va al repositorio porque son canciones con derechos.

## Siguiente: M2 «Preparar canciones» (solo música local)

- **Clasificación por capacidad,** con la prueba que ya usan Nova y el karaoke:
  - teléfono que pasa → KARA;
  - el que no pasa pero aguanta lo básico → Spleeter;
  - gama muy baja → no se ofrece, y se dice claro.
- **Estudio:**
  - los dos modelos aparecen en el centro de modelos con su espacio y su crédito;
  - el KARA se comparte con el karaoke.
- **Cuándo corre:** en segundo plano con notificación, de a una canción. Lo pesado, ojalá con el teléfono cargando.
- **Qué guarda:** la voz y la música en AAC, unos 8 MB por canción.
- **La escucha del mashup es liviana:** dos audios ya separados, como la mezcla de hoy.
- **Licencias:** UVR ya está acreditado en Estudio. Spleeter (Deezer) es MIT y hay que sumarlo a los créditos.
