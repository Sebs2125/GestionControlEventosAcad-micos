package Servicio;

import ConfidencialUsuario.Password;
import Eventos.Configuracion.BaseDeDatosConfiguracion;
import Modelo.Usuario;
import org.hibernate.Session;
import org.hibernate.Transaction;

import javax.persistence.NoResultException;
import java.util.List;

public class UsuarioServicio
{

    private static final String ADMIN_USERNAME = "admin";
    private static final String ADMIN_PASSWORD = "admin123";

    public void inicializarAdmin() {
        Session session = BaseDeDatosConfiguracion.getSessionFactory().openSession();
        Transaction tx = null;

        try {
            tx = session.beginTransaction();

            Long count = (Long) session.createQuery(
                    "SELECT COUNT(u) FROM Usuario u WHERE u.rol = 'ADMINISTRADOR'"
            ).uniqueResult();

            if (count == 0) {
                Usuario admin = new Usuario(
                        ADMIN_USERNAME,
                        Password.hash(ADMIN_PASSWORD),
                        "Administrador del Sistema",
                        "admin@universidad.edu",
                        Usuario.Rol.ADMINISTRADOR
                );
                session.save(admin);
                System.out.println("ADMIN CREADO:");
                System.out.println("Usuario: " + ADMIN_USERNAME);
                System.out.println("Contraseña: " + ADMIN_PASSWORD);
            }

            tx.commit();
        } catch (Exception e) {
            if (tx != null) tx.rollback();
            throw e;
        } finally {
            session.close();
        }
    }

    public Usuario crearUsuario(String username, String password, String nombre,
                                String email, Usuario.Rol rol) {
        validarDatos(username, password, email);

        Session session = BaseDeDatosConfiguracion.getSessionFactory().openSession();
        Transaction tx = null;

        try {
            tx = session.beginTransaction();

            if (existeUsername(username)) {
                throw new IllegalArgumentException("El usuario ya existe");
            }

            Usuario usuario = new Usuario(
                    username,
                    Password.hash(password),
                    nombre,
                    email,
                    rol
            );

            session.save(usuario);
            tx.commit();

            return usuario;

        } catch (Exception e) {
            if (tx != null) tx.rollback();
            throw e;
        } finally {
            session.close();
        }
    }

    public Usuario validarLogin(String username, String password) {
        Session session = BaseDeDatosConfiguracion.getSessionFactory().openSession();

        try {
            Usuario usuario = session.createQuery(
                            "FROM Usuario WHERE username = :username AND activo = true",
                            Usuario.class)
                    .setParameter("username", username)
                    .getSingleResult();

            if (Password.verify(password, usuario.getPassword())) {
                return usuario;
            }

        } catch (NoResultException e) {
            return null;
        } finally {
            session.close();
        }

        return null;
    }

    public Usuario buscarPorId(Long id) {
        Session session = BaseDeDatosConfiguracion.getSessionFactory().openSession();
        try {
            return session.get(Usuario.class, id);
        } finally {
            session.close();
        }
    }

    public Usuario buscarPorUsername(String username) {
        Session session = BaseDeDatosConfiguracion.getSessionFactory().openSession();
        try {
            return session.createQuery(
                            "FROM Usuario WHERE username = :username", Usuario.class)
                    .setParameter("username", username)
                    .uniqueResult();
        } finally {
            session.close();
        }
    }

    public List<Usuario> listarTodos() {
        Session session = BaseDeDatosConfiguracion.getSessionFactory().openSession();
        try {
            return session.createQuery(
                            "FROM Usuario ORDER BY fechaRegistro DESC", Usuario.class)
                    .list();
        } finally {
            session.close();
        }
    }

    public long contarPorRol( Usuario.Rol rol)
    {
        Session session = BaseDeDatosConfiguracion.getSessionFactory().openSession();

        try {
            return (Long) session.createQuery(
                    "SELECT COUNT(u) FROM Usuario u WHERE u.rol = :rol")
                    .setParameter("rol", rol)
                    .uniqueResult();
        } finally {
            session.close();
        }

    }

    public void asignarRol(Long usuarioId, Usuario.Rol nuevoRol)
    {
        cambiarRol(usuarioId, nuevoRol);
    }

    public void cambiarEstado(Long usuarioId, boolean activo) {
        Session session = BaseDeDatosConfiguracion.getSessionFactory().openSession();
        Transaction tx = null;

        try {
            tx = session.beginTransaction();
            Usuario usuario = session.get(Usuario.class, usuarioId);

            if (usuario != null && !usuario.esAdmin()) {
                usuario.setActivo(activo);
                session.update(usuario);
            }

            tx.commit();
        } catch (Exception e) {
            if (tx != null) tx.rollback();
            throw e;
        } finally {
            session.close();
        }
    }

    private void cambiarRol(Long usuarioId, Usuario.Rol nuevoRol) {
        Session session = BaseDeDatosConfiguracion.getSessionFactory().openSession();
        Transaction tx = null;

        try {
            tx = session.beginTransaction();
            Usuario usuario = session.get(Usuario.class, usuarioId);

            if (usuario != null && !usuario.esAdmin()) {
                usuario.setRol(nuevoRol);
                session.update(usuario);
            }

            tx.commit();
        } catch (Exception e) {
            if (tx != null) tx.rollback();
            throw e;
        } finally {
            session.close();
        }
    }

    private boolean existeUsername(String username) {
        return buscarPorUsername(username) != null;
    }

    private void validarDatos(String username, String password, String email)
    {

        if (username == null || username.length() < 3) {
            throw new IllegalArgumentException("Usuario debe tener al menos 3 caracteres");
        }
        if (password == null || password.length() < 6) {
            throw new IllegalArgumentException("Contraseña debe tener al menos 6 caracteres");
        }
        if (email == null || !email.contains("@")) {
            throw new IllegalArgumentException("Email inválido");
        }
    }
}