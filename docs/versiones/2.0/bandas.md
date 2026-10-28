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
programa mejor en un chat nuevo, «Life Music · Bandas», que arranque leyendo esta ficha. **Hecho:** ese chat existe
desde el 28-10 por la tarde y sigue desde aquí.

## F3 · el servidor, por tandas (propuesta del 28-10, espera el sí de CG)

**Lo que se vio en el código antes de proponer:**
- El cliente de la app manda JSON en marcos binarios y entiende respuestas en texto. El servidor tiene que aceptar los dos.
- **Quien manda la música no recibe su propio eco.** Si le llega `sync_playback`, el anfitrión se corre a sí mismo.
  Solo se reenvía a los demás.
- El anfitrión manda un PLAY cada 10 s mientras suena (`startHeartbeat`), y cada cliente manda un ping cada 25 s.
  Por eso la sala no hiberna mientras suena, y eso es lo que más gasta del plan gratis. Ver «Consumo».
- El invitado espera a `buffer_complete` antes de arrancar una canción nueva. El servidor lo manda cuando todos
  cargaron, o a los 8 s, para que un teléfono lento no frene a todos.
- La maqueta pide estos datos al servidor:
  - por persona: nombre, inicial, color y desde cuándo está;
  - por banda: «5 de 8 · la creó Camila» y quién pide entrar;
  - En vivo ahora: qué suena, quién pone la música y cuántos escuchan;
  - en la cola: «La puso Andrés».
- El código de respaldo «trae tus bandas». Por eso hace falta un índice mínimo de persona a bandas, solo con códigos.

**Cómo queda por dentro:**
- El código va en un archivo nuevo, `enlace/src/bandas.js`. `index.js` solo suma las rutas y el TV no se toca.
- Hay dos clases nuevas en la migración `v2`:
  - `Banda`: un objeto por código. Guarda el nombre, el color y los miembros, y ahí mismo vive la sala En vivo;
  - `Persona`: un objeto por persona, que guarda solo los códigos de sus bandas, para el respaldo.
- **Identidad sin cuentas:**
  - el teléfono inventa un `id` público y un `secreto`;
  - los manda en la cabecera `Authorization`, nunca en la dirección;
  - el servidor guarda solo la huella SHA-256 del secreto;
  - el código de respaldo es ese id y ese secreto, escritos en grupos fáciles de copiar.
- **Rutas HTTP:**
  - `POST /bandas` crea la banda y devuelve el código de 6 letras, con el alfabeto del TV;
  - `POST /banda/COD/pedir` pide entrar;
  - `GET /banda/COD` muestra la banda: a los miembros les da todo; a quien pidió, si está pendiente o rechazado;
  - `POST /banda/COD/aprobar`, `/rechazar` y `/quitar` son solo de quien la creó;
  - `POST /banda/COD/salir` saca a la persona;
  - `DELETE /banda/COD` la borra, solo quien la creó, y cierra En vivo;
  - `GET /yo` da mis bandas con su estado corto, para la tarjeta del Inicio y para restaurar el respaldo.
- **En vivo:** WebSocket en `/banda/COD/envivo`, con el mismo protocolo de `Protocol.kt` en JSON.
  - `create_room` hace anfitrión al primero; con `join_room`, los de la banda entran directo con `join_approved`,
    sin pedir permiso, porque ya son de la banda.
  - El anfitrión manda `playback_action`, y a los demás les llega `sync_playback` con `server_time`.
  - `change_track` abre la espera de carga: `buffer_wait` y `buffer_complete`, con el tope de 8 s.
  - También van `request_sync` → `sync_state`, con la posición calculada; `transfer_host`; las sugerencias;
    `reconnect` con `session_token` y 2 minutos de gracia.
  - Si el anfitrión se va, el mando pasa a quien lleva más tiempo en la sala.
  - Va un mensaje nuevo, `reaccion`, con las 6 de la maqueta. Llega a todos, no se guarda y se permite una por segundo
    por persona.
  - `chat` se ignora. `kick_user` y la aprobación de Metrolist no aplican.
  - El estado de la sala vive en el objeto solo mientras dura y se borra cuando sale el último. Eso incluye qué suena
    y la cola.
- **Topes:**
  - 8 por banda;
  - 5 pedidos pendientes;
  - 10 bandas por persona;
  - el nombre de la banda, de 1 a 30 letras, y el de la persona, de 1 a 20;
  - el color, solo de la lista Pulso.

**Consumo (plan gratis, a medir en F5):**
- El cupo de Durable Objects es de unas 100 000 peticiones y 13 000 GB-s al día. Cada mensaje de WebSocket que
  entra cuenta 1/20 de petición.
- Una sala de 8 personas durante 2 horas:
  - son unas 3100 entradas, que suman unas 155 peticiones, poca cosa;
  - como no hiberna, son unos 920 GB-s. Caben unas 14 salas así al día, compartidas con el TV.
- **Para F4 en la app**, para que la sala pueda dormir entre mensajes:
  - el ping va como texto exacto, y así lo contesta el relevo sin despertar la sala;
  - el latido del anfitrión pasa de 10 s a 30 s.

**Tandas (sin publicar; pruebas en local con `wrangler dev --persist-to` en el scratchpad):**
1. **F3.1 · La banda:**
   - qué entra: la identidad, crear, pedir, aprobar, rechazar, quitar, ver, salir, borrar, `GET /yo` y los topes;
   - cómo se prueba: con un guion de node con 3 personas de mentira.
2. **F3.2 · En vivo:**
   - qué entra: la sala con el protocolo de Metrolist, las reacciones, la espera de carga, la reconexión y el mando;
   - cómo se prueba: con 3 clientes simulados. Uno pone la música y los otros la siguen; uno se cae y vuelve; el
     anfitrión se va.
3. **F3.3 · Cierre:**
   - qué entra: el código de respaldo, el tope de mensajes, `/salud` con la versión nueva y la prueba del TV repetida
     para confirmar que el enlace no cambió;
   - lo que queda: la estimación de consumo, medida.

**Decisiones para CG (va lo recomendado si no dice otra cosa):**
1. Si quien creó la banda se sale, la banda pasa a quien lleva más tiempo en ella. Recomendado: así la banda no
   muere por una persona.
2. Cualquiera de la banda puede prender En vivo, y el primero que entra pone la música. Recomendado: es lo que
   muestra la maqueta.
3. El código es fijo. Cambiarlo, si se filtra, queda para «Después».

**Después (no entra en F3):**
- cambiar el código;
- cambiar mi nombre o mi color;
- la edad mínima, que va en la app y en la política, no en el servidor.
