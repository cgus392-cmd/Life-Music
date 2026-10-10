# Ficha técnica · Life Line en el carro

**Versión:** carro 0.3.0 · **Fase:** 2 (planteamiento técnico) · **Estado:** aprobada por CG el 2026-10-10 (fase 3, ejecución)
**Idea:** 32 de la hoja de ruta, nueva el 10-10, heredada de la app del teléfono (1.1.5)

## 1. Qué es

Life Line, la línea de letra viva del teléfono, llega al carro:
- En **Sonando**, bajo el artista, va **una sola línea** de la canción que se resalta palabra por palabra
  mientras suena.
- Cuando no hay letra, **habla la app** y cuenta lo que pasa de verdad: cargando, sin red, en pausa, la canción
  despidiéndose.
- Tocar la línea abre la letra completa, que ya existe desde la 2a.

Además trae **tres voces nuevas de carretera**, que salen de las otras funciones de esta versión:

| Voz | Cuándo | Viene de |
|---|---|---|
| «Retomando donde lo dejaste» | 8 s después de reanudar al encender | Reanudar |
| «La siguiente ya va guardada» | Una vez, cuando termina de guardarla | Precarga |
| «Sin señal: sonando de lo guardado» | Mientras no hay internet y suena desde la caché | Precarga e indicador de red |

La regla de Life Line se mantiene: **nunca se inventa un estado**. Cada frase sale de una señal real.

## 2. Cómo se ve en el carro

- **Tamaño:** 22 sp en Sonando, contra 14 sp en el teléfono, para leerla de reojo. En el panel Sonando de
  Inicio, 18 sp. No va en el mini reproductor, porque no cabe bien.
- **Alto fijo:** 40 dp en Sonando, para que al pasar de letra a voz no se mueva nada. Ocupa el espacio de 20 dp
  que hoy hay entre el artista y la barra ondulada, y algo de aire.
- **Color:** lo cantado va en el color de la carátula (resaltado «Dinámico» del teléfono) y lo pendiente en
  blanco al 42 %. En el carro no hay selector de color.
- **Con el panel de Letra abierto**, la línea se esconde, como en el teléfono: si no, sería la misma frase dos
  veces.

**Ajustes:** un interruptor «Life Line», encendido de fábrica. Usa la misma clave del teléfono
(`LifeLineEnabledKey`).

## 3. Decisiones técnicas

### 3.1 Reutilizar, no copiar

- Se usan tal cual, desde `main`, `LifeLine`, `recordarVozLifeLine` y `LifeLineVoiceRow`, con sus frases ya
  traducidas en es y en.
- **El único cambio en el código compartido** es un parámetro `tamano: TextUnit = 14.sp`, con su alto mínimo,
  en `LifeLine`, `LifeLineVoiceRow` y `FilaLifeLine`. El teléfono no lo pasa y queda idéntico.
- `CarActivity` pasa a dar `LocalPlayerConnection`, porque la voz del teléfono lee de ahí.

### 3.2 La parte del carro (`LifeLineCarro.kt`)

- Lee la posición del reproductor cada **100 ms en el perfil Completo y cada 250 ms en Ligero**. El barrido se
  suaviza con la animación, como en el teléfono, y así no castiga a los radios flojos.
- Primero mira si toca una voz de carretera, que dura poco. Si no, deja hablar a la lógica del teléfono: letra
  sincronizada o su voz.
- Las voces de Automix (mezclando, filtrando) no saldrán en la práctica, porque el carro no usa Automix. El
  código no se toca.
- **En Ligero**, el cambio de línea es solo un fundido, sin deslizar, y el texto largo salta en vez de
  desplazarse.

### 3.3 La letra ya llega

El servicio ya busca la letra al empezar cada canción cuando `LifeLineEnabledKey` está encendido, que es lo de
fábrica (`MusicService` ~1061). Respeta el ahorro de datos. No hay que tocar nada.

## 4. Riesgos

| Riesgo | Qué tan probable | Cómo se cubre |
|---|---|---|
| Distrae al conductor | Bajo | Una línea quieta en su sitio, sin parpadeos; el interruptor la apaga |
| Gasta CPU en radios flojos | Bajo | 250 ms y sin deslizamientos en Ligero |
| Mover el código compartido cambia el teléfono | Muy bajo | El parámetro nuevo tiene el valor de hoy de fábrica; se compila también la variante del teléfono |
| Texto largo que no cabe | Medio | Se desplaza para seguir la palabra viva, como en el teléfono |

## 5. Pruebas

**En el emulador:**
1. «Me Porto Bonito» o cualquier canción con letra sincronizada: la línea se barre palabra por palabra.
2. Una canción sin letra: habla la app y cambia de frase cada 12 s.
3. Pausar: voz de pausa.
4. Modo avión: «sin red», en rojo.
5. Abrir Letra: la línea se esconde; al cerrarla vuelve.
6. Apagar el interruptor: desaparece y el diseño queda como en la 0.2.0.
7. Perfil Ligero y perfil Completo.
8. Las tres voces de carretera, junto con las pruebas de reanudar y de precarga.
9. Compilar también la variante del teléfono (`assembleArm64FossDebug`) para comprobar que no cambió.

**En el radio, las hace CG:** si se lee bien de reojo, sobre todo con sol.
