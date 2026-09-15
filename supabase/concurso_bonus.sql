-- Life Music · Reto de la semana · BONUS de minutos editable a mano
-- ---------------------------------------------------------------------------
-- Pegar entero en Supabase > SQL Editor > Run (idempotente).
--
-- Antes, editar `minutos` en la tabla duraba hasta el siguiente envio de la
-- app: el servidor recalcula `minutos` desde `minutos_por_dia`. Ahora hay una
-- columna `bonus` que CG edita desde el Table Editor y que el servidor SUMA
-- siempre al recalcular. Un disparador recalcula `minutos` en cuanto cambian
-- `bonus` o `minutos_por_dia`, asi que el ranking se mueve al instante.
--
-- Uso: Table Editor > concurso_participantes > columna `bonus` > escribir los
-- minutos extra (positivos o negativos) > guardar. Nada mas.
-- ---------------------------------------------------------------------------

alter table public.concurso_participantes
  add column if not exists bonus integer not null default 0;

-- minutos = suma de cada dia con tope + bonus. Vale para el envio de la app y
-- para la edicion a mano; y si el total sube, marca el momento (desempate).
create or replace function public.concurso_recalcular()
returns trigger
language plpgsql
security definer
set search_path = public, extensions
as $$
declare
  tope  integer;
  total integer;
begin
  select tope_min_dia into tope from public.concurso_config where id = 1;
  select coalesce(sum(least((value)::text::integer, tope)), 0) into total
    from jsonb_each(coalesce(new.minutos_por_dia, '{}'::jsonb));
  new.minutos := total + coalesce(new.bonus, 0);
  if tg_op = 'UPDATE' and new.minutos > old.minutos then
    new.alcanzado_en := now();
  end if;
  return new;
end;
$$;

drop trigger if exists concurso_recalcular_trg on public.concurso_participantes;
create trigger concurso_recalcular_trg
  before insert or update of bonus, minutos_por_dia
  on public.concurso_participantes
  for each row execute function public.concurso_recalcular();

-- La funcion de envio ya no escribe `minutos` a mano: lo deja al disparador y
-- devuelve a la app el total con bonus incluido.
-- ─── Envío de minutos ──────────────────────────────────────────────────────
-- p_por_dia: {"2026-09-13": 250, "2026-09-14": 91}  (minutos enteros por dia,
-- tal como los cuenta el telefono, SIN tope: el tope lo aplica el servidor).
-- Devuelve {ok:true, minutos, puesto, participantes} o {ok:false, error}.
create or replace function public.concurso_reportar(p_id uuid, p_secreto text, p_por_dia jsonb)
returns jsonb
language plpgsql
security definer
set search_path = public, extensions
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
  minutos_antes_registro integer;
  primer_envio boolean;
  puesto     integer;
  cuantos    integer;
begin
  select * into cfg from public.concurso_config where id = 1;
  select * into fila from public.concurso_participantes where id = p_id;
  if not found or fila.secreto_hash <> encode(extensions.digest(p_secreto, 'sha256'), 'hex') then
    return jsonb_build_object('ok', false, 'error', 'no_registrado');
  end if;

  hoy        := public.concurso_hoy();
  ahora_zona := timezone(cfg.zona, now());
  dia_registro := (timezone(cfg.zona, fila.registrado_en))::date;
  transcurrido_min := greatest(0, floor(extract(epoch from (now() - fila.actualizado_en)) / 60))::integer;
  -- Minutos del dia de inscripcion que ya habian pasado al inscribirse: el
  -- primer envio puede traerlos (quien se inscribe de noche con la tarde
  -- escuchada). Siguen limitados por la hora real y por el tope.
  minutos_antes_registro := greatest(0, floor(extract(epoch from (timezone(cfg.zona, fila.registrado_en) - dia_registro::timestamp)) / 60))::integer;
  primer_envio := (fila.minutos_por_dia = '{}'::jsonb);
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

  if aumento_reciente > transcurrido_min + cfg.tolerancia_min
                        + (case when primer_envio then minutos_antes_registro else 0 end) then
    -- Envio implausible: se ignora entero, y el reloj no avanza, para que
    -- no se pueda «trocear» la trampa en envios pequenos.
    return jsonb_build_object('ok', false, 'error', 'implausible',
                              'minutos', fila.minutos);
  end if;

  select coalesce(sum(least((value)::text::integer, cfg.tope_min_dia)), 0) into total
    from jsonb_each(nuevos);
  -- Retroactivos: dias anteriores a la inscripcion, mas lo del dia de
  -- inscripcion que ya habia pasado al inscribirse. Para verificar al ganador.
  select coalesce(sum(least((value)::text::integer, cfg.tope_min_dia)), 0) into retro
    from jsonb_each(nuevos) where key::date < dia_registro;
  retro := retro + least(coalesce((nuevos ->> dia_registro::text)::integer, 0), minutos_antes_registro);

  -- `minutos` y `alcanzado_en` los fija el disparador concurso_recalcular
  -- (suma con tope + bonus), asi que aqui no se tocan.
  update public.concurso_participantes p
    set minutos_por_dia = nuevos,
        retroactivos    = retro,
        actualizado_en  = now()
    where p.id = p_id;

  select p.minutos into total from public.concurso_participantes p where p.id = p_id;
  select r.puesto into puesto from public.concurso_ranking r where r.apodo = fila.apodo;
  select count(*) into cuantos from public.concurso_participantes;
  return jsonb_build_object('ok', true, 'minutos', total, 'puesto', puesto, 'participantes', cuantos);
end;
$$;

-- Recalcular las filas que ya existen, por si alguien tenia un `minutos` tocado a mano.
update public.concurso_participantes set bonus = bonus;

-- Para ver como queda:
-- select apodo, minutos, bonus, minutos_por_dia from public.concurso_participantes order by minutos desc;
