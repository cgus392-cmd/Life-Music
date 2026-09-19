-- Anuncio del ganador (1.1.10). Parche sobre concurso.sql; ejecutar entero en el
-- SQL Editor de Supabase, una vez. No borra nada.
--
-- Dos columnas nuevas en la configuracion, que CG rellena A MANO en el Table
-- Editor (fila id = 1) cuando el ganador este verificado:
--   ganador_apodo  el apodo tal como aparece en el ranking (mayusculas no importan)
--   anuncio        un texto libre para todos («Gracias a los 20 que jugaron…»)
-- La app las lee por concurso_estado(): muestra la tarjeta del ganador en la
-- pantalla del reto y avisa una vez, con notificacion, a quien participo.
-- Para retirar el anuncio basta con vaciar las dos columnas.

alter table public.concurso_config
  add column if not exists ganador_apodo text,
  add column if not exists anuncio       text;

create or replace function public.concurso_estado()
returns jsonb
language sql
security definer
set search_path = public, extensions
stable
as $$
  select jsonb_build_object(
    'inicio',        c.inicio,
    'fin',           c.fin,
    'tope_min_dia',  c.tope_min_dia,
    'zona',          c.zona,
    'premio',        c.premio,
    'participantes', (select count(*) from public.concurso_participantes),
    'ganador_apodo', c.ganador_apodo,
    'anuncio',       c.anuncio,
    'ahora',         now()
  )
  from public.concurso_config c where c.id = 1;
$$;

grant execute on function public.concurso_estado() to anon, authenticated;
