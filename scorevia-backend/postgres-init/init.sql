-- =========================================================
-- CREACIÓN DE ESQUEMAS PARA MICROSERVICIOS
-- =========================================================
CREATE SCHEMA IF NOT EXISTS sch_usuarios;
CREATE SCHEMA IF NOT EXISTS sch_incidencias;
CREATE SCHEMA IF NOT EXISTS sch_estadisticas;
CREATE SCHEMA IF NOT EXISTS sch_predicciones;

-- =========================================================
-- SCH_USUARIOS  (Microservicio-usuarios-roles)
-- =========================================================

CREATE TABLE IF NOT EXISTS sch_usuarios.roles (
                                                  id_rol SERIAL PRIMARY KEY,
                                                  nombre_rol VARCHAR(100) NOT NULL UNIQUE,
                                                  activo BOOLEAN DEFAULT TRUE
);

CREATE TABLE IF NOT EXISTS sch_usuarios.usuarios (
                                                     id_usuario SERIAL PRIMARY KEY,
                                                     nombre_usuario VARCHAR(100) NOT NULL,
                                                     apellido_usuario VARCHAR(100) NOT NULL,
                                                     nickname VARCHAR(50) UNIQUE,
                                                     correo_usuario VARCHAR(150) NOT NULL UNIQUE,
                                                     contrasenha_usuario VARCHAR(255) NOT NULL,
                                                     url_foto TEXT,
                                                     activo BOOLEAN DEFAULT TRUE,
                                                     email_verificado BOOLEAN DEFAULT FALSE,
                                                     codigo_verificacion VARCHAR(6),
                                                     codigo_verificacion_expira TIMESTAMP,
                                                     intentos_verificacion INT DEFAULT 0,
                                                     bloqueado_verificacion_hasta TIMESTAMP,
                                                     intentos_login INT DEFAULT 0,
                                                     bloqueado_login_hasta TIMESTAMP,
                                                     codigo_reset_password VARCHAR(6),
                                                     codigo_reset_password_expira TIMESTAMP,
                                                     intentos_reset_password INT DEFAULT 0,
                                                     bloqueado_reset_password_hasta TIMESTAMP,
                                                     es_premium BOOLEAN DEFAULT FALSE
);

CREATE TABLE IF NOT EXISTS sch_usuarios.usuarios_roles (
                                                           id_usuario INT NOT NULL REFERENCES sch_usuarios.usuarios(id_usuario) ON DELETE CASCADE,
                                                           id_rol INT NOT NULL REFERENCES sch_usuarios.roles(id_rol) ON DELETE CASCADE,
                                                           PRIMARY KEY (id_usuario, id_rol)
);

CREATE TABLE IF NOT EXISTS sch_usuarios.usuarios_equipos_favoritos (
                                                                       id_usuario INT REFERENCES sch_usuarios.usuarios(id_usuario) ON DELETE CASCADE,
                                                                       id_equipo_api INT NOT NULL,
                                                                       fecha_agregado TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                                                                       PRIMARY KEY (id_usuario, id_equipo_api)
);

CREATE TABLE IF NOT EXISTS sch_usuarios.usuarios_partidos_favoritos (
                                                                        id_usuario INT REFERENCES sch_usuarios.usuarios(id_usuario) ON DELETE CASCADE,
                                                                        id_partido_api INT NOT NULL,
                                                                        fecha_agregado TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                                                                        PRIMARY KEY (id_usuario, id_partido_api)
);

CREATE TABLE IF NOT EXISTS sch_usuarios.usuarios_ligas_favoritas (
                                                                     id_usuario INT REFERENCES sch_usuarios.usuarios(id_usuario) ON DELETE CASCADE,
                                                                     id_liga_api INT NOT NULL,
                                                                     fecha_agregado TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                                                                     PRIMARY KEY (id_usuario, id_liga_api)
);

CREATE TABLE IF NOT EXISTS sch_usuarios.refresh_tokens (
                                                           id_refresh_token BIGSERIAL PRIMARY KEY,
                                                           id_usuario INT NOT NULL REFERENCES sch_usuarios.usuarios(id_usuario) ON DELETE CASCADE,
                                                           jti VARCHAR(36) NOT NULL,
                                                           fecha_expiracion TIMESTAMP NOT NULL,
                                                           revocado BOOLEAN NOT NULL DEFAULT FALSE,
                                                           fecha_creacion TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                                           CONSTRAINT jti_UNIQUE UNIQUE (jti)
);
CREATE INDEX IF NOT EXISTS idx_refresh_tokens_usuario ON sch_usuarios.refresh_tokens(id_usuario);

INSERT INTO sch_usuarios.roles (nombre_rol, activo)
VALUES ('FREE', TRUE), ('PREMIUM', TRUE), ('ADMIN', TRUE)
ON CONFLICT (nombre_rol) DO NOTHING;

-- =========================================================
-- SCH_INCIDENCIAS  (Microservicio-incidencias)
-- =========================================================

CREATE TABLE IF NOT EXISTS sch_incidencias.incidencias (
                                                           id_incidencia SERIAL PRIMARY KEY,
                                                           endpoint VARCHAR(255),
                                                           tipo VARCHAR(100),
                                                           clase VARCHAR(255),
                                                           metodo VARCHAR(255),
                                                           traza TEXT,
                                                           fecha TIMESTAMP,
                                                           usuario_contexto VARCHAR(150)
);

CREATE TABLE IF NOT EXISTS sch_incidencias.reportes (
                                                        id_reporte SERIAL PRIMARY KEY,
                                                        -- FK real hacia sch_usuarios: antes estos
                                                        -- eran INT sueltos sin REFERENCES, así que borrar un usuario dejaba reportes
                                                        -- huérfanos apuntando a un id_usuario inexistente para siempre. ON DELETE CASCADE
                                                        -- en vez de SET NULL porque las columnas son NOT NULL (un reporte sin denunciante
                                                        -- o sin denunciado no tiene sentido de negocio): si cualquiera de las dos partes
                                                        -- deja de existir, el reporte deja de tener sentido como registro activo de moderación.
                                                        id_denunciante INT NOT NULL REFERENCES sch_usuarios.usuarios(id_usuario) ON DELETE CASCADE,
                                                        id_denunciado INT NOT NULL REFERENCES sch_usuarios.usuarios(id_usuario) ON DELETE CASCADE,
                                                        motivo VARCHAR(255) NOT NULL,
                                                        estado VARCHAR(50) DEFAULT 'PENDIENTE',
                                                        fecha_reporte TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                                                        resolucion TEXT,
                                                        id_admin_resolutor INT,
                                                        fecha_resolucion TIMESTAMP
);

-- =========================================================
-- SCH_ESTADISTICAS  (Microservicio-estadisticas)
-- =========================================================

CREATE TABLE IF NOT EXISTS sch_estadisticas.estadios (
                                                         id_estadio INT PRIMARY KEY,
                                                         nombre VARCHAR(150) NOT NULL,
                                                         ciudad VARCHAR(100),
                                                         capacidad INT,
                                                         superficie VARCHAR(50),
                                                         foto_url TEXT
);

CREATE TABLE IF NOT EXISTS sch_estadisticas.ligas (
                                                      id_liga INT PRIMARY KEY,
                                                      nombre VARCHAR(100) NOT NULL,
                                                      pais VARCHAR(50),
                                                      temporada_actual INT,
                                                      activa BOOLEAN DEFAULT TRUE,
                                                      logo TEXT,
                                                      bandera TEXT,
                                                      -- Qué publica API-Football de esta competición (llega gratis en /leagues,
                                                      -- dentro de seasons[].coverage). NULL = todavía no se sabe. Sirve para no
                                                      -- gastar peticiones pidiendo estadísticas de competiciones que no las dan:
                                                      -- de los partidos de 2026 ya acabados, 9 de cada 10 no tienen stats porque
                                                      -- su competición no las cubre (amistosos, filiales, ligas regionales).
                                                      cobertura_stats_partido BOOLEAN,
                                                      cobertura_stats_jugadores BOOLEAN,
                                                      cobertura_eventos BOOLEAN,
                                                      cobertura_alineaciones BOOLEAN,
                                                      cobertura_clasificacion BOOLEAN,
                                                      cobertura_actualizada TIMESTAMP
);

CREATE TABLE IF NOT EXISTS sch_estadisticas.equipos (
                                                        id_equipo INT PRIMARY KEY,
                                                        nombre VARCHAR(100) NOT NULL,
                                                        pais VARCHAR(50),
                                                        escudo_url VARCHAR(255),
                                                        id_liga INT REFERENCES sch_estadisticas.ligas(id_liga),
                                                        codigo VARCHAR(10),
                                                        fundacion INT,
                                                        id_estadio INT REFERENCES sch_estadisticas.estadios(id_estadio) ON DELETE SET NULL,
                                                        ultima_sync_traspasos TIMESTAMP
);

CREATE TABLE IF NOT EXISTS sch_estadisticas.jugadores (
                                                          id_jugador INT PRIMARY KEY,
                                                          nombre VARCHAR(150) NOT NULL,
                                                          nombre_completo VARCHAR(255),
                                                          nacionalidad VARCHAR(50),
                                                          fecha_nacimiento DATE,
                                                          lugar_nacimiento VARCHAR(150),
                                                          posicion VARCHAR(30),
                                                          dorsal INT,
                                                          foto_url VARCHAR(255),
                                                          id_equipo INT REFERENCES sch_estadisticas.equipos(id_equipo),
                                                          altura VARCHAR(50),
                                                          peso VARCHAR(50),
                                                          ultima_sync_bajas TIMESTAMP,
                                                          perfil_verificado BOOLEAN DEFAULT FALSE,
                                                          latitud_nacimiento DOUBLE PRECISION,
                                                          longitud_nacimiento DOUBLE PRECISION,
                                                          pie_bueno VARCHAR(30),
                                                          fecha_equipo_actualizado TIMESTAMP
);

CREATE TABLE IF NOT EXISTS sch_estadisticas.historial_dorsales (
                                                                   id_historial BIGSERIAL PRIMARY KEY,
                                                                   id_jugador INT NOT NULL,
                                                                   id_equipo INT NOT NULL,
                                                                   temporada INT NOT NULL,
                                                                   dorsal INT,
                                                                   posicion VARCHAR(50),
                                                                   id_partido INT NULL,
                                                                   fecha_actualizacion TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                                                                   UNIQUE(id_jugador, id_equipo, temporada)
);

CREATE TABLE IF NOT EXISTS sch_estadisticas.entrenadores (
                                                             id_entrenador INT PRIMARY KEY,
                                                             nombre VARCHAR(255) NOT NULL,
                                                             edad INT,
                                                             nacionalidad VARCHAR(255),
                                                             foto TEXT,
                                                             id_equipo INT REFERENCES sch_estadisticas.equipos(id_equipo) ON DELETE SET NULL,
    -- NULL = todavía no se le ha pedido el palmarés a la API. Distinguirlo de "no ha ganado
    -- nada" es lo que hace que el relleno termine: ver la migración 2026-09-08.
                                                             ultima_sync_trofeos TIMESTAMP
);

CREATE TABLE IF NOT EXISTS sch_estadisticas.historial_entrenadores (
                                                                       id_historial BIGSERIAL PRIMARY KEY,
                                                                       id_entrenador INT NOT NULL REFERENCES sch_estadisticas.entrenadores(id_entrenador) ON DELETE CASCADE,
                                                                       id_equipo INT NOT NULL REFERENCES sch_estadisticas.equipos(id_equipo) ON DELETE CASCADE,
                                                                       temporada INT NOT NULL,
                                                                       fecha_actualizacion TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                                                                       UNIQUE(id_entrenador, id_equipo, temporada)
);

CREATE TABLE IF NOT EXISTS sch_estadisticas.arbitros (
                                                         id_arbitro SERIAL PRIMARY KEY,
                                                         nombre VARCHAR(150) NOT NULL,
                                                         -- NOT NULL DEFAULT '': API-Football no siempre manda país, y un UNIQUE(nombre, pais)
                                                         -- con NULL permitiría filas "duplicadas" (Postgres trata cada NULL como distinto).
                                                         pais VARCHAR(50) NOT NULL DEFAULT '',
                                                         UNIQUE(nombre, pais)
);

CREATE TABLE IF NOT EXISTS sch_estadisticas.partidos (
                                                         id_partido INT PRIMARY KEY,
                                                         id_liga INT REFERENCES sch_estadisticas.ligas(id_liga),
                                                         id_equipo_local INT REFERENCES sch_estadisticas.equipos(id_equipo),
                                                         id_equipo_visitante INT REFERENCES sch_estadisticas.equipos(id_equipo),
                                                         id_arbitro INT REFERENCES sch_estadisticas.arbitros(id_arbitro),
                                                         fecha TIMESTAMP,
                                                         jornada INT,
                                                         -- La fase en crudo ("Quarter-finals", "Group Stage - A").
                                                         -- jornada solo guarda el número, y en copa no hay.
                                                         ronda VARCHAR(100),
                                                         resultado_local INT,
                                                         resultado_visitante INT,
                                                         estado VARCHAR(50),
                                                         -- Marcador desglosado: sin esto, una eliminatoria resuelta en los
                                                         -- penaltis queda guardada como el empate que no fue.
                                                         resultado_local_descanso INT,
                                                         resultado_visitante_descanso INT,
                                                         penaltis_local INT,
                                                         penaltis_visitante INT,
                                                         datos_api JSONB,
                                                         stats_not_found BOOLEAN DEFAULT FALSE,
                                                         intentos_ingesta INT DEFAULT 0,
                                                         eventos_not_found BOOLEAN DEFAULT FALSE,
                                                         intentos_eventos INT DEFAULT 0,
                                                         temporada INT,
                                                         version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS sch_estadisticas.eventos_partido (
                                                                id_evento BIGSERIAL PRIMARY KEY,
                                                                id_partido INT REFERENCES sch_estadisticas.partidos(id_partido) ON DELETE CASCADE,
                                                                id_equipo INT REFERENCES sch_estadisticas.equipos(id_equipo) ON DELETE CASCADE,
                                                                tiempo_minuto INT NOT NULL,
                                                                tiempo_extra INT,
                                                                tipo_evento VARCHAR(50) NOT NULL,
                                                                detalle VARCHAR(100),
                                                                id_jugador_principal INT REFERENCES sch_estadisticas.jugadores(id_jugador) ON DELETE SET NULL,
                                                                id_jugador_secundario INT REFERENCES sch_estadisticas.jugadores(id_jugador) ON DELETE SET NULL
);

CREATE TABLE IF NOT EXISTS sch_estadisticas.clasificaciones (
                                                                id_clasificacion BIGSERIAL PRIMARY KEY,
                                                                id_liga INT REFERENCES sch_estadisticas.ligas(id_liga) ON DELETE CASCADE,
                                                                id_equipo INT REFERENCES sch_estadisticas.equipos(id_equipo) ON DELETE CASCADE,
                                                                temporada INT NOT NULL,
                                                                posicion INT NOT NULL,
                                                                puntos INT NOT NULL,
                                                                partidos_jugados INT,
                                                                partidos_ganados INT,
                                                                partidos_empatados INT,
                                                                partidos_perdidos INT,
                                                                goles_favor INT,
                                                                goles_contra INT,
                                                                diferencia_goles INT,
                                                                estado_competicion VARCHAR(500),
                                                                UNIQUE (id_liga, id_equipo, temporada)
);

CREATE TABLE IF NOT EXISTS sch_estadisticas.estadisticas_equipo_partido (
                                                                            id_est_equipo SERIAL PRIMARY KEY,
                                                                            id_partido INT NOT NULL REFERENCES sch_estadisticas.partidos(id_partido) ON DELETE CASCADE,
                                                                            id_equipo INT NOT NULL REFERENCES sch_estadisticas.equipos(id_equipo),
                                                                            posesion NUMERIC(5,2),
                                                                            tiros INT,
                                                                            tiros_puerta INT,
                                                                            corners INT,
                                                                            faltas INT,
                                                                            tarjetas_amarillas INT,
                                                                            tarjetas_rojas INT,
                                                                            goles_esperados NUMERIC(5,2),
                                                                            sistema_tactico VARCHAR(20),
                                                                            -- Entrenador de ESE partido (bloque lineups del fixture). Sin FK
                                                                            -- a propósito: puede no estar todavía en `entrenadores`.
                                                                            id_entrenador INT,
                                                                            estadisticas_extra JSONB,
                                                                            UNIQUE (id_partido, id_equipo)
);

CREATE TABLE IF NOT EXISTS sch_estadisticas.estadisticas_jugador_partido (
                                                                             id SERIAL PRIMARY KEY,
                                                                             id_partido INT NOT NULL REFERENCES sch_estadisticas.partidos(id_partido) ON DELETE CASCADE,
                                                                             id_jugador INT NOT NULL REFERENCES sch_estadisticas.jugadores(id_jugador),
                                                                             id_equipo INT NOT NULL REFERENCES sch_estadisticas.equipos(id_equipo),
                                                                             minutos_jugados INT,
                                                                             goles INT,
                                                                             asistencias INT,
                                                                             tarjetas_amarillas INT,
                                                                             tarjetas_rojas INT,
                                                                             pases_completados INT,
                                                                             pases_totales INT,
                                                                             valoracion NUMERIC(3,1),
                                                                             titular BOOLEAN DEFAULT FALSE,
                                                                             posicion_campo VARCHAR(50),
                                                                             -- Sitio real en el campo ("fila:columna") de la alineación oficial.
                                                                             posicion_grid VARCHAR(10),
                                                                             UNIQUE (id_partido, id_jugador)
);

CREATE TABLE IF NOT EXISTS sch_estadisticas.historial_bajas (
                                                                id_baja BIGSERIAL PRIMARY KEY,
                                                                id_jugador INT REFERENCES sch_estadisticas.jugadores(id_jugador) ON DELETE CASCADE,
                                                                tipo VARCHAR(50) NOT NULL,
                                                                causa VARCHAR(255),
                                                                fecha_inicio DATE NOT NULL,
                                                                fecha_fin DATE,
                                                                activo BOOLEAN DEFAULT FALSE,
                                                                UNIQUE(id_jugador, tipo, fecha_inicio)
);

CREATE TABLE IF NOT EXISTS sch_estadisticas.traspasos (
                                                          id_traspaso BIGSERIAL PRIMARY KEY,
                                                          id_jugador INT REFERENCES sch_estadisticas.jugadores(id_jugador) ON DELETE CASCADE,
                                                          fecha DATE NOT NULL,
                                                          id_equipo_origen INT REFERENCES sch_estadisticas.equipos(id_equipo) ON DELETE SET NULL,
                                                          id_equipo_destino INT REFERENCES sch_estadisticas.equipos(id_equipo) ON DELETE SET NULL,
                                                          tipo VARCHAR(100),
                                                          coste VARCHAR(100)
);

CREATE TABLE IF NOT EXISTS sch_estadisticas.trofeos (
                                                        id_trofeo BIGSERIAL PRIMARY KEY,
                                                        id_jugador INT REFERENCES sch_estadisticas.jugadores(id_jugador) ON DELETE CASCADE,
                                                        id_entrenador INT REFERENCES sch_estadisticas.entrenadores(id_entrenador) ON DELETE CASCADE,
                                                        competicion VARCHAR(150) NOT NULL,
                                                        pais VARCHAR(50),
                                                        temporada VARCHAR(20),
                                                        resultado VARCHAR(50) NOT NULL,
                                                        CONSTRAINT chk_trofeo_titular CHECK (
                                                            (id_jugador IS NOT NULL AND id_entrenador IS NULL) OR
                                                            (id_jugador IS NULL AND id_entrenador IS NOT NULL)
                                                            )
);

CREATE INDEX IF NOT EXISTS idx_equipos_liga ON sch_estadisticas.equipos(id_liga);
CREATE INDEX IF NOT EXISTS idx_jugadores_equipo ON sch_estadisticas.jugadores(id_equipo);
CREATE INDEX IF NOT EXISTS idx_partidos_liga ON sch_estadisticas.partidos(id_liga);
CREATE INDEX IF NOT EXISTS idx_partidos_equipo_local ON sch_estadisticas.partidos(id_equipo_local);
CREATE INDEX IF NOT EXISTS idx_partidos_equipo_visitante ON sch_estadisticas.partidos(id_equipo_visitante);
CREATE INDEX IF NOT EXISTS idx_partidos_arbitro ON sch_estadisticas.partidos(id_arbitro);
CREATE INDEX IF NOT EXISTS idx_partidos_fecha ON sch_estadisticas.partidos(fecha);
CREATE INDEX IF NOT EXISTS idx_partidos_estado ON sch_estadisticas.partidos(estado);
CREATE INDEX IF NOT EXISTS idx_historial_dorsales_jugador ON sch_estadisticas.historial_dorsales(id_jugador);
CREATE INDEX IF NOT EXISTS idx_historial_entrenadores_entrenador ON sch_estadisticas.historial_entrenadores(id_entrenador);
-- Índices añadidos en la auditoría de rendimiento del 2026-08-30: columnas FK muy consultadas
-- (Postgres NO indexa automáticamente el lado que referencia una FK) sin cobertura previa.
CREATE INDEX IF NOT EXISTS idx_eventos_partido_partido ON sch_estadisticas.eventos_partido(id_partido);
CREATE INDEX IF NOT EXISTS idx_est_equipo_partido_equipo ON sch_estadisticas.estadisticas_equipo_partido(id_equipo);
CREATE INDEX IF NOT EXISTS idx_est_jugador_partido_jugador ON sch_estadisticas.estadisticas_jugador_partido(id_jugador);
CREATE INDEX IF NOT EXISTS idx_traspasos_jugador ON sch_estadisticas.traspasos(id_jugador);
CREATE INDEX IF NOT EXISTS idx_traspasos_equipo_origen ON sch_estadisticas.traspasos(id_equipo_origen);
CREATE INDEX IF NOT EXISTS idx_traspasos_equipo_destino ON sch_estadisticas.traspasos(id_equipo_destino);
CREATE INDEX IF NOT EXISTS idx_historial_bajas_jugador ON sch_estadisticas.historial_bajas(id_jugador);
CREATE INDEX IF NOT EXISTS idx_trofeos_jugador ON sch_estadisticas.trofeos(id_jugador);
CREATE INDEX IF NOT EXISTS idx_trofeos_entrenador ON sch_estadisticas.trofeos(id_entrenador);
CREATE INDEX IF NOT EXISTS idx_entrenadores_equipo ON sch_estadisticas.entrenadores(id_equipo);
CREATE INDEX IF NOT EXISTS idx_entrenadores_pendientes_trofeos ON sch_estadisticas.entrenadores(id_entrenador) WHERE ultima_sync_trofeos IS NULL;
CREATE INDEX IF NOT EXISTS idx_clasificaciones_liga_temp ON sch_estadisticas.clasificaciones(id_liga, temporada);
-- Filtrar eventos_partido (8,7M filas) por tipo de evento (goles, tarjetas, VAR...).
CREATE INDEX IF NOT EXISTS idx_eventos_partido_tipo ON sch_estadisticas.eventos_partido(tipo_evento, id_evento);
-- La alineación de un (partido, equipo) concreto.
CREATE INDEX IF NOT EXISTS idx_est_jugador_partido_partido_equipo
    ON sch_estadisticas.estadisticas_jugador_partido(id_partido, id_equipo);

-- Traspasos y trofeos no tienen id propio en API-Football: se deduplican por clave natural.
-- El ingest lo hacía solo contra la base de datos y no contra el lote en vuelo, así que una
-- respuesta que repetía la misma fila la insertaba N veces (había palmarés con el mismo título
-- seis veces y ~26.000 traspasos duplicados). El código ya filtra dentro del lote; estos
-- índices son la garantía dura de que no puede volver a pasar.
CREATE UNIQUE INDEX IF NOT EXISTS uq_traspaso_natural ON sch_estadisticas.traspasos (
    COALESCE(id_jugador, -1), fecha,
    COALESCE(id_equipo_origen, -1), COALESCE(id_equipo_destino, -1),
    COALESCE(tipo, '')
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_trofeo_natural ON sch_estadisticas.trofeos (
    COALESCE(id_jugador, -1), COALESCE(id_entrenador, -1),
    competicion, COALESCE(pais, ''), COALESCE(temporada, ''), resultado
);

CREATE TABLE IF NOT EXISTS sch_estadisticas.backfill_progreso (
                                                                  id SERIAL PRIMARY KEY,
                                                                  temporada INT NOT NULL,
                                                                  id_liga INT NOT NULL,
                                                                  pagina_actual INT NOT NULL,
                                                                  completado BOOLEAN DEFAULT FALSE,
                                                                  ultima_actualizacion TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- =========================================================
-- SCH_PREDICCIONES  (Microservicio Predictivo y Cuotas)
-- =========================================================
-- Sin tablas propias a propósito: Microservicio-predicciones no tiene ninguna dependencia
-- JPA/JDBC (sin @Entity, sin repositorio, sin spring-boot-starter-data-jpa en su pom.xml); todo
-- se calcula en caliente (Dixon-Coles) y se cachea en memoria 15s (Caffeine). Hubo 4 tablas aquí
-- pensadas para una función de "cuotas propias de Scorevia" antes de partido -- descartada
-- (2026-09-01): sin diseño claro de cómo diferenciarla del 1X2 ya existente, y API-Football ya
-- trae cuotas propias (de ahí el campo fuente='API_FOOTBALL' que tenía la tabla `predicciones`
-- descartada), así que construir un motor de cuotas propio sería duplicar esa fuente sin más.
-- El esquema sigue existiendo (arriba, CREATE SCHEMA) por si se retoma la idea con un diseño
-- real más adelante, pero sin tablas colgando mientras tanto.

-- Marcas de "ya procesado" de los scripts de rellenado, para que sean reanudables: sin ellas
-- cada ejecucion vuelve a empezar por el principio y gasta cuota en lo que ya estaba hecho.
-- Tampoco tienen entidad JPA, asi que `ddl-auto: validate` no avisa si faltan.
CREATE TABLE IF NOT EXISTS sch_estadisticas.backfill_partidos_hechos (
    id_partido INTEGER PRIMARY KEY
);

CREATE TABLE IF NOT EXISTS sch_estadisticas.historial_entrenadores_procesados (
    id_entrenador INTEGER PRIMARY KEY
);

-- El volcado de GeoNames: 235.206 ciudades con coordenadas, nombres alternativos y poblacion.
-- Es la fuente de las coordenadas del lugar de nacimiento de los jugadores, y se
-- rellena con scripts/coordenadas_desde_geonames.py (no viene de API-Football, no cuesta cuota).
CREATE TABLE IF NOT EXISTS sch_estadisticas.ciudades_mundo (
    geonameid         INTEGER,
    name              VARCHAR(255),
    asciiname         VARCHAR(255),
    alternatenames    TEXT,
    latitude          DOUBLE PRECISION,
    longitude         DOUBLE PRECISION,
    feature_class     VARCHAR(2),
    feature_code      VARCHAR(10),
    country_code      VARCHAR(2),
    cc2               VARCHAR(255),
    admin1_code       VARCHAR(20),
    admin2_code       VARCHAR(80),
    admin3_code       VARCHAR(20),
    admin4_code       VARCHAR(20),
    population        BIGINT,
    elevation         INTEGER,
    dem               INTEGER,
    timezone          VARCHAR(40),
    modification_date DATE
);

CREATE INDEX IF NOT EXISTS idx_ciudades_mundo_pais_ascii
    ON sch_estadisticas.ciudades_mundo (country_code, asciiname);

-- Que club se ha sincronizado y cuando, para que el cron de plantillas sea reanudable y no
-- vuelva a pedir el mismo equipo dos veces el mismo dia. No tiene entidad JPA (se usa solo
-- con SQL nativo), asi que `ddl-auto: validate` no avisa si falta: el fallo saldria de noche,
-- en ejecucion, y solo en una base recien creada. Ver la migracion del 2026-09-10.
CREATE TABLE IF NOT EXISTS sch_estadisticas.plantillas_sincronizadas (
    id_equipo  INTEGER   NOT NULL,
    temporada  INTEGER   NOT NULL,
    fecha      TIMESTAMP NOT NULL DEFAULT now(),
    PRIMARY KEY (id_equipo, temporada)
);

CREATE INDEX IF NOT EXISTS idx_historial_dorsales_equipo ON sch_estadisticas.historial_dorsales (id_equipo);

-- =====================================================================
-- ROLES DE APLICACIÓN POR MICROSERVICIO (principio de mínimo privilegio)
-- =====================================================================
-- El rol POSTGRES_USER (superusuario) solo debe usarse para administración
-- y para estos scripts de arranque. Cada microservicio se conecta con un
-- rol propio, confinado a su schema, en vez de compartir el superusuario.
--
-- Las contraseñas de aquí abajo son SOLO placeholders para entornos nuevos.
-- Nada más levantar el contenedor por primera vez, cámbialas con
-- ALTER ROLE <rol> WITH PASSWORD '...'; y actualiza el .env con los mismos
-- valores (ESTADISTICAS_DB_PASS, USUARIOS_DB_PASS,
-- INCIDENCIAS_DB_PASS). Nunca subas contraseñas reales a este archivo.
DO $$
    BEGIN
        IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = 'scorevia_incidencias') THEN
            CREATE ROLE scorevia_incidencias WITH LOGIN PASSWORD 'PEGAR_AQUI_INCIDENCIAS_DB_PASS';
        END IF;
        IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = 'scorevia_usuarios') THEN
            CREATE ROLE scorevia_usuarios WITH LOGIN PASSWORD 'PEGAR_AQUI_USUARIOS_DB_PASS';
        END IF;
        IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = 'scorevia_estadisticas') THEN
            CREATE ROLE scorevia_estadisticas WITH LOGIN PASSWORD 'PEGAR_AQUI_ESTADISTICAS_DB_PASS';
        END IF;
    END
$$;

GRANT CONNECT ON DATABASE futbol_stats TO scorevia_incidencias, scorevia_usuarios, scorevia_estadisticas;

GRANT USAGE ON SCHEMA sch_incidencias TO scorevia_incidencias;
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA sch_incidencias TO scorevia_incidencias;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA sch_incidencias TO scorevia_incidencias;
ALTER DEFAULT PRIVILEGES IN SCHEMA sch_incidencias GRANT ALL PRIVILEGES ON TABLES TO scorevia_incidencias;
ALTER DEFAULT PRIVILEGES IN SCHEMA sch_incidencias GRANT ALL PRIVILEGES ON SEQUENCES TO scorevia_incidencias;

GRANT USAGE ON SCHEMA sch_usuarios TO scorevia_usuarios;
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA sch_usuarios TO scorevia_usuarios;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA sch_usuarios TO scorevia_usuarios;
ALTER DEFAULT PRIVILEGES IN SCHEMA sch_usuarios GRANT ALL PRIVILEGES ON TABLES TO scorevia_usuarios;
ALTER DEFAULT PRIVILEGES IN SCHEMA sch_usuarios GRANT ALL PRIVILEGES ON SEQUENCES TO scorevia_usuarios;

GRANT USAGE ON SCHEMA sch_estadisticas TO scorevia_estadisticas;
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA sch_estadisticas TO scorevia_estadisticas;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA sch_estadisticas TO scorevia_estadisticas;
ALTER DEFAULT PRIVILEGES IN SCHEMA sch_estadisticas GRANT ALL PRIVILEGES ON TABLES TO scorevia_estadisticas;
ALTER DEFAULT PRIVILEGES IN SCHEMA sch_estadisticas GRANT ALL PRIVILEGES ON SEQUENCES TO scorevia_estadisticas;

-- =====================================================================
-- BASE DE DATOS DEDICADA PARA SONARQUBE (sustituye a su H2 embebida)
-- =====================================================================
-- Sonar necesita su propia base de datos completa (gestiona sus propias
-- migraciones/tablas), no un esquema dentro de futbol_stats.
DO $$
    BEGIN
        IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = 'scorevia_sonarqube') THEN
            CREATE ROLE scorevia_sonarqube WITH LOGIN PASSWORD 'PEGAR_AQUI_SONARQUBE_DB_PASS';
        END IF;
    END
$$;

SELECT 'CREATE DATABASE sonarqube OWNER scorevia_sonarqube'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'sonarqube')\gexec
