# Ficha técnica · Reanudar al encender el carro

**Versión:** carro 0.3.0 · **Fase:** 2 (planteamiento técnico) · **Estado:** aprobada por CG el 2026-10-10 (fase 3, ejecución)
**Idea:** 16 de la hoja de ruta («La app arranca sola con el radio y sigue donde quedó»)

## 1. Qué es

Al encender el carro, Life Music for Car vuelve sola a la pantalla y la canción sigue donde iba, con el volumen
subiendo suave. No hay que buscar la app ni tocar play.

## 2. Cómo lo vive la persona

1. Va escuchando música y apaga el carro.
2. Al encenderlo de nuevo, en unos segundos aparece la app y la canción sigue desde donde quedó, con un margen
   de 10 s como mucho. El volumen sube en 2 s, sin golpe. Life Line dice «Retomando donde lo dejaste».
3. Si había pausado la música antes de apagar, la app se abre pero no suena: se respeta su pausa.

**Ajustes › «Al encender el carro»:** tiene tres pastillas.
- **Seguir sonando** (de fábrica).
- **Solo abrir la app.**
- **Nada.**

**Cuándo no se reanuda**, aunque esté en «Seguir sonando»:
- hay una llamada en curso;
- otra app ya está sonando, como la radio FM del propio aparato: no se le roba el sonido;
- la cola está vacía.

## 3. Los dos tipos de radio

| Tipo | Qué pasa al apagar | Al encender | Señal que recibe la app |
|---|---|---|---|
| **Se apaga del todo** | Se corta la corriente | Arranca de cero en 20 a 40 s | `BOOT_COMPLETED` |
| **Se duerme** (la mayoría de los chinos modernos) | Pantalla apagada y app congelada en memoria | Vuelve en 2 a 5 s | Pantalla encendida (`SCREEN_ON`) y, según la marca, un aviso de ACC propio |

La app cubre los dos casos, y la bitácora dice cuál es el del radio de CG.

## 4. Decisiones técnicas

### 4.1 Arranque en frío

- `EncendidoReceiver` en el manifiesto del carro, escuchando `BOOT_COMPLETED`. El permiso
  `RECEIVE_BOOT_COMPLETED` ya está en el manifiesto común.
- En Android 8 y 9 una app puede abrir su pantalla desde segundo plano; la restricción llegó en Android 10.
  Por eso el receptor abre `CarActivity` con el extra `desdeEncendido = true`.
- Si algún radio trae Android 10 o más, no se abre la pantalla: se arranca solo el servicio, que sí está
  permitido desde `BOOT_COMPLETED`, y la música suena igual.

### 4.2 Radio que se duerme

- `VigiaDelEncendido` se registra en tiempo de ejecución desde `App` cuando `ES_CARRO` y vive lo que vive el
  proceso. Escucha:
  - `SCREEN_OFF` y `SCREEN_ON`, que existen en todos los radios;
  - los avisos de ACC de las marcas más comunes. Los nombres exactos se confirman con el radio de CG en la
    tanda 8.
- Desde Android 8, esos avisos de fábrica **no llegan a un receptor del manifiesto** porque son difusiones
  implícitas. Por eso se registran en tiempo de ejecución.
- Solo se reanuda si el carro estuvo apagado **30 s o más**. Así no se dispara cuando la pantalla se apaga sola
  un momento.

### 4.3 Saber si sonaba al apagar

Al cortar la corriente no hay tiempo de guardar nada. Por eso se usa un «reloj de vida»:
- cada 15 s, mientras la app vive, se guarda `CarroVivoKey = ahora`;
- si está sonando, también se guarda `CarroSonabaKey = ahora`.

Al volver se compara: **sonaba al apagar si (vivo − sonaba) ≤ 20 s**. Si la persona pausó minutos antes de
apagar, la diferencia es grande y no se reanuda. Sirve igual para los dos tipos de radio.

La cola y la posición ya las guarda el servicio cada 10 s mientras suena (`saveQueueToDisk`), y al arrancar
las restaura en pausa (`playWhenReady = false`). Eso no se toca.

### 4.4 Reanudar

1. Se espera a que el servicio restaure la cola.
2. Se esperan **4 s más**, porque el radio suele poner su propio lanzador o la radio FM en esos primeros
   segundos.
3. Se comprueba que no haya llamada (`AudioManager.mode`) ni otra música sonando (`isMusicActive`).
4. Volumen del reproductor a 0, `play()` y una rampa de 0 a 1 en 2 s, con la misma curva seno del fundido de
   la 1.3.1.
5. Life Line dice «Retomando donde lo dejaste» durante 8 s.

Cada paso queda en la bitácora, también el motivo cuando no se reanuda («había otra música sonando»).

## 5. Riesgos

| Riesgo | Qué tan probable | Cómo se cubre |
|---|---|---|
| El radio mata las apps al dormirse («limpieza al apagar», típica de FYT) | Medio | La bitácora lo muestra; se le pide a CG poner la app en la lista blanca del radio |
| El radio también abre «la última app» por su cuenta | Alto | No hace daño: `singleTask` evita una segunda pantalla y el reanudar solo corre una vez por encendido |
| La radio FM del aparato toma el audio al encender | Medio | La espera de 4 s y la comprobación de `isMusicActive` |
| Arranca a todo volumen | Bajo | La rampa de 2 s, y la red de seguridad: si algo falla, el volumen vuelve a 1 |
| Sin red al encender (el hotspot del teléfono tarda) | Alto | Si la canción no está en caché, Life Line dice «cargando» y suena al llegar la red; la precarga (ficha 4) hace que casi siempre esté guardada |

## 6. Pruebas

**En el emulador:**
1. Reproducir y luego `adb -s emulator-5554 reboot`: la app se abre sola y sigue sonando donde iba.
2. Lo mismo, pero pausando antes de reiniciar: se abre y no suena.
3. Para simular el radio que se duerme: `input keyevent 26` (pantalla apagada), esperar 40 s y otra vez 26.
   Debe reanudar.
4. Igual que la 3, pero esperando solo 10 s: no hace nada.
5. Con otra app sonando: no le quita el sonido.
6. Con «Solo abrir la app» y con «Nada».

**En el radio, las hace CG:** apagar y encender el carro sonando y en pausa, y mirar la bitácora.
