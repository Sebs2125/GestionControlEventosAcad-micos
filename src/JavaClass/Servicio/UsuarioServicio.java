package Servicio;


import configuracion.DatabaseConfig;
import modelo.Usuario;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.mindrot.jbcrypt.BCrypt;



import  java.util.list;






public class UsuarioServicio {



    public void crearAdminInicial(){

        Session session = DatabaseConfig.getSessionFactory().openSession();
        Transaction tx= null;

        try{
            tx =session.beginTransaction();

            Long count = (Long) session.createQuery(
                    "SELECT COUNT(u) FROM Usuario u WHERE u.rol = 'ADMINISTRADOR'"

            ).uniqueResult();
            if(count == 0)
            {
                Usuario admin = new Usuario(
                        "admin",
                        BCrypt.hashpw("admin123", BCrypt.gensalt()),
                        "Administrador",
                        "admin@universidad.edu",
                        Usuario.Rol.ADMINISTRADOR
                );
                session.save(admin);
                System.out.println("Usuario administrador creado: admin / admin123");

            }


            tx.commit();


        } catch (Exception e) {
                if (tx != null) tx.rollback();
                e.printStackTrace();
            } finally {
                session.close();
            }

    }


    public Usuario crearUsuario(String username, String password, String nombre,
                                String email, Usuario.Rol rol) {
        Session session = DatabaseConfig.getSessionFactory().openSession();
        Transaction tx = null;
        Usuario usuario = null;

        try {
            tx = session.beginTransaction();

            // Verificar si username ya existe
            Usuario existente = buscarPorUsername(username);
            if (existente != null) {
                throw new RuntimeException("El username ya existe");
            }

            usuario = new Usuario(
                    username,
                    BCrypt.hashpw(password, BCrypt.gensalt()),
                    nombre,
                    email,
                    rol
            );

            session.save(usuario);
            tx.commit();

        } catch (Exception e) {
            if (tx != null) tx.rollback();
            throw e;
        } finally {
            session.close();
        }

        return usuario;
    }


    public Usuario buscarPorUsername(String username) {
        Session session = DatabaseConfig.getSessionFactory().openSession();
        try {
            return (Usuario) session.createQuery(
                            "FROM Usuario WHERE username = :username"
                    ).setParameter("username", username)
                    .uniqueResult();
        } finally {
            session.close();
        }
    }

    public Usuario validarLogin(String username, String password) {
        Usuario usuario = buscarPorUsername(username);

        if (usuario != null && usuario.isActivo() &&
                BCrypt.checkpw(password, usuario.getPassword())) {
            return usuario;
        }
        return null;
    }

    public void asignarRolOrganizador(Long usuarioId) {
        cambiarRol(usuarioId, Usuario.Rol.ORGANIZADOR);
    }

    public void revocarRolOrganizador(Long usuarioId) {
        cambiarRol(usuarioId, Usuario.Rol.PARTICIPANTE);
    }

    private void cambiarRol(Long usuarioId, Usuario.Rol nuevoRol) {
        Session session = DatabaseConfig.getSessionFactory().openSession();
        Transaction tx = null;

        try {
            tx = session.beginTransaction();
            Usuario usuario = session.get(Usuario.class, usuarioId);

            if (usuario != null && usuario.getRol() != Usuario.Rol.ADMINISTRADOR) {
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

    public List<Usuario> listarTodos() {
        Session session = DatabaseConfig.getSessionFactory().openSession();
        try {
            return session.createQuery("FROM Usuario", Usuario.class).list();
        } finally {
            session.close();
        }
    }

    public void cambiarEstadoUsuario(Long usuarioId, boolean activo) {
        Session session = DatabaseConfig.getSessionFactory().openSession();
        Transaction tx = null;

        try {
            tx = session.beginTransaction();
            Usuario usuario = session.get(Usuario.class, usuarioId);

            // No permitir bloquear al admin inicial
            if (usuario != null && usuario.getRol() != Usuario.Rol.ADMINISTRADOR) {
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





}
