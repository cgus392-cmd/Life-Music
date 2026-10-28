# «Bandas» · el lugar social de Life Music (2.0)

**La idea de CG (27-10):** grupos pequeños de amigos que comparten su música y sus listas, con racha y con mascotas que
se crean poco a poco. Un centro de control amistoso y social, pero de música.

**Decisiones de CG (27-10):**
- el nombre es **«Bandas»**: tu grupo de amigos es tu banda;
- se habla **solo con reacciones**, sin chat libre;
- arranca por la **fase 1: Bandas y En vivo**.

**Referencia (Spotify, 2026):**
- **Jam:** escuchar en vivo con cola compartida; solo Premium lo crea.
- **Blend:** lista que mezcla los gustos de hasta 10 personas, con su % de afinidad.
- **Actividad de escucha:** opcional, se prende en Privacidad y es para mayores de 16.
- **Listas colaborativas.**

Nosotros, gratis y sin vender nada.

## Fases

1. **Bandas y En vivo** ← va primero.
2. **La racha de la banda y la primera mascota.** La mascota crece por etapas y nunca se compra.
3. **La mezcla de la banda** (% de afinidad), **las listas de la banda** y **qué está sonando** (opcional).
4. **El resumen del mes** de la banda.

## Fase 1 · ficha técnica (espera el sí de CG)

**Para la persona:**
- **Crear una banda:** nombre y color Pulso. Sale un **código de 6 letras** y un **QR** con
  `lifemusic.app/banda/CODIGO`, que es un App Link: abre la app, o la página de descarga si no la tiene.
- **Entrar:** con el código o el QR. Quien creó la banda **aprueba** a quien entra.
- **Límites:** máximo **8 personas por banda**. Cada persona puede estar en varias bandas.
- **La banda por dentro:** quiénes son (iniciales y color) y un botón **«En vivo»**.
- **En vivo** es el JAM:
  - todos escuchan lo mismo al mismo tiempo, cada uno desde su teléfono, sin mandar audio;
  - la cola es compartida y el anfitrión puede pasar el mando;
  - se reacciona a las canciones con un set fijo de reacciones Pulso.
- **Salir de una banda** y **borrar la banda** (solo quien la creó) están siempre a mano.

**Por dentro:**
- **Servidor propio y gratis:** el relevo `lifemusic-enlace` (Cloudflare, Durable Objects). Se suman:
  - un objeto por banda, que guarda solo el nombre, el color y los miembros (id, nombre, inicial);
  - salas En vivo de varias personas, que no guardan nada y desaparecen cuando se va el último.
- **Se reaprovecha** lo que ya trae la app de Metrolist (`listentogether/`, `Protocol.kt`): la sincronización, la
  espera de carga, el anfitrión y la cola. Cambia el servidor, que hoy es de Metrolist en Polonia (`app/server.json`),
  y se suma la banda alrededor.
- **Identidad sin cuentas:** el teléfono genera un id y un secreto, como el prerregistro. Para no perder las bandas al
  cambiar de teléfono hay un **código de respaldo**.
- **Sin avisos push:** la versión foss no tiene Firebase. «Tu banda está en vivo» se ve al abrir la app, y desde el
  enlace o el QR.

**Reglas que aplican:**
- datos mínimos;
- nada de historial de lo escuchado en el servidor;
- nada de telemetría;
- todo opcional;
- **se actualiza la política de privacidad** antes de publicar;
- edad mínima por definir, como los 16 de Spotify.

**Topes del plan gratis:** el cupo de peticiones es de toda la cuenta y lo comparten el TV, la comunidad y la web.
En vivo manda mensajes solo cuando algo cambia, no cada segundo, y se mide antes de abrirlo a todos.

**Pasos:**
1. **F1:** esta ficha, para que la apruebe CG.
2. **F2:** maqueta con Diseño y marca:
   - Bandas vacío;
   - crear una banda;
   - el código y el QR;
   - entrar a una banda;
   - la banda;
   - En vivo con reacciones.
3. **F3:** el servidor, con pruebas en local y sin publicar.
4. **F4:** la app, en tandas instalables.
5. **F5:** prueba con dos teléfonos. CG consigue el segundo con un probador.

## Estado al 28-10 y cómo seguir (F3)

**La maqueta está APROBADA por CG (28-10):** `Brand/maquetas/2026-10-28-bandas/`.
- El concepto es «la banda es un acorde»: cada persona es una cabeza de nota con su color y su inicial.
- Hay 6 reacciones propias, dibujadas en `bandas.js` (`REACCIONES`): Suena, Me encanta, Otra vez, Súbele, A bailar y
  Escalofrío.
- Bandas entra en el menú de la foto, en el lugar de «Escuchar juntos». En Inicio va la tarjeta «Tu banda está en vivo».

**Lo que ya se sabe para el servidor (F3):**
- **El relevo** `enlace/` (`src/index.js`, 191 líneas) tiene un Durable Object `Cuarto` en SQLite, con la migración
  `v1`. Para las bandas se suma una clase nueva, con su migración `v2`.
- **El cliente de la app** (`listentogether/`, unas 4300 líneas) puede hablar **JSON** (`MessageCodec`, `MessageFormat.JSON`).
  - Mensajes del cliente: `create_room`, `join_room`, `leave_room`, `approve_join`, `reject_join`, `playback_action`,
    `buffer_ready`, `kick_user`, `transfer_host`, `ping`, `request_sync`, `reconnect`, `suggest_track` y
    `update_room_settings`.
  - Mensajes del servidor: `room_created`, `join_request`, `join_approved`, `user_joined`, `user_left`, `sync_playback`,
    `buffer_wait`, `buffer_complete`, `host_changed`, `kicked`, `sync_state`, `reconnected`, `pong`, `error` y los
    demás.
  - El tipo `chat` se ignora, porque la banda solo usa reacciones.
- **Para probar en local:** en este PC, `wrangler dev` falló con «miniflare-email-store: acceso denegado» (lo vio la
  sesión iPhone). Hay que probar con `--persist-to` apuntando al scratchpad.
- **Nada se publica sin el «publica» de CG.**

**Recomendación (28-10):** la sesión General ya va al 76 % de su contexto, y cada mensaje la relee entera. Bandas se
programa mejor en un chat nuevo, «Life Music · Bandas», que arranque leyendo esta ficha.
