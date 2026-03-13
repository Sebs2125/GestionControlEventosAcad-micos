package Modelo;


import javax.persistence.*;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name= "usuario")

public class Usuario implements java.io.Serializable
{
        public enum Rol
        {
            ADMINISTRADOR,ORGANIZADOR,PARTICIPANTE
        }

        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private long id;

        @Column(unique = true, nullable = false)
        private String username;

        @Column(nullable = false)
        private String password;

        @Column(nullable = false)
        private String nombre;

        @Column(nullable = false)
        private String email;

    @Enumerated(EnumType.STRING)
        @Column(nullable = false)
        private Rol rol;

        private boolean activo = true;

        @Column(name = "fecha_registro")
        private LocalDateTime fechaRegistro = LocalDateTime.now();

        @OneToMany(mappedBy = "organizador", cascade = CascadeType.ALL, fetch = FetchType.LAZY )
        private Set<Evento> eventosOrganizados = new HashSet<>();

        @OneToMany(mappedBy = "participante", cascade = CascadeType.ALL, fetch = FetchType.LAZY )
        private Set<InscripcionUsuario> inscripciones = new HashSet<>();

        public Usuario(){}


    public LocalDateTime getFechaRegistro() {
        return fechaRegistro;
    }

    public Usuario(String username, String password, String nombre, String email, Rol rol ) {
        this.username = username;
        this.password = password;
        this.nombre = nombre;
        this.email = email;
        this.rol = rol;
    }


    public long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public String getNombre() {
        return nombre;
    }

    public String getEmail() {
        return email;
    }

    public Rol getRol() {
        return rol;
    }

    public boolean isActivo() {
        return activo;
    }

    public boolean esAdmin() {
            return this.rol == Rol.ADMINISTRADOR;
    }

    public boolean esOrganizador() {
            return this.rol == Rol.ORGANIZADOR || this.rol == Rol.ADMINISTRADOR;
    }

    public void setRol(Rol rol) {
        this.rol = rol;
    }

    public void setActivo(boolean activo) {
        this.activo = activo;
    }

    public boolean puedeGestionarEvento(Evento evento) {
            return esAdmin() || (esOrganizador() && evento.getOrganizador().getId() == (this.id));
    }

}