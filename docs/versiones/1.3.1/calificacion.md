# Ficha técnica · Calificación con estrellas

**Versión:** 1.3.1 (función 5) · **Fase:** 2 (planteamiento técnico) · **Estado:** espera la aprobación de CG
**Idea de:** CG, 2026-10-07 · **Mesa de Ideas:** «Calificación con estrellas y reconocimiento»

## 1. Qué es

Que la gente califique Life Music **sin registrarse en nada**, que CG sepa qué tanto gusta y
qué mejorar, y que eso se vea en público para que la app se conozca, como Vivi y Echo.

**Lo de GitHub, claro desde el principio:** una estrella en GitHub siempre necesita una cuenta
de GitHub. No hay forma limpia de saltarse eso, y las estrellas automáticas o compradas van
contra las reglas de GitHub. Lo que sí hacemos es **invitar** a darla a quien tiene cuenta, en
el momento justo, y tener **nuestra propia calificación pública**, visible en GitHub sin cuenta
gracias a una insignia.

## 2. Cómo lo vive la persona

1. **La pregunta aparece cuando ya conoce la app:**
   - después de 7 días de instalada y 20 canciones escuchadas;
   - como mucho una vez por versión;
   - nunca en la misma sesión que otra ventana (novedades, actualización o prerregistro);
   - si toca «Ahora no», no vuelve en 60 días;
   - si ya calificó, no vuelve a preguntar (puede cambiar su voto en Acerca de).
2. **La hoja «¿Te gusta Life Music?»** tiene 5 estrellas grandes. Al elegir, aparece un
   comentario opcional (500 caracteres como máximo) que cambia según las estrellas:
   - con 4 o 5: «¿Qué es lo que más te gusta?»;
   - con 1 a 3: «¿Qué mejorarías?».
   - Los botones son **Enviar** y **Ahora no**.
3. **Después de enviar:**
   - Con 4 o 5: «¡Gracias!» y dos botones:
     - «**Dale una estrella en GitHub**», que abre el repositorio (para quien tenga cuenta);
     - «**Compártela con alguien**».
   - Con 1 a 3: «Gracias, lo leemos todo. Lo vamos a tener en cuenta.»
4. **En Ajustes › Acerca de** está la fila «Califica Life Music», que muestra «★ 4,8 · 1.234»
   cuando ya hay suficientes calificaciones, y permite calificar o cambiar el voto.

## 3. Decisiones técnicas

### 3.1 Servidor

Es el mismo Worker `lifemusic-comunidad` con D1 del prerregistro: gratis y nunca cobra (ver
`prerregistro.md` §3.1).

```sql
create table calificaciones (
  id text primary key,         -- numero al azar SOLO para calificar (distinto del del prerregistro)
  estrellas integer not null check (estrellas between 1 and 5),
  comentario text,             -- opcional, maximo 500
  version_app text,
  idioma text,
  creada_en text not null default (datetime('now')),
  cambiada_en text not null default (datetime('now'))
);
```

Rutas:

| Método | Ruta | Qué hace |
|---|---|---|
| POST | `/calificar` | `{id, estrellas, comentario, version, idioma}`. Si el id ya existe, lo actualiza: un voto por teléfono, que se puede cambiar. |
| GET | `/calificaciones/resumen` | Pública: `{promedio, total, porEstrella}`. Con menos de 25 calificaciones devuelve solo `{total, visible: false}`. |
| GET | `/insignia.svg` | La insignia «Life Music ★ 4,8 · 1.234», en caché por 1 hora. |

**Privacidad:**
- Se guardan solo las estrellas, el comentario, la versión, el idioma y la fecha. Sin nombre,
  sin correo y sin IP.
- El número al azar de calificar **no es el mismo** que el del prerregistro. Así nunca se puede
  saber quién puso qué estrellas, aunque esa persona esté prerregistrada.
- Los comentarios nunca se publican: solo los lee CG.

### 3.2 El panel de CG

- Te armo una página privada al estilo de la Mesa de Ideas, con:
  - el promedio;
  - cuántas calificaciones hay de cada estrella;
  - la evolución por versión;
  - los comentarios.
- **Los datos los leo yo desde aquí con tu cuenta** (`wrangler d1 execute`) y actualizo la
  página cuando me digas «muéstrame las calificaciones».
- Así no hay ninguna dirección pública con los comentarios ni una clave guardada en la página.

### 3.3 Reconocimiento público

- **README de GitHub:** la insignia
  `![Calificación](https://lifemusic-comunidad.cho--usic.workers.dev/insignia.svg)`. Se sube
  con el «publica» de la versión, y solo cuando ya haya 25 calificaciones.
- **lifemusic.pages.dev:** el promedio y el total, debajo del botón de descarga.
- **En la app:** la fila de Acerca de.

### 3.4 En la app

- `comunidad/Calificacion.kt`:
  - cuándo preguntar: lee la fecha de instalación, las canciones escuchadas de la base local
    y los avisos guardados en DataStore;
  - la llamada al servidor.
- `comunidad/HojaDeCalificacion.kt`: la hoja (ModalBottomSheet), con el mismo estilo de la hoja
  de actualización.
- La fila en `AboutScreen.kt`.
- Se pregunta desde Inicio, con la misma regla de «una ventana por sesión» que ya usan el
  boletín y la actualización.

### 3.5 Fuera de la app (sin código, con tus cuentas)

Te dejo listos los textos para:

- **IzzyOnDroid**, el repositorio tipo F-Droid; antes reviso sus reglas;
- **Obtainium**, con una insignia «Consíguela en Obtainium» en el README;
- **AlternativeTo**;
- **Reddit**, en las comunidades de apps Android;
- **un canal de Telegram**.

No cuentan como función de la versión.

## 4. Riesgos

| Riesgo | Cómo se cubre |
|---|---|
| Que alguien infle las estrellas con un script | Un voto por id; la insignia redondea y espera a 25; si se ve algo raro, CG lo ve en el panel y se limpia |
| Molestar a la gente | Solo después de 7 días y 20 canciones, una vez por versión, 60 días después de «Ahora no» y nunca junto a otra ventana |
| Política de privacidad | La sección nueva, «Calificación», en PRIVACY_POLICY.md |
| Pocas calificaciones al principio | La insignia y el promedio solo se muestran desde 25 |

## 5. Tandas

1. **Servidor:** se hace junto con la tanda 1 del prerregistro, porque es el mismo Worker.
2. **App, unos 25 min:** la hoja, las reglas de cuándo preguntar y la fila en Acerca de.
   Termina con un APK instalado. Para probar hay un atajo solo en las builds de prueba, que la
   hace salir ya.
3. **Web, política y panel, unos 15 min:** el promedio en la landing, la insignia lista para el
   README, PRIVACY_POLICY y tu panel.

## 6. Pruebas

- **Script del servidor:**
  - calificar;
  - cambiar el voto;
  - los límites de 1 a 5 estrellas y de 500 caracteres;
  - el resumen oculto con menos de 25;
  - que la insignia se genere bien.
- **Pruebas de `:app`:** la regla de cuándo preguntar, con 7 días, 20 canciones, una vez por
  versión, 60 días después de «Ahora no» y si ya calificó.
- **En el S25:**
  - la hoja con 5 estrellas y con 2;
  - el botón de GitHub;
  - compartir;
  - cambiar el voto en Acerca de;
  - sin internet;
  - en inglés.
