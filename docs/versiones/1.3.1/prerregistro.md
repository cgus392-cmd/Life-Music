# Ficha técnica · Prerregistro del nuevo concurso

**Versión:** 1.3.1 (función 6; CG hizo una excepción a la regla de 5, solo para esta versión)
**Fase:** 2 (planteamiento técnico) · **Estado:** espera la aprobación de CG
**Idea de:** CG, 2026-10-07 · **Mesa de Ideas:** «Prerregistro del nuevo concurso»

## 1. Qué es

Es un **prerregistro**, no el concurso. Sirve para saber cuántas personas están interesadas
en un nuevo concurso de Life Music.

Lo que pidió y decidió CG:

| Tema | Decisión |
|---|---|
| Dónde se entra | Vuelve **la copa a la barra de arriba de Inicio**, en el mismo lugar del reto de septiembre. |
| Qué se pide | **Solo nombre y correo.** |
| Qué se muestra | **Solo el contador** de personas registradas. **Nunca una lista pública.** |
| Premio | Confirmado: **JBL PartyBox 330**. |
| Meta | Mínimo **100 personas** para que el concurso sea posible. |
| Duración | **6 días** de prerregistro. Si no se llega a 100, **sigue abierto hasta llegar**. |
| Quién participa | Las personas prerregistradas son las que participan. |
| Cómo se gana | **Por misiones completadas** (por mérito, no es un sorteo). |
| País y edad | **Sin definir por ahora.** |
| Video | El que mande CG. **Falta la ruta.** |

## 2. Cómo lo vive la persona

1. **En Inicio aparece una copa** en la barra de arriba, con un punto que late mientras el
   prerregistro está abierto.
2. **Al tocarla se abre la pantalla del prerregistro:**
   - el **video de CG** arriba, que se toca para darle play con sonido;
   - «**Nuevo concurso de Life Music**» y el premio, **JBL PartyBox 330**;
   - el contador en grande, «**37 de 100**», con barra de progreso, y debajo «Quedan 4 días»;
   - el texto: «Esto es un prerregistro: el concurso todavía no empieza. Cuando seamos 100,
     arranca un concurso por misiones y participan las personas prerregistradas. Si en 6 días
     no llegamos, el prerregistro sigue abierto hasta llegar.»;
   - el formulario: **Nombre**, **Correo** y dos casillas:
     - «Soy mayor de edad» (recomendada; CG la puede quitar al aprobar);
     - «Acepto las bases y que mi correo se use solo para este concurso», con enlace a las
       bases;
   - el botón **Prerregistrarme**.
3. **Después de registrarse:**
   - «¡Estás dentro!», con el contador al día;
   - el botón «**Invita a un amigo**», que comparte el enlace de la app;
   - «Salir del prerregistro», que borra tus datos del servidor.
4. **Al volver a abrirla**, la app recuerda que ya estás dentro y no vuelve a pedir nada.

## 3. Decisiones técnicas

### 3.1 Servidor: Cloudflare, no Supabase

El Supabase del reto de septiembre **ya no responde**: su dominio no existe, así que el
proyecto está pausado o borrado. Además, en el plan gratis se duerme si pasa 7 días sin uso.

Propuesta: **un Worker nuevo, `lifemusic-comunidad`, con una base D1**, en la misma cuenta de
Cloudflare del relevo del TV. Es el mismo servidor de la calificación (ficha `calificacion.md`).

- **Costo cero.** En el plan gratis, si se pasa un límite, falla esa petición y nunca cobra.
  Los límites son 100.000 peticiones al día, 100.000 escrituras al día y 5 GB, y aquí hablamos
  de cientos.
- **Los correos solo los ve CG,** consultándolos desde su cuenta con `wrangler d1 execute`.
  No hay ninguna dirección pública que los devuelva.

Tablas:

```sql
create table prerregistro (
  id text primary key,          -- numero al azar del telefono (solo para el prerregistro)
  secreto_hash text not null,   -- para que solo ese telefono pueda salirse
  nombre text not null,
  correo text not null unique collate nocase,
  mayor_de_edad integer not null,
  version_app text,
  registrado_en text not null default (datetime('now'))
);
create table config (clave text primary key, valor text not null);
-- inicio, dias (6), meta (100), premio, video_url
```

Direcciones:

| Método | Ruta | Qué hace |
|---|---|---|
| GET | `/prerregistro/estado` | Pública. Devuelve `{abierto, inicio, fin, meta, contador, premio, video}`. Sin nombres ni correos. |
| POST | `/prerregistro` | Registra `{id, secreto, nombre, correo, mayor, acepta, version}` y devuelve el contador. |
| POST | `/prerregistro/salir` | Borra la fila con `{id, secreto}`. |

**Reglas del servidor:**
- **Abierto** quiere decir: ya empezó y (no han pasado los 6 días **o** el contador va por
  debajo de 100). Así se alarga solo, sin que nadie toque nada.
- Valida el nombre (2 a 60 caracteres) y el correo (formato, máximo 120).
- Pide `acepta = true`.
- Un registro por teléfono y uno por correo. Si el correo ya está, contesta «ya estabas
  dentro», sin decir de quién es.
- Solo acepta peticiones de la app o del sitio, con un tamaño máximo.
- **Ante cuentas falsas:** la app es de código abierto y alguien podría registrar correos
  inventados. El concurso es por misiones, así que un registro falso no completa ninguna. Antes
  de empezar, CG puede depurar la lista.

### 3.2 Las fechas las decide el servidor

El inicio está en la tabla `config`. CG dice «abre el prerregistro» y yo pongo la fecha, sin
sacar otra versión. Antes del inicio, la copa no aparece.

### 3.3 El video

- Va en **lifemusic.pages.dev/concurso/video.mp4**, no dentro del APK, para no hacerlo más
  pesado.
- Se comprime a 720p, H.264 y menos de 10 MB.
- La app lo reproduce con Media3 y en caché, y su dirección viene de `/prerregistro/estado`,
  así que se puede cambiar sin actualizar la app.

### 3.4 En la app

- `concurso/Prerregistro.kt` tiene el estado y la llamada al servidor, con OkHttp como
  `ConcursoApi`.
- `concurso/PrerregistroScreen.kt` es la pantalla, en la ruta `prerregistro`.
- `concurso/CopaPrerregistro.kt` es la copa de la barra de Inicio. Va en el mismo lugar que el
  `TrofeoReto` de septiembre (MainActivity), y solo se ve mientras el servidor dice que está
  abierto o hasta que empiece el concurso.
- El id, el secreto y «ya estoy dentro» se guardan en DataStore.
- El reto viejo (`Concurso.ACTIVO = false`) **no se toca**.

### 3.5 En la web

- **`lifemusic.pages.dev/concurso`** muestra el contador, el premio, el video y las bases. El
  registro solo se hace desde la app.
- **`/concurso/bases`** tiene las políticas: qué es un prerregistro, la meta de 100, la duración
  y la extensión, que se gana por misiones, que no hay lista pública, para qué se usa el correo,
  que los datos se borran al terminar y cómo salirse. País y edad: «se publican antes de que
  empiece el concurso».
- **PRIVACY_POLICY.md** gana la sección «Prerregistro del concurso». Hoy dice que nada sale
  hacia CG LABS, y esto sí sale, solo si la persona lo pide.

## 4. Riesgos

| Riesgo | Cómo se cubre |
|---|---|
| Registros falsos o con scripts | Uno por teléfono y por correo, validación y límite de tamaño; el concurso por misiones filtra solo, y CG depura antes de empezar |
| Datos personales (Colombia, Ley 1581) | Permiso explícito, uso único, borrado al terminar y opción de salirse |
| El video pesa mucho | Comprimido y fuera del APK |
| Sin internet | La pantalla lo dice y el botón espera; nada se queda colgado |
| Se publica la app antes de abrir el prerregistro | La copa no aparece hasta la fecha del servidor |

## 5. Tandas

1. **Servidor, unos 25 min.** El Worker y D1 compartidos con la calificación, las tres
   direcciones y las pruebas con un script, como el relevo. **Desplegarlo necesita tu OK**: es
   un servicio nuevo en tu cuenta, gratis.
2. **App, unos 25 min.** La copa, la pantalla, el formulario y salir del prerregistro. Termina
   con un APK instalado.
3. **Web y política, unos 15 min.** `/concurso`, las bases, el video comprimido y
   PRIVACY_POLICY. Se despliega a producción solo con tu orden.

## 6. Pruebas

- **Script del servidor:**
  - registrar;
  - el correo repetido;
  - el teléfono repetido;
  - salirse;
  - que el contador nunca devuelva nombres;
  - abierto o cerrado según las fechas y la meta.
- **En el S25:**
  - la copa aparece y desaparece según las fechas;
  - el video suena;
  - registrarse;
  - reabrir la pantalla y ver «ya estás dentro»;
  - invitar;
  - salirse;
  - sin internet;
  - en inglés.
