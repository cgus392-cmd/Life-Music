# Life Music 2.0.2 · Mantenimiento

> **Estado:** plan para aprobar (07-11-2026). Fuentes: la lista de CG y de los usuarios, y la revisión automática
> (lint, peso del APK y cierres del S25).
> Va **un bloque a la vez**, en tandas cortas que se prueban en el S25. Las ideas nuevas van a «Después».

## Bloque 1 · Canciones que desaparecen (crítico: se pierden datos)

**Lo que pasa:** canciones con «me gusta», guardadas en la biblioteca o en listas desaparecen solas.

**El origen** (`utils/SyncUtils.kt`), cuando la cuenta de YouTube está conectada y se sincroniza:
1. **Biblioteca:** toda canción de la biblioteca que no esté en la lista de YouTube **se saca de la biblioteca**
   (`executeSyncLibrarySongs`). Lo que guardaste en la app y nunca llegó a YouTube se borra en la siguiente
   sincronización. Ni siquiera se salvan las canciones locales.
2. **Me gusta:** una canción con «me gusta» que no está en YouTube se queda solo si su fecha es más nueva que la última
   sincronización; si no, se le quita el «me gusta». **La fecha se compara mal:** la hora de Colombia se lee como si
   fuera UTC, 5 horas de diferencia. Lo que diste «me gusta» en las horas antes de una sincronización se pierde.
3. **Sin freno de seguridad:** si YouTube devuelve la lista a medias (red lenta, página cortada), la app borra todo lo
   que no vino. Por eso a veces se van muchas de una.
4. Las listas de reproducción siguen el mismo patrón (`songsToRemove`); hay que revisarlo igual.

**El arreglo:**
- Nunca borrar lo local solo porque no vino de YouTube: primero se intenta **subirlo**.
- Corregir la hora.
- Poner un **freno**: si la respuesta trae muchas menos canciones de lo esperado, no se borra nada.
- No tocar nunca las canciones locales.

## Bloque 2 · Nova › Personalizar: el texto se desordena

**Lo que pasa:** al escribir el nombre de Nova o «cómo te dice», el cursor salta al inicio y las letras se mezclan.

**El origen** (`ui/component/NovaPersonalizar.kt`): cada letra se guarda en el almacenamiento y el campo se redibuja con
el valor viejo mientras llega el nuevo.

**El arreglo:** el campo escribe en memoria y guarda al terminar, como cualquier campo normal. Es rápido.

## Bloque 3 · El recorrido: los interruptores no aplican

**Lo que pasa:** en el recorrido de la 2.0, Aurora, Estudio y Bandas salen prendidos, pero la función no se activa
hasta prenderla a mano.

**Lo que se sabe:** en el código, todos leen «prendido de fábrica», así que hay algo más. **Hay que reproducirlo** en el
emulador con una instalación limpia y encontrar dónde se pierde.

## Bloque 4 · El ícono de Cast se deforma al conectar

**Lo que pasa:** con el TV conectado, el ícono de transmitir arriba en Inicio sale deformado.

**Falta:** la imagen del usuario. Después, revisar el dibujo del ícono «conectado» y su tamaño.

## Bloque 5 · Rendimiento en teléfonos viejos (el más grande)

**Lo que pasa:** en teléfonos de gama baja la app va pesada y calienta.

**El plan:**
1. **Medir primero:** el arranque en frío, los cuadros lentos al deslizar Inicio y el reproductor, y la memoria. En el
   emulador con un perfil de gama baja y en el S25, como referencia.
2. **Modo ligero automático:** la app ya mide el teléfono en Estudio (`NovaCapacidad`). Si el teléfono es de gama
   baja, que apague solita lo pesado: desenfoques, Liquid Glass, Canvas animado, fondo reactivo y animaciones grandes.
   El usuario puede volver a prenderlo.
3. **Arranque:** revisar lo que se carga al abrir la app y que no hace falta de inmediato.
4. Sin sacrificar funciones, solo dosificarlas según el equipo.

## Bloque 6 · Errores de código (de la revisión automática)

- El **widget** usa un tema que no existe en Android 8 y 9.
- **Liquid Glass** usa una función de Android 13: confirmar que nunca se llama en teléfonos viejos.
- **4 menús** (el del artista y otros) lanzan tareas en cada redibujado: lentitud o acciones repetidas.
- Textos con formato mal armado: el de «añadido a la lista» en azerí, el error del servidor y los plurales en
  francés.

## Bloque 7 · Peso y limpieza

- La **universal sin librerías x86** (solo emuladores): de 126 MB a unos 80 MB.
- Quitar **535 recursos sin uso** y **1.461 traducciones sobrantes**: menos peso, y los avisos dejan de tapar los
  errores de verdad.
- Más adelante, el diccionario japonés (13 MB) se podría bajar solo cuando haga falta.

## Orden propuesto de tandas

| Tanda | Bloques | Por qué primero |
|---|---|---|
| 1 | 1 + 2 | Se pierden datos del usuario; el de Nova es rápido |
| 2 | 3 + 4 | Lo que se ve mal al estrenar la 2.0 |
| 3 | 6 + 7 | Errores de código y peso, sin riesgo para el usuario |
| 4 | 5 | Rendimiento: medir, modo ligero y arranque |

## Después (cuando todo esto esté cerrado)

- **Nova:** estudiar cómo anuncia y cómo hace las transiciones el DJ de Spotify, y mejorar a Nova con eso.
