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
