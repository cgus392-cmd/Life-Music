-- 2.0 · La cabina pasa a ser la Sala de control (aprobado por CG el 18-10).
-- Ficha: docs/versiones/2.0/cabina-sala-de-control.md.
-- Solo agrega: ninguna tabla ni dato existente se borra. Lo de contratacion (pases,
-- acta, jornada, accesos) sigue en la base y vuelve con el interruptor del dueño.

-- La capa de cada idea: 1 funciones, 2 desarrollo, 3 optimizacion, 4 mantenimiento.
alter table ideas add column capa integer;

-- Un canal de la consola por frente de trabajo.
create table if not exists frentes (
  id text primary key,                     -- app, tv, carro, web, diseno
  nombre text not null,
  orden integer not null,
  version text,
  estado text not null default 'en-marcha' check (estado in ('en-marcha', 'espera-cg', 'en-pausa', 'lista')),
  avance text,                             -- lo ultimo que se hizo
  espera text,                             -- que espera a quien
  sesion text,                             -- la sesion que lo trabaja
  actualizado_en integer not null
);

-- Las tomas: cada APK para probar. La actual es la ultima sin cerrar.
create table if not exists tomas (
  id text primary key,
  numero integer not null unique,
  version text not null,
  apk text not null,
  trae text,
  enlace text,                             -- descarga (version de prueba en GitHub); vacio hasta que CG publique
  creada_en integer not null,
  cerrada_en integer,
  publicada_en integer
);

-- Los puntos de la hoja de pruebas de cada toma.
create table if not exists pruebas (
  id text primary key,
  toma_id text not null,
  orden integer not null,
  grupo text not null check (grupo in ('nuevo', 'volver', 'siempre')),
  idea_id text,
  titulo text not null,
  pasos text,
  espera text,                             -- lo que debe pasar
  requiere text                            -- tv, carro, descargada... (para «no pude»)
);
create index if not exists idx_pruebas_toma on pruebas (toma_id, orden);

-- Lo que marca cada supervisor en cada punto (el ultimo vale).
create table if not exists resultados (
  prueba_id text not null,
  persona_id text not null,
  estado text not null check (estado in ('funciona', 'falla', 'no-pude')),
  nota text,
  telefono text,                           -- el telefono con que lo probo, tal como estaba
  incidencia_id text,
  marcado_en integer not null,
  primary key (prueba_id, persona_id)
);

-- El telefono de cada supervisor: la regla de CG es que todo funcione en todos.
create table if not exists telefonos (
  persona_id text primary key,
  marca text not null,
  modelo text not null,
  android text not null,
  actualizado_en integer not null
);

-- Lo terminado, para la bitacora de la sala.
create table if not exists avances (
  id text primary key,
  frente text,
  titulo text not null,
  detalle text,
  hecho_en integer not null
);

-- Sin contratacion, quien entra acepta la politica de datos con una casilla (Ley 1581)
-- en vez de firmar el acta.
create table if not exists aceptaciones (
  persona_id text primary key,
  politica_version integer not null,
  mayor_edad integer not null,
  aparato text,
  aceptada_en integer not null
);

insert or replace into config (clave, valor) values ('version_objetivo', '2.0');
insert or ignore into config (clave, valor) values ('equipo_contratacion', '0');
