-- Base D1 de la comunidad de Life Music. Es idempotente: se puede volver a correr.
--   npx wrangler d1 execute lifemusic-comunidad --remote --file schema.sql
--
-- Nada de aqui se lee en publico: el Worker solo devuelve contadores y promedios.
-- Los nombres, correos y comentarios los consulta CG desde su cuenta con
-- `wrangler d1 execute ... --command "select ..."`.

-- Prerregistro del concurso (1.3.1). Sin lista publica: solo el contador.
create table if not exists prerregistro (
  id             text primary key,               -- numero al azar del telefono, solo para esto
  secreto_hash   text not null,                  -- sha256 del secreto: solo ese telefono puede salirse
  nombre         text not null,
  correo         text not null unique collate nocase,
  mayor_de_edad  integer not null,
  version_app    text,
  registrado_en  text not null default (datetime('now'))
);

-- Calificacion con estrellas (1.3.1). Un voto por telefono, que se puede cambiar.
-- El id es OTRO numero al azar, distinto del del prerregistro: nunca se puede
-- saber quien puso que estrellas.
create table if not exists calificaciones (
  id           text primary key,
  estrellas    integer not null check (estrellas between 1 and 5),
  comentario   text,
  version_app  text,
  idioma       text,
  creada_en    text not null default (datetime('now')),
  cambiada_en  text not null default (datetime('now'))
);

-- Reseñas de lifemusic.app/calificar (22-10). De 1 a 5 notas, con nombre y comentario.
-- Salen en publico solo cuando CG las aprueba desde la Sala de control. El correo es
-- opcional y nunca sale de aqui: es solo para que CG pueda responder.
create table if not exists resenas (
  id           text primary key,
  notas        integer not null check (notas between 1 and 5),
  nombre       text not null,
  correo       text,
  comentario   text,
  estado       text not null default 'pendiente' check (estado in ('pendiente', 'aprobada', 'oculta')),
  creada_en    text not null default (datetime('now')),
  revisada_en  text
);
create index if not exists resenas_por_estado on resenas (estado, creada_en);

-- Freno contra el abuso del formulario: una huella del dia (sha256 de la IP, la fecha
-- y una sal al azar que cambia cada dia) y la hora. Se borra sola a las 24 horas y no
-- se cruza con las reseñas: no se puede saber quien escribio que.
create table if not exists limites (
  huella     text not null,
  creada_en  text not null default (datetime('now'))
);
create index if not exists limites_por_huella on limites (huella, creada_en);

-- Ajustes que CG cambia sin sacar version: fechas, meta, premio y video.
create table if not exists config (
  clave  text primary key,
  valor  text not null
);

insert or ignore into config (clave, valor) values
  ('prerregistro_dias', '6'),
  ('prerregistro_meta', '100'),
  ('prerregistro_premio', 'JBL PartyBox 330'),
  ('prerregistro_video', 'https://lifemusic.pages.dev/concurso/video.mp4');
-- prerregistro_inicio (ISO 8601, p. ej. 2026-10-12T00:00:00-05:00) se pone
-- cuando CG diga «abre el prerregistro». Sin el, no ha empezado.
