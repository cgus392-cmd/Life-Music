-- Cabina del equipo de Life Music (lifemusic.app/equipo). Ficha: docs/versiones/1.3.3/equipo.md.
-- La base sale VACIA: solo tablas y configuracion. Nada de datos de ejemplo (pedido de CG, 10-10).
-- Ningun codigo ni sesion se guarda en claro: solo su huella (HMAC o SHA-256).

create table if not exists personas (
  id text primary key,
  nombre text not null,
  rol text not null check (rol in ('duenio', 'operaciones', 'supervisor')),
  estado text not null default 'invitada' check (estado in ('invitada', 'prueba', 'activa', 'pausa', 'terminada')),
  numero integer not null unique,          -- Nº del pase
  desde text,                              -- vigencia del cargo, AAAA-MM-DD (hora de Colombia)
  hasta text,
  dias text not null default '1,2,3,4,5',  -- 1 = lunes ... 5 = viernes; nunca sabado ni domingo
  entrada text not null default '08:00',
  salida text not null default '17:00',
  creada_en integer not null,
  terminada_en integer
);

create table if not exists invitaciones (
  id text primary key,
  persona_id text not null,
  huella text not null unique,
  creada_en integer not null,
  vence_en integer not null,
  vista_en integer,
  usada_en integer,
  anulada_en integer
);

create table if not exists sesiones (
  id text primary key,
  persona_id text not null,
  huella text not null unique,
  aparato text,
  creada_en integer not null,
  vista_en integer not null,
  vence_en integer not null,
  cerrada_en integer
);

create table if not exists codigos_aparato (
  huella text primary key,
  persona_id text not null,
  creada_en integer not null,
  vence_en integer not null,
  usado_en integer
);

create table if not exists actas (
  id text primary key,
  persona_id text not null unique,
  folio text not null,
  version integer not null,
  texto text not null,
  nombre text not null,
  documento text,
  firma text not null,                     -- SVG armado por el servidor
  firma_tipo text not null check (firma_tipo in ('dibujada', 'escrita')),
  firmada_en integer not null,
  aparato text,
  huella text not null,
  autoriza_datos_en integer not null,
  politica_version integer not null,
  mayor_edad integer not null,
  contrafirma text,
  contrafirmada_en integer,
  huella_final text
);

create table if not exists ideas (
  id text primary key,
  numero integer not null,
  titulo text not null,
  para_que text,
  version text,
  fase integer not null default 1,         -- 1 idea, 2 planteamiento, 3 luz verde, 4 ejecucion, 5 pruebas, 6 lanzada
  descartada integer not null default 0,
  responsable_id text,
  autor_id text not null,
  enlace text,
  creada_en integer not null,
  actualizada_en integer not null
);

create table if not exists incidencias (
  id text primary key,
  titulo text not null,
  detalle text,
  gravedad text not null check (gravedad in ('alta', 'media', 'baja')),
  abierta integer not null default 1,
  autor_id text not null,
  creada_en integer not null,
  cerrada_en integer
);

create table if not exists notas (
  id text primary key,
  autor_id text not null,
  titulo text not null,
  texto text,
  fijada integer not null default 0,
  creada_en integer not null
);

create table if not exists accesos (
  id text primary key,
  persona_id text not null,
  plataforma text not null,
  permiso text,
  estado text not null check (estado in ('pedido', 'concedido', 'retirado')),
  motivo text,
  creado_en integer not null,
  actualizado_en integer not null
);

create table if not exists revision (
  semana text not null,
  item integer not null,
  persona_id text not null,
  hecho_en integer not null,
  primary key (semana, item)
);

create table if not exists solicitudes (
  id text primary key,
  persona_id text not null,
  tipo text not null check (tipo in ('corregir', 'borrar')),
  detalle text,
  creada_en integer not null,
  respondida_en integer
);

create table if not exists bitacora (
  id integer primary key autoincrement,
  persona_id text,
  accion text not null,
  detalle text,
  aparato text,
  creada_en integer not null
);

-- Intentos fallidos por conexion (huella de la IP con pimienta), en ventanas de 15 minutos.
-- Se borran a las 24 horas.
create table if not exists intentos (
  ip text not null,
  ventana integer not null,
  fallos integer not null default 0,
  primary key (ip, ventana)
);

create table if not exists config (
  clave text primary key,
  valor text not null
);

create index if not exists idx_sesiones_persona on sesiones (persona_id);
create index if not exists idx_bitacora_fecha on bitacora (creada_en);
create index if not exists idx_ideas_fase on ideas (fase, descartada);

-- Configuracion, no datos de ejemplo. Festivos de Colombia (Ley 51 de 1983); CG los puede editar.
insert or ignore into config (clave, valor) values
  ('festivos', '2026-01-01,2026-01-12,2026-03-23,2026-04-02,2026-04-03,2026-05-01,2026-05-18,2026-06-08,2026-06-15,2026-06-29,2026-07-20,2026-08-07,2026-08-17,2026-10-12,2026-11-02,2026-11-16,2026-12-08,2026-12-25,2027-01-01,2027-01-11,2027-03-22,2027-03-25,2027-03-26,2027-05-01,2027-05-10,2027-05-31,2027-06-07,2027-07-05,2027-07-20,2027-08-07,2027-08-16,2027-10-18,2027-11-01,2027-11-15,2027-12-08,2027-12-25'),
  ('politica_version', '1'),
  ('acta_version', '1'),
  ('version_objetivo', '1.3.3'),
  ('retencion_meses', '12');
