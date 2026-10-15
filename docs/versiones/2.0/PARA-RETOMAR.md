# Para retomar · Life Music 2.0

Nota de relevo para cualquier asistente (Claude, Gemini u otro) que siga el trabajo. **Léela entera antes de tocar
nada.** Actualizada el 2026-10-15.

## Quién es CG y cómo se trabaja

- CG (Camilo) hace Life Music: un fork educativo de Echo Music, sin fines comerciales y con licencia GPL-3.0.
- **Se le habla en español, con cariño.** Primero la persona, después el trabajo.
- **Ciclo de versión en 4 fases:** ideas → planteamiento técnico (ficha) → ejecución → pruebas. **No se programa
  hasta que CG apruebe la ficha.**
- **Tandas cortas** de 10 a 20 min, que se puedan instalar y probar. **El asistente compila e instala; CG prueba en
  el teléfono.**
- **Cada avance se cierra con «APK para probar: sí (cuál, a qué hora)» o «no todavía».**

## Reglas que no se rompen

1. **Nada de push, tag ni release** sin un «publica» explícito de CG en ese mismo momento. Aprobar el trabajo no es
   aprobar publicarlo.
2. **Nunca generar costos en Cloudflare** («no tengo presupuesto»).
3. **Nunca escribir contraseñas, llaves ni tokens.** La llave de firma vive solo en `local.properties` (no está en
   git): no se lee ni se copia.
4. **Sin telemetría:** los datos solo salen del teléfono cuando la persona hace algo a propósito.
5. **Borrar cualquier cosa** necesita el permiso explícito de CG.
6. **Todo lo visual nuevo va detrás de un interruptor** en Ajustes, apagado de fábrica, hasta que CG lo apruebe en
   el teléfono.
7. **`BrandTitle`** (el título que alterna «Life Music» ↔ logo CG LABS según el ajuste) **no se toca.**
8. **Un solo concepto visual por versión. Nada genérico:** CG ya rechazó dos diseños por genéricos.
9. **Un solo Gradle a la vez, y nada de emulador mientras se compila:** el PC no aguanta.
10. **Las pantallas del teléfono pueden tener contenido personal:** no se leen ni se capturan. Los cierres de la app
    se revisan solo con logcat.
11. **No ofrecer ni tocar la webapp de iPhone** (`webapp/`, archivada).

## Cómo se compila e instala

- Desde `Echo-Music/`: `./gradlew :app:assembleArm64FossRelease` (con R8, unos 5 min).
- El APK sale en `app/build/outputs/apk/arm64Foss/release/`.
- Se instala con `adb -s R5GL64Z9YJR install -r <apk>`. Es el Galaxy S25 de CG, conectado por cable.
- Para ver si la app se cerró sola: `adb -s R5GL64Z9YJR logcat -b crash -d` y `adb shell pidof com.cglabs.lifemusic`.
- **Finales de línea:** `HomeScreen.kt` y `res/values*/life_strings.xml` usan **CRLF**. Hay que respetarlos al
  editar.

## Dónde va el trabajo

**Hecho y guardado:**
- Commit local `e134e3d0` (sin subir): la barra «Aurora» detrás del interruptor `AuroraActivaKey`, apagado.
  - CG la **rechazó por genérica**. De ella sirve `ui/component/Aurora.kt` (colores de la carátula, vidrio y lo
    bajado del Inicio).
- En el S25 está instalada la «2.0 · prueba 2» (Aurora apagada).
- Otros commits locales sin subir: c0786a8a, eb273eea, 61b661a8, e14a70c8 y d2860242.

**Esperando a CG:**
- **Maqueta v2 «Receptor hi-fi»** (barra, Inicio y Nova en un solo mundo): la hizo la sesión de Diseño y **no está
  aprobada**. No se programa nada de la v2 hasta que CG diga.
- **Nova (Modo DJ):** el diseño está **aprobado** (`Brand/maquetas/2026-10-15-nova-dj.png` y `.html`).
  - **La ficha técnica es `docs/versiones/2.0/nova.md`. APROBADA el 15-10.** Las decisiones están en su sección 9.
    **Nova no sigue la v2 «Receptor hi-fi»:** usa la maqueta aprobada adaptada a Material You.
  - **Tanda 0:** el interruptor «Nova, tu DJ (vista previa)» en Ajustes › Reproductor y la voz del teléfono
    en el cambio natural de canción, con la música al 30 %. El código está en `app/src/main/kotlin/com/cglabs/lifemusic/nova/`
    y los ganchos en `MusicService` (busca «Nova»). **Hecha y probada** (commit 7ef9cb38).
  - **Tanda 1 hecha** (commit 100c579b): la tabla `senal_nova` (base de datos 47) guarda lo que se salta.
  - **Tanda 2 hecha** (commit cead5784): el motor de bloques (`NovaQueue`, `NovaSesion`) y la cápsula de Nova en el
    Inicio (`ui/component/NovaCapsula.kt`).
  - **Tanda 3 hecha** (commit 46803666): los subtítulos en Life Line.
  - **Tanda 4 hecha** (commit 61f150dc): la estrella y la cabina.
    - Pedido de CG: nada fijo en el Inicio ni en el reproductor. Ver la sección 10 de la ficha.
  - **Tanda 5 hecha** (commit del 15-10, 15:24): Personalizar dentro de la cabina (`NovaPersonalizar.kt`).
  - **Esperando a CG:** que elija el «personaje de luz» de Nova entre las 3 opciones de Diseño (A la mirada, B el neón,
    C la forma viva). No se programa hasta que elija.
  - **Después:** la tanda 6, la voz HD (Piper con sherpa-onnx; antes hay que revisar las licencias de cada voz).

**Más adelante:**
- La casilla «Capa» en la cabina de ideas.
- El correo `datos@lifemusic.app`.
- Proteger `main`.
- El borrado programado.
- La política de analítica.
