package modelo;


import javax.persistence.*;
import java.util.Set;

@Entity
@Table(name= "usuario")


public class Usuario {



        public enum Rol {

            ADMINISTRADOR,ORGANIZADOR,PARTICIPANTE



        }

        @Id
        @GeneratedValue(strategy = GenerationType.IDENRITY)
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

        public Usuario(){}

    public Usuario(String username, String password, String nombre, String email, modelo.Usuario.Rol rol) {
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
}