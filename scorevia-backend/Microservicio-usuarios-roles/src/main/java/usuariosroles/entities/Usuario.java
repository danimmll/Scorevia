package usuariosroles.entities;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import lombok.*;

@Entity
@Table(name = "usuarios", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"correo_usuario"}, name = "correo_usuario_UNIQUE"),
        @UniqueConstraint(columnNames = {"nickname"}, name = "nickname_UNIQUE") // Importante: Único a nivel BBDD
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_usuario")
    private Integer idUsuario;

    @Column(name = "nombre_usuario")
    @NotEmpty(message = "El nombre del usuario no puede estar vacío")
    private String nombreUsuario;

    @Column(name = "apellido_usuario")
    @NotEmpty(message = "El apellido del usuario no puede estar vacío")
    private String apellidoUsuario;

    // Nuevo campo Nickname con validación para evitar "admins" o carácteres raros
    @Column(name = "nickname", length = 50)
    @Pattern(regexp = "^(?!(?i)(admin|moderador|soporte|system|root)).{3,20}$",
            message = "El nickname debe tener entre 3 y 20 caracteres y no puede usar palabras reservadas")
    @NotEmpty(message = "El nickname no puede estar vacío")
    private String nickname;

    @Column(name = "correo_usuario")
    @Email(regexp = "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$", message = "El correo debe tener un formato válido")
    @NotEmpty(message = "El correo del usuario no puede estar vacío")
    private String correoUsuario;

    @Column(name = "contrasenha_usuario")
    @Pattern(regexp = "^(?=.*[!@#$%^&*(),.?\":{}|<>])[A-Za-z\\d!@#$%^&*(),.?\":{}|<>]{8,}$", message = "La contraseña debe tener al menos 8 caracteres y un carácter especial")
    @NotEmpty(message = "La contraseña no puede estar vacía")
    private String contrasenhaUsuario;

    @Column(name = "activo")
    private Boolean activo;

    @Column(name = "email_verificado")
    private Boolean emailVerificado;

    @Column(name = "codigo_verificacion")
    private String codigoVerificacion;

    @Column(name = "codigo_verificacion_expira")
    private java.time.LocalDateTime codigoVerificacionExpira;

    @Column(name = "intentos_verificacion")
    @Builder.Default
    private Integer intentosVerificacion = 0;

    @Column(name = "bloqueado_verificacion_hasta")
    private java.time.LocalDateTime bloqueadoVerificacionHasta;

    @Column(name = "intentos_login")
    @Builder.Default
    private Integer intentosLogin = 0;

    @Column(name = "bloqueado_login_hasta")
    private java.time.LocalDateTime bloqueadoLoginHasta;

    // Campos propios de "olvidé mi contraseña", separados de codigo_verificacion/etc (los de
    // arriba son del flujo de verificación de email al registrarse) para que solicitar un reset
    // de contraseña no invalide un código de verificación de email todavía pendiente, y viceversa.
    @Column(name = "codigo_reset_password")
    private String codigoResetPassword;

    @Column(name = "codigo_reset_password_expira")
    private java.time.LocalDateTime codigoResetPasswordExpira;

    @Column(name = "intentos_reset_password")
    @Builder.Default
    private Integer intentosResetPassword = 0;

    @Column(name = "bloqueado_reset_password_hasta")
    private java.time.LocalDateTime bloqueadoResetPasswordHasta;

    @Column(name = "url_foto")
    private String urlFoto;

    @Column(name = "es_premium")
    @Builder.Default
    private Boolean esPremium = false;

    @ManyToMany(cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    @JoinTable(
            name = "usuarios_roles",
            joinColumns = @JoinColumn(name = "id_usuario"),
            inverseJoinColumns = @JoinColumn(name = "id_rol")
    )
    @Builder.Default
    private Set<Rol> roles = new HashSet<>();

    @PrePersist
    public void prePersist() {
        if (activo == null) {
            activo = true;
        }
        if (emailVerificado == null) {
            emailVerificado = false;
        }
        if (intentosVerificacion == null) {
            intentosVerificacion = 0;
        }
        if (intentosLogin == null) {
            intentosLogin = 0;
        }
        if (intentosResetPassword == null) {
            intentosResetPassword = 0;
        }
        if (esPremium == null) {
            esPremium = false;
        }
        if (nickname == null || nickname.trim().isEmpty()) {
            nickname = "User_" + UUID.randomUUID().toString().substring(0, 8);
        }

        if (urlFoto == null || urlFoto.trim().isEmpty()) {
            String seed = nickname.replaceAll("\\s+", "");
            urlFoto = "https://api.dicebear.com/7.x/avataaars/svg?seed=" + seed;
        }
    }
}