package usuariosroles.services.usuario;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;

import lombok.RequiredArgsConstructor;
import usuariosroles.dto.UsuarioDTO;
import usuariosroles.dto.UsuarioUpdateDTO;
import usuariosroles.entities.Rol;
import usuariosroles.entities.Usuario;
import usuariosroles.exception.*;
import usuariosroles.mappers.UsuarioMapper;
import usuariosroles.repositories.RolRepository;
import usuariosroles.repositories.UsuarioRepository;
import usuariosroles.services.jwt.JwtTokenService;
import usuariosroles.services.mail.MailService;
import usuariosroles.services.usuariorol.UsuarioRolServiceImpl;

@Service
@RequiredArgsConstructor
public class UsuarioServiceImpl implements UsuarioService {

	private static final boolean ACTIVO = true;
	private static final String ROL_DEFAULT = "FREE";
	private static final int CODIGO_EXPIRA_MINUTOS = 15;
	private static final int MAX_INTENTOS_VERIFICACION = 5;
	private static final int MINUTOS_BLOQUEO_VERIFICACION = 15;
	private static final int MAX_INTENTOS_RESET_PASSWORD = 5;
	private static final int MINUTOS_BLOQUEO_RESET_PASSWORD = 15;
	private static final Logger log = LoggerFactory.getLogger(UsuarioServiceImpl.class);
	private static final SecureRandom random = new SecureRandom();
	private static final String ROLE_ADMIN = "ROLE_ADMIN";
	private static final String ERR_ROL_NO_EXISTE = "Error crítico: El rol por defecto no existe en el sistema.";
	private static final String ERR_ASIGNACION_ROL_FALLIDA = "Error crítico: No se pudo asignar el rol por defecto al usuario.";

	private final UsuarioRepository usuarioRepository;
	private final UsuarioMapper usuarioMapper;
	private final RolRepository rolRepository;
	private final UsuarioRolServiceImpl service;
	private final PasswordEncoder encoder;
	private final MailService mailService;
	private final GoogleIdTokenVerifier googleIdTokenVerifier;
	private final JwtTokenService jwtTokenService;

	@Transactional
	public UsuarioDTO crearUsuario(UsuarioDTO usuarioDTO) {
		Usuario usuario = usuarioMapper.toUsuario(usuarioDTO);
		usuario.setContrasenhaUsuario(encoder.encode(usuario.getContrasenhaUsuario()));

		String codigo = generarCodigoVerificacion();
		usuario.setCodigoVerificacion(codigo);
		usuario.setCodigoVerificacionExpira(LocalDateTime.now(ZoneId.systemDefault()).plusMinutes(CODIGO_EXPIRA_MINUTOS));

		Usuario aux = usuarioRepository.save(usuario);

		Rol rol = rolRepository.findByNombreRolAndActivo(ROL_DEFAULT, ACTIVO)
				.orElseThrow(() -> new ConflictException(ERR_ROL_NO_EXISTE));

		// asignarRolUsuario devuelve null (en vez de lanzar) si el usuario o el rol recién
		// resueltos ya no están activos -- una ventana de carrera muy estrecha, pero sin esta
		// comprobación el registro "tendría éxito" dejando al usuario sin ningún rol asignado.
		if (service.asignarRolUsuario(aux.getIdUsuario(), rol.getIdRol()) == null) {
			throw new ConflictException(ERR_ASIGNACION_ROL_FALLIDA);
		}

		mailService.enviarCodigoVerificacion(aux.getCorreoUsuario(), aux.getNombreUsuario(), codigo);

		UsuarioDTO auxDto = usuarioMapper.toDtoPost(aux);
		Set<Rol> auxRols = new HashSet<>();
		auxRols.add(rol);
		auxDto.setRoles(auxRols);

		return auxDto;
	}

	@Override
	@Transactional
	public void verificarEmail(String correo, String codigo) {
		Usuario usuario = usuarioRepository.findByCorreoUsuario(correo)
				.orElseThrow(() -> new NotFoundException("No existe ningún usuario con ese correo"));

		if (Boolean.TRUE.equals(usuario.getEmailVerificado())) {
			throw new BadRequestException("Este correo ya está verificado", "correo-ya-verificado");
		}

		LocalDateTime ahora = LocalDateTime.now(ZoneId.systemDefault());

		if (usuario.getBloqueadoVerificacionHasta() != null) {
			if (usuario.getBloqueadoVerificacionHasta().isAfter(ahora)) {
				throw new ForbiddenException(
						"Demasiados intentos fallidos. Vuelve a intentarlo pasadas las " + usuario.getBloqueadoVerificacionHasta());
			}
			usuario.setIntentosVerificacion(0);
			usuario.setBloqueadoVerificacionHasta(null);
		}

		if (usuario.getCodigoVerificacion() == null || !usuario.getCodigoVerificacion().equals(codigo)) {
			registrarIntentoFallidoVerificacion(usuario, ahora);
			throw new BadRequestException("Código de verificación incorrecto", "codigo-verificacion-invalido");
		}

		if (usuario.getCodigoVerificacionExpira() == null || usuario.getCodigoVerificacionExpira().isBefore(ahora)) {
			throw new BadRequestException("El código de verificación ha caducado, solicita uno nuevo", "codigo-verificacion-invalido");
		}

		usuario.setEmailVerificado(true);
		usuario.setCodigoVerificacion(null);
		usuario.setCodigoVerificacionExpira(null);
		usuario.setIntentosVerificacion(0);
		usuario.setBloqueadoVerificacionHasta(null);
		usuarioRepository.save(usuario);
	}

	private void registrarIntentoFallidoVerificacion(Usuario usuario, LocalDateTime ahora) {
		int intentos = (usuario.getIntentosVerificacion() == null ? 0 : usuario.getIntentosVerificacion()) + 1;
		usuario.setIntentosVerificacion(intentos);

		if (intentos >= MAX_INTENTOS_VERIFICACION) {
			usuario.setBloqueadoVerificacionHasta(ahora.plusMinutes(MINUTOS_BLOQUEO_VERIFICACION));
			usuario.setIntentosVerificacion(0);
			String codigo = generarCodigoVerificacion();
			usuario.setCodigoVerificacion(codigo);
			usuario.setCodigoVerificacionExpira(ahora.plusMinutes(CODIGO_EXPIRA_MINUTOS));
			usuarioRepository.save(usuario);

			// Sin reenviar, el único código que tiene el usuario (el del mail original) queda
			// permanentemente inválido tras el bloqueo, y no hay endpoint de reenvío: la cuenta
			// quedaría inutilizable para siempre.
			mailService.enviarCodigoVerificacion(usuario.getCorreoUsuario(), usuario.getNombreUsuario(), codigo);

			throw new ForbiddenException("Has superado el límite de intentos fallidos. Tu cuenta está bloqueada temporalmente por seguridad.", "demasiados-intentos");
		}
		usuarioRepository.save(usuario);
	}

	@Override
	@Transactional
	public void solicitarResetPassword(String correo) {
		// Anti-enumeración a propósito: si el correo no existe (o pertenece a una cuenta
		// desactivada), no hacemos nada visible desde fuera -- ni lanzamos, ni devolvemos una
		// señal distinta. El controller siempre responde el mismo mensaje genérico exista o no
		// esa cuenta, igual que ya hace el rate-limiter de login para no revelar qué correos
		// están registrados.
		Optional<Usuario> usuarioOpt = usuarioRepository.findByCorreoUsuario(correo);
		if (usuarioOpt.isEmpty() || !Boolean.TRUE.equals(usuarioOpt.get().getActivo())) {
			log.debug("Solicitud de reset de contraseña para un correo no registrado o inactivo: {}", correo);
			return;
		}

		Usuario usuario = usuarioOpt.get();
		String codigo = generarCodigoVerificacion();
		usuario.setCodigoResetPassword(codigo);
		usuario.setCodigoResetPasswordExpira(LocalDateTime.now(ZoneId.systemDefault()).plusMinutes(CODIGO_EXPIRA_MINUTOS));
		usuarioRepository.save(usuario);

		mailService.enviarCodigoResetPassword(usuario.getCorreoUsuario(), usuario.getNombreUsuario(), codigo);
	}

	@Override
	@Transactional
	public void resetPassword(String correo, String codigo, String nuevaContrasenha) {
		// BadRequestException, no NotFoundException: un correo desconocido debe dar la misma
		// respuesta (400, "código incorrecto") que un código erróneo sobre un correo real --
		// devolver 404 aquí permitiría enumerar cuentas registradas por status code, rompiendo
		// justo la protección anti-enumeración que solicitarResetPassword() ya documenta arriba.
		Usuario usuario = usuarioRepository.findByCorreoUsuario(correo)
				.orElseThrow(() -> new BadRequestException("Código de restablecimiento incorrecto", "codigo-restablecimiento-invalido"));

		LocalDateTime ahora = LocalDateTime.now(ZoneId.systemDefault());

		if (usuario.getBloqueadoResetPasswordHasta() != null) {
			if (usuario.getBloqueadoResetPasswordHasta().isAfter(ahora)) {
				throw new ForbiddenException(
						"Demasiados intentos fallidos. Vuelve a intentarlo pasadas las " + usuario.getBloqueadoResetPasswordHasta());
			}
			usuario.setIntentosResetPassword(0);
			usuario.setBloqueadoResetPasswordHasta(null);
		}

		if (usuario.getCodigoResetPassword() == null || !usuario.getCodigoResetPassword().equals(codigo)) {
			registrarIntentoFallidoResetPassword(usuario, ahora);
			throw new BadRequestException("Código de restablecimiento incorrecto", "codigo-restablecimiento-invalido");
		}

		if (usuario.getCodigoResetPasswordExpira() == null || usuario.getCodigoResetPasswordExpira().isBefore(ahora)) {
			throw new BadRequestException("El código de restablecimiento ha caducado, solicita uno nuevo", "codigo-restablecimiento-invalido");
		}

		usuario.setContrasenhaUsuario(encoder.encode(nuevaContrasenha));
		usuario.setCodigoResetPassword(null);
		usuario.setCodigoResetPasswordExpira(null);
		usuario.setIntentosResetPassword(0);
		usuario.setBloqueadoResetPasswordHasta(null);
		usuarioRepository.save(usuario);

		// Si alguien más tenía sesión abierta con la contraseña robada/compartida, un reset debe
		// cerrarle el acceso también a él, no solo evitar que vuelva a hacer login con la vieja.
		jwtTokenService.revocarTodasLasSesiones(usuario.getIdUsuario());
	}

	private void registrarIntentoFallidoResetPassword(Usuario usuario, LocalDateTime ahora) {
		int intentos = (usuario.getIntentosResetPassword() == null ? 0 : usuario.getIntentosResetPassword()) + 1;
		usuario.setIntentosResetPassword(intentos);

		if (intentos >= MAX_INTENTOS_RESET_PASSWORD) {
			usuario.setBloqueadoResetPasswordHasta(ahora.plusMinutes(MINUTOS_BLOQUEO_RESET_PASSWORD));
			usuario.setIntentosResetPassword(0);
			usuario.setCodigoResetPassword(null);
			usuario.setCodigoResetPasswordExpira(null);
			usuarioRepository.save(usuario);
			throw new ForbiddenException("Has superado el límite de intentos fallidos. Vuelve a solicitar un código pasado un tiempo.", "demasiados-intentos");
		}
		usuarioRepository.save(usuario);
	}

	@Override
	@Transactional
	public Usuario procesarLoginGoogle(String tokenString) {
		try {
			GoogleIdToken idToken = googleIdTokenVerifier.verify(tokenString);
			if (idToken == null) {
				throw new BadRequestException("Token de Google inválido o caducado");
			}

			GoogleIdToken.Payload payload = idToken.getPayload();

			// Un token con firma válida puede igual llevar email_verified=false (p.ej. algunos
			// dominios Workspace permiten direcciones sin verificar): sin este chequeo, alguien
			// con un token así asertando el correo de otra persona entraría directo a su cuenta.
			if (!Boolean.TRUE.equals(payload.getEmailVerified())) {
				throw new BadRequestException("El correo de la cuenta de Google no está verificado.");
			}

			String email = payload.getEmail();
			String nombre = (String) payload.get("name");

			Optional<Usuario> usuarioOpt = usuarioRepository.findByCorreoUsuario(email);

			if (usuarioOpt.isPresent()) {
				Usuario u = usuarioOpt.get();
				if (u.getActivo() != null && !u.getActivo()) {
					throw new ConflictException("La cuenta está desactivada. Ponte en contacto con soporte.");
				}

				if (u.getEmailVerificado() == null || !u.getEmailVerificado()) {
					u.setEmailVerificado(true);
					usuarioRepository.save(u);
				}
				return u;
			}

			Usuario nuevoUsuario = new Usuario();
			nuevoUsuario.setCorreoUsuario(email);
			nuevoUsuario.setNombreUsuario(nombre != null ? nombre : "Usuario Google");
			nuevoUsuario.setNickname("GoogleUser_" + UUID.randomUUID().toString().substring(0, 5));
			nuevoUsuario.setContrasenhaUsuario(encoder.encode(UUID.randomUUID().toString()));
			nuevoUsuario.setActivo(true);
			nuevoUsuario.setEmailVerificado(true);

			Usuario aux = usuarioRepository.save(nuevoUsuario);

			Rol rol = rolRepository.findByNombreRolAndActivo(ROL_DEFAULT, ACTIVO)
					.orElseThrow(() -> new ConflictException(ERR_ROL_NO_EXISTE));
			if (service.asignarRolUsuario(aux.getIdUsuario(), rol.getIdRol()) == null) {
				throw new ConflictException(ERR_ASIGNACION_ROL_FALLIDA);
			}

			Set<Rol> auxRols = new HashSet<>();
			auxRols.add(rol);
			aux.setRoles(auxRols);

			return aux;
		} catch (BadRequestException | ConflictException e) {
			throw e;
		} catch (Exception e) {
			log.error("Error procesando login de Google: {}", e.getMessage());
			throw new BadRequestException("Error de autenticación con Google: " + e.getMessage());
		}
	}

	private String generarCodigoVerificacion() {
		int codigo = random.nextInt(900000) + 100000;
		return String.valueOf(codigo);
	}

	@Transactional(readOnly = true)
	public UsuarioDTO getUsuarioById(Integer id) {
		Usuario usuario = usuarioRepository.findByIdUsuarioAndActivo(id, ACTIVO).orElse(null);
		if (usuario == null) {
			return null;
		}

		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null) {
			throw new BadRequestException("No hay usuario autenticado");
		}

		String correoAuth = authentication.getName();
		boolean isAdmin = authentication.getAuthorities().stream()
				.map(GrantedAuthority::getAuthority)
				.anyMatch(UsuarioServiceImpl::esAuthorityAdmin);

		if (!isAdmin && !correoAuth.equals(usuario.getCorreoUsuario())) {
			throw new ForbiddenException("No tienes permisos para consultar los datos de este usuario.");
		}

		return usuarioMapper.toDtoGetById(usuario);
	}

	@Transactional(readOnly = true)
	public Page<UsuarioDTO> getUsuarios(Pageable pageable) {
		Page<Usuario> usuariosPage = usuarioRepository.findAllByActivo(true, pageable);

		if (usuariosPage.isEmpty()) {
			throw new NoContentException("Actualmente no hay ningún usuario activo.");
		}

		return usuariosPage.map(usuarioMapper::toDtoGetAll);
	}

	@Transactional
	public UsuarioDTO putUsuarioDTO(UsuarioUpdateDTO usuarioDTO) {
		Usuario usuarioAux = usuarioRepository.findByIdUsuarioAndActivo(usuarioDTO.getIdUsuario(), ACTIVO)
				.orElseThrow(() -> new NotFoundException("Usuario no encontrado o inactivo"));

		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null) {
			throw new BadRequestException("No hay usuario autenticado");
		}

		String correoAuth = authentication.getName();
		Set<String> rolesActuales = authentication.getAuthorities().stream()
				.map(GrantedAuthority::getAuthority)
				.collect(Collectors.toSet());

		boolean isAdmin = rolesActuales.stream().anyMatch(UsuarioServiceImpl::esAuthorityAdmin);

		// La autorización se comprueba ANTES de la unicidad del correo: si no, un usuario
		// sin permisos podría averiguar si un correo ajeno ya está registrado (BadRequest)
		// antes de recibir el Forbidden que le correspondería.
		if (!isAdmin && !correoAuth.equals(usuarioAux.getCorreoUsuario())) {
			throw new ForbiddenException("No tienes permisos para modificar los datos de este usuario.");
		}

		validarPermisosModificacionAdmin(isAdmin, correoAuth, usuarioAux, usuarioDTO);

		Optional<Usuario> usuarioConCorreo = usuarioRepository.findByCorreoUsuario(usuarioDTO.getCorreoUsuario());
		if (usuarioConCorreo.isPresent() && !usuarioConCorreo.get().getIdUsuario().equals(usuarioDTO.getIdUsuario())) {
			throw new BadRequestException("El correo ya existe en la base de datos");
		}

		usuarioAux.setNombreUsuario(usuarioDTO.getNombreUsuario());
		usuarioAux.setApellidoUsuario(usuarioDTO.getApellidoUsuario());
		usuarioAux.setNickname(usuarioDTO.getNickname());

		actualizarDatosSensiblesUsuario(usuarioAux, usuarioDTO);
		// La activación/desactivación de la cuenta NO se toca desde este PUT genérico de perfil:
		// usuarioAux ya viene de findByIdUsuarioAndActivo(..., ACTIVO), así que aquí siempre es
		// true; el único camino real para desactivar es deleteUsuarioDTO, que además hace la
		// limpieza real (quita roles, libera nickname/email). Antes, UsuarioUpdateDTO exponía un
		// campo "activo" que este método aplicaba directo -- cualquier cliente que lo mandara en
		// false (a propósito o por un bug de formulario) dejaba la cuenta bloqueada a medias, sin
		// esa limpieza y sin forma de recuperarla.

		return usuarioMapper.toDtoPut(usuarioRepository.save(usuarioAux));
	}

	private void validarPermisosModificacionAdmin(boolean isAdmin, String correoAuth, Usuario usuarioAux, UsuarioUpdateDTO usuarioDTO) {
		if (isAdmin && !correoAuth.equals(usuarioAux.getCorreoUsuario())) {
			boolean cambioContrasenha = usuarioDTO.getContrasenhaUsuario() != null
					&& !encoder.matches(usuarioDTO.getContrasenhaUsuario(), usuarioAux.getContrasenhaUsuario());
			boolean cambioCorreo = usuarioDTO.getCorreoUsuario() != null
					&& !usuarioDTO.getCorreoUsuario().equals(usuarioAux.getCorreoUsuario());

			if (cambioContrasenha || cambioCorreo) {
				throw new ForbiddenException("El ADMIN no puede cambiar el correo ni la contraseña de otros usuarios.");
			}
		}
	}

	private void actualizarDatosSensiblesUsuario(Usuario usuarioAux, UsuarioUpdateDTO usuarioDTO) {
		if (usuarioDTO.getCorreoUsuario() != null && !usuarioDTO.getCorreoUsuario().equals(usuarioAux.getCorreoUsuario())) {
			usuarioAux.setCorreoUsuario(usuarioDTO.getCorreoUsuario());
			usuarioAux.setEmailVerificado(false);

			String codigo = generarCodigoVerificacion();
			usuarioAux.setCodigoVerificacion(codigo);
			usuarioAux.setCodigoVerificacionExpira(LocalDateTime.now(ZoneId.systemDefault()).plusMinutes(CODIGO_EXPIRA_MINUTOS));
			usuarioAux.setIntentosVerificacion(0);

			mailService.enviarCodigoVerificacion(usuarioAux.getCorreoUsuario(), usuarioAux.getNombreUsuario(), codigo);
		}

		if (usuarioDTO.getContrasenhaUsuario() != null) {
			usuarioAux.setContrasenhaUsuario(encoder.encode(usuarioDTO.getContrasenhaUsuario()));
		}
	}

	@Transactional
	public UsuarioDTO activarUsuarioDTO(UsuarioDTO usuarioDTO) {
		Optional<Usuario> optional = usuarioRepository.findByCorreoUsuario(usuarioDTO.getCorreoUsuario());
		if (optional.isPresent()) {
			Usuario usuarioAux = optional.get();

			// Excluye la propia fila (la que se está reactivando) del choque: sin este
			// "distinto del actual", reactivar reenviando el MISMO nickname de siempre
			// (el caso más común) fallaba en falso, porque existsByNickname encuentra la
			// fila del propio usuario desactivado. El registro en frío sí valida esto
			// (AuthControllerImpl.crearUsuario), pero esta rama de reactivación no lo hacía.
			boolean cambiaNickname = !Objects.equals(usuarioDTO.getNickname(), usuarioAux.getNickname());
			if (cambiaNickname && usuarioRepository.existsByNickname(usuarioDTO.getNickname())) {
				throw new DuplicateException("Ese nickname ya está en uso.", "nickname-en-uso");
			}

			usuarioAux.setNombreUsuario(usuarioDTO.getNombreUsuario());
			usuarioAux.setApellidoUsuario(usuarioDTO.getApellidoUsuario());
			usuarioAux.setNickname(usuarioDTO.getNickname());
			usuarioAux.setContrasenhaUsuario(encoder.encode(usuarioDTO.getContrasenhaUsuario()));
			usuarioAux.setActivo(true);

			String codigo = generarCodigoVerificacion();
			usuarioAux.setEmailVerificado(false);
			usuarioAux.setCodigoVerificacion(codigo);
			usuarioAux.setCodigoVerificacionExpira(LocalDateTime.now(ZoneId.systemDefault()).plusMinutes(CODIGO_EXPIRA_MINUTOS));
			usuarioAux.setIntentosVerificacion(0);
			usuarioAux.setBloqueadoVerificacionHasta(null);

			Usuario usuarioUpd = usuarioRepository.save(usuarioAux);

			Rol rol = rolRepository.findByNombreRolAndActivo(ROL_DEFAULT, ACTIVO)
					.orElseThrow(() -> new ConflictException(ERR_ROL_NO_EXISTE));
			if (service.asignarRolUsuario(usuarioUpd.getIdUsuario(), rol.getIdRol()) == null) {
				throw new ConflictException(ERR_ASIGNACION_ROL_FALLIDA);
			}

			mailService.enviarCodigoVerificacion(usuarioUpd.getCorreoUsuario(), usuarioUpd.getNombreUsuario(), codigo);

			return usuarioMapper.toDtoPost(usuarioUpd);
		}
		return null;
	}

	@Transactional
	public UsuarioDTO deleteUsuarioDTO(Integer id) {
		Usuario usuario = usuarioRepository.findByIdUsuarioAndActivo(id, ACTIVO).orElse(null);
		if (usuario == null) {
			return null;
		}

		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null) {
			throw new BadRequestException("No hay usuario autenticado");
		}

		String correoAuth = authentication.getName();
		Set<String> rolesActuales = authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority)
				.collect(Collectors.toSet());

		boolean isAdmin = rolesActuales.stream().anyMatch(UsuarioServiceImpl::esAuthorityAdmin);

		if (!isAdmin && !correoAuth.equals(usuario.getCorreoUsuario())) {
			throw new ForbiddenException("No tienes permisos para eliminar a este usuario.");
		}

		boolean esAutoEliminacion = correoAuth.equals(usuario.getCorreoUsuario());

		// Se quitan los roles ANTES de anonimizar el correo: quitarRolUsuario reautoriza
		// la operación buscando al usuario autenticado por su correo actual en BD. Si se
		// ejecutara después de cambiar el correo (como antes), el auto-flush de Hibernate
		// ya habría persistido el nuevo correo "deleted_..." antes de esa búsqueda, que
		// entonces no encontraría al usuario por su correo original y rompería el
		// auto-borrado de cualquier usuario no-admin con roles asignados (es decir, todos,
		// por el rol FREE que se asigna siempre al registrarse).
		Set<Rol> rolesAQuitar = new HashSet<>(usuario.getRoles());
		rolesAQuitar.forEach(rol -> service.quitarRolUsuario(id, rol.getIdRol()));

		usuario.setActivo(false);
		usuario.setNombreUsuario("Usuario");
		usuario.setApellidoUsuario("Borrado");
		usuario.setCorreoUsuario("deleted_" + id + "@scorevia.local");
		usuario.setContrasenhaUsuario("");
		usuario.setUrlFoto(null);
		// Sin liberar el nickname, la unique constraint de BBDD lo deja atrapado en la fila
		// anonimizada para siempre: ni un tercero ni la misma persona registrándose de nuevo
		// podría volver a usarlo.
		usuario.setNickname("del_" + id);

		Usuario usuarioUpd = usuarioRepository.save(usuario);

		if (!isAdmin || esAutoEliminacion) {
			SecurityContextHolder.getContext().setAuthentication(null);
			SecurityContextHolder.clearContext();
		}

		return usuarioMapper.toDtoDelete(usuarioUpd);
	}

	// Tamaño de lote para recorrer la tabla completa al exportar a Excel -- independiente de
	// la ventana en memoria de SXSSFWorkbook (ver ExcelUtils), esto es solo cuántas filas se
	// piden a BD por consulta.
	private static final int TAMANIO_LOTE_EXPORT = 1000;

	@Override
	@Transactional(readOnly = true)
	public long contarUsuarios() {
		return usuarioRepository.count();
	}

	@Override
	@Transactional(readOnly = true)
	public void exportarTodosStreaming(Consumer<UsuarioDTO> consumidor) {
		// Paginación por keyset (id > último visto), no por OFFSET: un export de toda la
		// tabla con miles de usuarios no debe degradar según avanza (OFFSET obliga a la BD a
		// recorrer y descartar todas las filas anteriores en cada página).
		Integer ultimoId = 0;
		List<Usuario> lote;
		do {
			lote = usuarioRepository.findByIdUsuarioGreaterThanOrderByIdUsuarioAsc(
					ultimoId, PageRequest.of(0, TAMANIO_LOTE_EXPORT));
			for (Usuario usuario : lote) {
				consumidor.accept(usuarioMapper.toDtoExcel(usuario));
				ultimoId = usuario.getIdUsuario();
			}
		} while (lote.size() == TAMANIO_LOTE_EXPORT);
	}

	public boolean existsUsuario(String correo) {
		return usuarioRepository.existsByCorreoUsuario(correo);
	}

	public boolean existsNickname(String nickname) {
		return usuarioRepository.existsByNickname(nickname);
	}

	public boolean existsUsuarioDesactivado(String correo) {
		return usuarioRepository.existsByCorreoUsuarioAndActivo(correo, false);
	}

	private static boolean esAuthorityAdmin(String authority) {
		return ROLE_ADMIN.equals(authority) || "ADMIN".equals(authority);
	}

	@Override
	@Transactional(readOnly = true)
	public Map<Integer, String> obtenerNicknames(List<Integer> idsUsuarios) {
		Map<Integer, String> nicknames = new HashMap<>();
		usuarioRepository.findAllById(idsUsuarios).forEach(u -> nicknames.put(u.getIdUsuario(), u.getNickname()));
		return nicknames;
	}

	@Override
	@Transactional(readOnly = true)
	public boolean existeUsuario(Integer idUsuario) {
		return usuarioRepository.existsById(idUsuario);
	}

	@Override
	@Transactional(readOnly = true)
	public boolean usuarioActivo(Integer idUsuario) {
		return usuarioRepository.existsByIdUsuarioAndActivo(idUsuario, true);
	}
}