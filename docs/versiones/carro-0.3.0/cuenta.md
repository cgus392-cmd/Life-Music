# Ficha técnica · Iniciar sesión con YouTube Music

**Versión:** carro 0.3.0 · **Fase:** 2 (planteamiento técnico) · **Estado:** aprobada por CG el 2026-10-10 (fase 3, ejecución)
**Idea:** 22 de la hoja de ruta («Tu biblioteca y tus recomendaciones, como en el teléfono»)

## 1. Qué es

**Ajustes › Tu cuenta › «Iniciar sesión con YouTube Music».** Con la sesión iniciada:
- **Inicio** muestra las recomendaciones de la persona. Sin cuenta, la primera página de YouTube llega vacía y
  se rellena con Novedades.
- **Biblioteca** trae sus Me gusta, listas, álbumes y artistas de YouTube Music.
- Los Me gusta que se den en el carro, también con el doble toque, **llegan a la cuenta** y aparecen en el
  teléfono.
- El saludo de Inicio usa su nombre: «Buenas tardes, Camilo».

## 2. Cómo lo vive la persona

1. Con el carro estacionado, en **Ajustes › Tu cuenta**, toca «Iniciar sesión».
2. Se abre la página de Google a pantalla completa, en horizontal, y escribe su correo y su contraseña con el
   teclado del radio. Si tiene la verificación en dos pasos, le llega al teléfono como siempre.
3. Al terminar ve «Listo, Camilo». La app se reinicia sola y la música sigue donde iba, gracias a la ficha de
   reanudar.
4. La fila pasa a mostrar el nombre y el correo, con «Sincronizar ahora» y «Cerrar sesión».

## 3. Decisiones técnicas

### 3.1 La página de Google

- `Cuenta.kt` en el carro usa la misma lógica del `LoginScreen` del teléfono:
  1. un `WebView` abre `accounts.google.com/ServiceLogin?continue=music.youtube.com`;
  2. al llegar a `music.youtube.com` toma la cookie;
  3. con JavaScript lee `VISITOR_DATA` y `DATASYNC_ID`;
  4. valida con `YouTube.accountInfo()`.
- Guarda **las mismas claves** que el teléfono: `InnerTubeCookieKey`, `VisitorDataKey`, `DataSyncIdKey`,
  `AccountNameKey`, `AccountEmailKey` y `AccountChannelHandleKey`. `App` ya las lee al arrancar, así que el
  motor no cambia.
- Se reinicia como en el teléfono. Es lo más seguro: los clientes de YouTube se configuran al arrancar. Antes de
  reiniciar se guarda la cola y se marca «sonaba» para que la música siga.
- Es un archivo propio del carro y no se reutiliza la pantalla del teléfono, porque esa depende de su
  `NavController` y su barra superior.

### 3.2 Sincronizar la biblioteca

- La Biblioteca del carro lee la base de datos local. En el teléfono, esa base la llenan sus pantallas de
  biblioteca (los ViewModels llaman a `SyncUtils`), y el carro no las usa.
- El carro llama a `SyncUtils.performFullSyncSuspend()` en tres momentos:
  - al iniciar sesión;
  - al arrancar, como mucho cada 6 h y solo con la red Estable o Lenta, nunca Deficiente;
  - con «Sincronizar ahora».

### 3.3 Cerrar sesión

Borra las claves y las cookies del `WebView` (`CookieManager.removeAllCookies` y `WebStorage.deleteAllData`).
Las canciones guardadas en el carro se quedan, igual que en el teléfono.

### 3.4 El navegador interno del radio

- Antes de abrir la página se lee la versión del navegador interno (`WebView.getCurrentWebViewPackage()`, que
  existe desde Android 8).
- Si es muy vieja, se avisa: «El navegador interno de tu radio es antiguo; Google podría no dejarte entrar». Se
  deja intentar igual.
- La versión queda en la bitácora.

### 3.5 Seguridad

La cookie de la sesión vale lo mismo que una contraseña. Se trata así:
- se guarda solo en el DataStore privado de la app, igual que en el teléfono;
- nunca va a la bitácora ni al logcat;
- no pasa por ningún servidor nuestro.

## 4. Riesgos

| Riesgo | Qué tan probable | Cómo se cubre |
|---|---|---|
| **Google no deja entrar desde el navegador viejo del radio** (sin Play Store, el WebView no se actualiza) | **Alto, es el riesgo principal** | Aviso previo con la versión; **plan B** (abajo) si pasa en el radio de CG |
| La página se ve apretada en 1024×600 con el teclado abierto | Medio | `adjustResize` y zoom permitido, como en el teléfono |
| La sincronización tarda o gasta datos con el hotspot | Medio | Solo con red Estable o Lenta y como mucho cada 6 h |
| Reiniciar corta la música | Seguro, pero breve | La música sigue sola por la ficha de reanudar; además se inicia sesión con el carro estacionado |

**Plan B, si Google no deja entrar en el radio de CG:** «Entrar con el teléfono».
1. El carro muestra un QR.
2. Life Music del teléfono, con la sesión ya iniciada, lo escanea.
3. El teléfono le pasa la sesión al carro **por la red local** (el hotspot), sin servidores.

Necesita una actualización de la app del teléfono, se coordina con esa sesión y adelanta parte de la idea 23
(«Pasar la biblioteca con un QR»). **Solo se hace si CG lo aprueba** (pregunta 4 del README).

## 5. Pruebas

**En el emulador.** El Android 8.1 del emulador también trae un navegador interno antiguo, así que sirve para
medir el riesgo antes de ir al radio.
1. Iniciar sesión. **La contraseña la escribe CG**; yo no escribo contraseñas.
2. Inicio con recomendaciones.
3. Biblioteca con los Me gusta de la cuenta.
4. Dar Me gusta en el carro y verlo en el teléfono.
5. Saludo con el nombre.
6. «Sincronizar ahora».
7. «Cerrar sesión»: vuelve a la vista sin cuenta.
8. Reiniciar tras iniciar sesión: la música sigue.

**En el radio, las hace CG:** las mismas, y anotar la versión del navegador interno que muestra la bitácora.
