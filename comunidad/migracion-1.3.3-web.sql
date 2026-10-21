-- Calificar desde la web (1.3.3). Se corre UNA vez sobre la base que ya existe:
--   npx wrangler d1 execute lifemusic-comunidad --remote --file migracion-1.3.3-web.sql
--
-- En una base nueva no hace falta: schema.sql ya crea las dos cosas. Si se
-- vuelve a correr aqui, el `alter table` falla porque la columna ya esta, y ese
-- error es inofensivo: no deja la base a medias.

-- De donde vino el voto. Los que ya habia son de la app, que era lo unico que
-- podia escribir; por eso el valor por defecto.
alter table calificaciones add column origen text not null default 'app';

-- Freno por IP de los votos que llegan del navegador. No guarda la IP: guarda
-- el sha256 de la IP junto al dia, que no se puede deshacer y caduca solo.
create table if not exists limite_web (
  huella text primary key,
  n      integer not null default 0,
  dia    text not null
);
