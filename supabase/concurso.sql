-- Life Music · Reto de la semana (concurso de minutos de escucha)
-- ---------------------------------------------------------------------------
-- Se pega entero en Supabase > SQL Editor > New query > Run. Es idempotente:
-- se puede volver a ejecutar sin romper nada.
--
-- Principios:
--   * La app NUNCA lee ni escribe las tablas directamente. Solo llama a las
--     funciones de abajo (RPC) y lee la vista del ranking, que no expone ni
--     nombre ni correo. RLS queda activado sin políticas: la clave anon no
--     puede tocar las filas.
--   * El servidor decide qué minutos son plausibles. La app es de código
--     abierto y cualquiera puede compilarse una que diga «9000»; aquí eso no
--     pasa: por día nunca se aceptan más minutos que los transcurridos de ese
--     día, hay tope diario, y para los días desde el registro el aumento
--     entre envíos no puede superar el tiempo real transcurrido.
--   * Los minutos de días ANTERIORES al registro se aceptan (la app cuenta
--     desde el inicio del concurso aunque el usuario actualice tarde) pero se
--     apuntan aparte en `retroactivos`, para poder verificar al ganador.
-- ---------------------------------------------------------------------------

create extension if not exists pgcrypto;

-- ─── Configuración (una sola fila) ─────────────────────────────────────────
create table if not exists public.concurso_config (
  id            integer primary key default 1 check (id = 1),
  inicio        date        not null,
  fin           date        not null,
  tope_min_dia  integer     not null,
  zona          text        not null default 'America/Bogota',
  tolerancia_min integer    not null default 5,
  premio        text        not null default 'AirPods Pro'
);
insert into public.concurso_config (id, inicio, fin, tope_min_dia)
values (1, date '2026-09-13', date '2026-09-19', 360)
on conflict (id) do update
  set inicio = excluded.inicio, fin = excluded.fin, tope_min_dia = excluded.tope_min_dia;

-- ─── Participantes ─────────────────────────────────────────────────────────
create table if not exists public.concurso_participantes (
  id              uuid primary key,
  secreto_hash    text        not null,
  apodo           text        not null,
  nombre          text        not null,
  correo          text        not null,
  minutos         integer     not null default 0,
  minutos_por_dia jsonb       not null default '{}'::jsonb,
  retroactivos    integer     not null default 0,
  registrado_en   timestamptz not null default now(),
  actualizado_en  timestamptz not null default now(),
  alcanzado_en    timestamptz not null default now(),
  version_app     text
);
create unique index if not exists concurso_apodo_unico
  on public.concurso_participantes (lower(apodo));

alter table public.concurso_config        enable row level security;
alter table public.concurso_participantes enable row level security;
revoke all on public.concurso_config        from anon, authenticated;
revoke all on public.concurso_participantes from anon, authenticated;

-- ─── Ranking público: solo apodo, minutos y puesto ────────────────────────
create or replace view public.concurso_ranking
with (security_invoker = false) as
  select
    row_number() over (order by minutos desc, alcanzado_en asc) as puesto,
    apodo,
    minutos
  from public.concurso_participantes
  where minutos > 0
  order by puesto;
grant select on public.concurso_ranking to anon, authenticated;

-- ─── Estado del concurso (lo lee la app al abrir) ─────────────────────────
create or replace function public.concurso_estado()
returns jsonb
language sql
security definer
set search_path = public
stable
as $$
  select jsonb_build_object(
    'inicio',        c.inicio,
    'fin',           c.fin,
    'tope_min_dia',  c.tope_min_dia,
    'zona',          c.zona,
    'premio',        c.premio,
    'participantes', (select count(*) from public.concurso_participantes),
    'ahora',         now()
  )
  from public.concurso_config c where c.id = 1;
$$;

-- ─── Utilidad: fecha de hoy en la zona del concurso ───────────────────────
create or replace function public.concurso_hoy()
returns date
language sql
security definer
set search_path = public
stable
as $$
  select (timezone((select zona from public.concurso_config where id = 1), now()))::date;
$$;

-- ─── Registro ──────────────────────────────────────────────────────────────
-- Devuelve {ok:true} o {ok:false, error:'apodo_en_uso'|'datos'|'cerrado'}.
create or replace function public.concurso_registrar(
  p_id uuid, p_secreto text, p_apodo text, p_nombre text, p_correo text, p_version text default null
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  cfg       public.concurso_config;
  v_apodo   text := btrim(p_apodo);
  v_nombre  text := btrim(p_nombre);
  v_correo  text := lower(btrim(p_correo));
  v_hash    text := encode(digest(coalesce(p_secreto, ''), 'sha256'), 'hex');
  existente public.concurso_participantes;
begin
  select * into cfg from public.concurso_config where id = 1;
  if public.concurso_hoy() > cfg.fin then
    return jsonb_build_object('ok', false, 'error', 'cerrado');
  end if;
  if char_length(v_apodo) < 2 or char_length(v_apodo) > 24
     or char_length(v_nombre) < 2 or char_length(v_nombre) > 80
     or v_correo !~ '^[^@\s]+@[^@\s]+\.[^@\s]+$'
     or char_length(coalesce(p_secreto, '')) < 32 then
    return jsonb_build_object('ok', false, 'error', 'datos');
  end if;

  select * into existente from public.concurso_participantes p where p.id = p_id;
  if found then
    -- Mismo telefono corrigiendo sus datos: solo con su secreto.
    if existente.secreto_hash <> v_hash then
      return jsonb_build_object('ok', false, 'error', 'datos');
    end if;
    if exists (select 1 from public.concurso_participantes p
               where lower(p.apodo) = lower(v_apodo) and p.id <> p_id) then
      return jsonb_build_object('ok', false, 'error', 'apodo_en_uso');
    end if;
    update public.concurso_participantes p
      set apodo = v_apodo, nombre = v_nombre, correo = v_correo,
          version_app = coalesce(p_version, p.version_app), actualizado_en = now()
      where p.id = p_id;
    return jsonb_build_object('ok', true);
  end if;

  if exists (select 1 from public.concurso_participantes p where lower(p.apodo) = lower(v_apodo)) then
    return jsonb_build_object('ok', false, 'error', 'apodo_en_uso');
  end if;
  insert into public.concurso_participantes (id, secreto_hash, apodo, nombre, correo, version_app)
    values (p_id, v_hash, v_apodo, v_nombre, v_correo, p_version);
  return jsonb_build_object('ok', true);
exception when unique_violation then
  return jsonb_build_object('ok', false, 'error', 'apodo_en_uso');
end;
$$;

-- ─── Envío de minutos ──────────────────────────────────────────────────────
-- p_por_dia: {"2026-09-13": 250, "2026-09-14": 91}  (minutos enteros por dia,
-- tal como los cuenta el telefono, SIN tope: el tope lo aplica el servidor).
-- Devuelve {ok:true, minutos, puesto, participantes} o {ok:false, error}.
create or replace function public.concurso_reportar(p_id uuid, p_secreto text, p_por_dia jsonb)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  cfg        public.concurso_config;
  fila       public.concurso_participantes;
  hoy        date;
  ahora_zona timestamp;
  dia        date;
  clave      text;
  valor      integer;
  previo     integer;
  permitido  integer;
  nuevos     jsonb;
  total      integer := 0;
  retro      integer := 0;
  aumento_reciente integer := 0;
  transcurrido_min integer;
  dia_registro date;
  puesto     integer;
  cuantos    integer;
begin
  select * into cfg from public.concurso_config where id = 1;
  select * into fila from public.concurso_participantes where id = p_id;
  if not found or fila.secreto_hash <> encode(digest(p_secreto, 'sha256'), 'hex') then
    return jsonb_build_object('ok', false, 'error', 'no_registrado');
  end if;

  hoy        := public.concurso_hoy();
  ahora_zona := timezone(cfg.zona, now());
  dia_registro := (timezone(cfg.zona, fila.registrado_en))::date;
  transcurrido_min := greatest(0, floor(extract(epoch from (now() - fila.actualizado_en)) / 60))::integer;
  nuevos := fila.minutos_por_dia;

  for clave, valor in
    select key, greatest(0, least(coalesce((value)::text::numeric, 0), 100000))::integer
    from jsonb_each(coalesce(p_por_dia, '{}'::jsonb))
  loop
    begin
      dia := clave::date;
    exception when others then
      continue;
    end;
    -- Solo dias dentro del concurso y que ya hayan empezado.
    if dia < cfg.inicio or dia > cfg.fin or dia > hoy then
      continue;
    end if;
    previo := coalesce((nuevos ->> clave)::integer, 0);

    -- Nunca mas minutos que los transcurridos de ese dia (mas tolerancia), ni mas que el tope.
    if dia = hoy then
      permitido := least(cfg.tope_min_dia,
                         floor(extract(epoch from (ahora_zona - hoy::timestamp)) / 60)::integer + cfg.tolerancia_min);
    else
      permitido := cfg.tope_min_dia;
    end if;
    valor := least(valor, permitido);

    -- Monotono: si el telefono manda menos (historial borrado), se conserva lo ya contado.
    if valor <= previo then
      continue;
    end if;

    -- Desde el dia de registro, el aumento entre envios no puede superar el
    -- tiempo real transcurrido. Antes del registro se acepta y se anota aparte.
    if dia >= dia_registro then
      aumento_reciente := aumento_reciente + (valor - previo);
    end if;

    nuevos := jsonb_set(nuevos, array[clave], to_jsonb(valor), true);
  end loop;

  if aumento_reciente > transcurrido_min + cfg.tolerancia_min then
    -- Envio implausible: se ignora entero, y el reloj no avanza, para que
    -- no se pueda «trocear» la trampa en envios pequenos.
    return jsonb_build_object('ok', false, 'error', 'implausible',
                              'minutos', fila.minutos);
  end if;

  select coalesce(sum(least((value)::text::integer, cfg.tope_min_dia)), 0) into total
    from jsonb_each(nuevos);
  select coalesce(sum(least((value)::text::integer, cfg.tope_min_dia)), 0) into retro
    from jsonb_each(nuevos) where key::date < dia_registro;

  update public.concurso_participantes p
    set minutos_por_dia = nuevos,
        minutos         = total,
        retroactivos    = retro,
        actualizado_en  = now(),
        alcanzado_en    = case when total > fila.minutos then now() else p.alcanzado_en end
    where p.id = p_id;

  select r.puesto into puesto from public.concurso_ranking r where r.apodo = fila.apodo;
  select count(*) into cuantos from public.concurso_participantes;
  return jsonb_build_object('ok', true, 'minutos', total, 'puesto', puesto, 'participantes', cuantos);
end;
$$;

-- ─── Abandonar: borra la fila entera, nombre y correo incluidos ────────────
create or replace function public.concurso_abandonar(p_id uuid, p_secreto text)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
begin
  delete from public.concurso_participantes
    where id = p_id and secreto_hash = encode(digest(p_secreto, 'sha256'), 'hex');
  return jsonb_build_object('ok', found);
end;
$$;

-- ─── Permisos: la clave anon solo puede llamar a esto ─────────────────────
revoke all on function public.concurso_hoy() from public;
grant execute on function public.concurso_estado()                                   to anon, authenticated;
grant execute on function public.concurso_registrar(uuid, text, text, text, text, text) to anon, authenticated;
grant execute on function public.concurso_reportar(uuid, text, jsonb)                to anon, authenticated;
grant execute on function public.concurso_abandonar(uuid, text)                      to anon, authenticated;

-- ─── Para ti, desde el editor SQL, cuando quieras ver como va ─────────────
-- select apodo, nombre, correo, minutos, retroactivos, minutos_por_dia, registrado_en
--   from public.concurso_participantes order by minutos desc, alcanzado_en asc;
