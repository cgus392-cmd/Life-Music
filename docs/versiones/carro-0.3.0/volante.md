# Ficha técnica · Botones del volante y del radio

**Versión:** carro 0.3.0 · **Fase:** 2 (planteamiento técnico) · **Estado:** aprobada por CG el 2026-10-10 (fase 3, ejecución)
**Idea:** 15 de la hoja de ruta («Siguiente, anterior y pausa desde los botones físicos»)

## 1. Qué es

Con los botones del volante y los del frente del radio, la persona puede pasar a la siguiente, volver a la
anterior y pausar o seguir. Funciona aunque la app esté detrás de Waze o de Maps.

## 2. Qué hay hoy

Puede que **ya funcione en parte**. La app tiene una sesión de medios (Media3 `MediaSession`) y el
`MediaButtonReceiver` en el manifiesto común. Android le manda a la última app que sonó las teclas estándar:

| Tecla | Código |
|---|---|
| Siguiente | 87 |
| Anterior | 88 |
| Play/pausa | 85 |
| Play | 126 |
| Pausa | 127 |
| Gancho del auricular | 79 |

**La pregunta 2 del README es justamente esa:** si con la 0.2.0 los botones ya cambian de canción.

Hay tres casos en que no llegan:
1. **El radio manda las teclas solo a la ventana que está al frente**, no a la sesión de medios.
2. **El radio usa sus propios avisos** en vez de teclas. Pasa, por ejemplo, en los Microntek (MTCD/MTCE).
3. **El radio tiene un ajuste de «app de música para el volante».** Hay que elegir ahí Life Music for Car. La
   app ya se declara como app de música (`APP_MUSIC`).

## 3. Decisiones técnicas

### 3.1 Probador de botones (lo primero)

En **Ajustes › Botones del volante › Probar** hay una pantalla grande que dice «Presiona un botón del volante».
Por cada pulsación muestra:
- qué botón es («Siguiente»);
- su código;
- **por dónde llegó**: sesión de medios, ventana o aviso del fabricante.

También lo anota en la bitácora. Con esto CG prueba su radio y me dice qué llega, sin cables ni `adb`.

### 3.2 Las tres vías

- **Sesión de medios:** ya existe. Se le pone un registro (`onMediaButtonEvent` del callback de la sesión) solo
  para la bitácora y el probador; el comportamiento no cambia.
- **Ventana:** `CarActivity.dispatchKeyEvent` atiende las teclas de medios cuando la app está al frente y las
  pasa al reproductor.
- **Avisos del fabricante:** un receptor en tiempo de ejecución con la lista de avisos conocidos. La lista
  exacta se ajusta en la tanda 8 con lo que muestre el probador en el radio de CG.

### 3.3 Una sola acción por pulsación

Una misma pulsación puede llegar por dos vías, por ejemplo por la ventana y por la sesión. Se ignora la
repetición si es la misma tecla en menos de 300 ms.

### 3.4 Qué hace cada botón

- **Siguiente:** pasa a la siguiente.
- **Anterior:** como en todo Android, si la canción lleva más de 3 s vuelve al inicio; si no, va a la anterior.
- **Play/pausa:** alterna.
- **Play y pausa sueltos:** hacen lo que dicen.
- **Volumen:** lo maneja el radio, no la app.
- **SRC/MODE:** lo maneja el radio, no la app.

**Fuera de la 0.3.0:** gestos extra, como mantener «siguiente» para adelantar o doble toque de play para pasar
de canción. Si CG los quiere, van en la siguiente versión.

## 4. Riesgos

| Riesgo | Qué tan probable | Cómo se cubre |
|---|---|---|
| El radio usa avisos con nombres que no conocemos | Medio | El probador y la bitácora; se ajusta en la tanda 8 |
| El radio se queda las teclas para su reproductor de fábrica | Bajo-medio | No hay arreglo desde la app; el probador al menos lo deja claro |
| Doble acción por la misma pulsación | Medio | El filtro de 300 ms |
| Que otra app de música se quede con los botones | Medio | Android las manda a la última que sonó: al sonar Life Music las recupera |

## 5. Pruebas

**En el emulador:**
1. `adb -s emulator-5554 shell input keyevent 87` con la app al frente: pasa a la siguiente. Lo mismo con 88 y
   con 85.
2. Lo mismo con la app detrás (después de Inicio del sistema): sigue funcionando por la sesión de medios.
3. `cmd media_session dispatch next`: funciona.
4. El probador muestra cada tecla con su vía, y una pulsación no se cuenta dos veces.

**En el radio, las hace CG:**
1. Abrir el probador y pulsar cada botón del volante y del frente.
2. Mandarme una foto de la bitácora.
3. Repetir con Waze o Maps al frente.
