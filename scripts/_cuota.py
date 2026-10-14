"""
Suelo de cuota diaria para los scripts de backfill.

Por qué hace falta
------------------
Los scripts miraban solo `x-ratelimit-remaining`, que es el límite **por minuto**, y ante un
número bajo esperaban 60 segundos. Del límite **por día** (`x-ratelimit-requests-remaining`)
no se enteraban: seguían pidiendo hasta que la propia API contestaba "se acabó".

Eso daba igual mientras el backend no hacía nada solo. Ya no: desde la ingesta automática
(ver `IngestaScheduler`), el backend gasta cuota todo el día en los partidos en directo y en
las estadísticas de los que van terminando. Un backfill largo dejando la cuota a cero deja al
directo sin nada con que trabajar hasta las 00:00 UTC del día siguiente.

Con esto, un backfill para solo cuando queda menos de la reserva, y lo dice claro para poder
retomarlo mañana. Los scripts de este proyecto son todos reanudables, así que parar no cuesta
nada: la próxima ejecución sigue donde lo dejó.

El suelo se ajusta con SCRIPTS_RESERVA_API en el .env. El valor por defecto (25.000) es el
mismo que usa el backend en `api.football.reserva-diaria` para sus propios trabajos de
mantenimiento, y deja de sobra para un día entero de directo y de ingesta post-partido.
"""

import os

# Cabecera con las peticiones que quedan HOY. Ojo, no confundir con 'x-ratelimit-remaining',
# que es la del minuto en curso.
CABECERA_DIA = "x-ratelimit-requests-remaining"

RESERVA_POR_DEFECTO = 25000


class CuotaDiariaAgotada(Exception):
    """Queda menos cuota diaria que la reserva: hay que parar y seguir mañana."""


def reserva_diaria():
    try:
        return int(os.environ.get("SCRIPTS_RESERVA_API", RESERVA_POR_DEFECTO))
    except ValueError:
        return RESERVA_POR_DEFECTO


def restantes_hoy(resp):
    """Peticiones que quedan hoy según la respuesta, o None si la cabecera no viene."""
    valor = resp.headers.get(CABECERA_DIA)
    if valor is None:
        return None
    try:
        return int(valor)
    except ValueError:
        return None


def guardia_cuota(resp):
    """
    Corta el script si la cuota del día ha bajado de la reserva.

    Se llama con cada respuesta: la cuenta la lleva la API, no el script, así que no hay que
    llevar contadores propios ni acertar cuántas peticiones va a costar cada fase.
    """
    quedan = restantes_hoy(resp)
    if quedan is None:
        return None

    reserva = reserva_diaria()
    if quedan <= reserva:
        raise CuotaDiariaAgotada(
            f"Quedan {quedan} peticiones hoy y la reserva son {reserva}. "
            f"Se para aquí para no dejar sin cuota al directo y a la ingesta post-partido; "
            f"vuelve a lanzarlo mañana y seguirá donde lo dejó "
            f"(ajustable con SCRIPTS_RESERVA_API en el .env)."
        )
    return quedan
