package Controlador;

import Modelo.Usuario;
import Servicio.UsuarioServicio;
import io.javalin.Javalin;
import io.javalin.http.Context;

import java.util.HashMap;
import java.util.Map;

public class AutorizacionControlador
{
    private final UsuarioServicio usuarioServicio;

    public AutorizacionControlador( UsuarioServicio usuarioServicio )
    {
        this.usuarioServicio = usuarioServicio;
    }

    public void registrarRutas( Javalin app )
    {
        app.get("/login", this::mostrarLogin);
        app.post("/login", this::procesarLogin);
        app.get("/registro", this::mostrarRegistro);
        app.post("/registro", this::procesarRegistro);
        app.get("/logout", this::logout);

        app.before( ctx -> {
            String path = ctx.path();

            if (esRutaProtegida(path) && ctx.sessionAttribute("usuario") == null )
            {
                ctx.redirect("/login");
            }
        });

        app.before("/admin/*", this::requerirAdmin);
        app.before("/organizador/*", this::requerirOrganizador);

    }

    private void mostrarLogin(Context ctx)
    {
        Map<String, Object> modelo = new HashMap<>();
        modelo.put("error", ctx.queryParam("error"));
        modelo.put("exito", ctx.queryParam("exito"));
        ctx.render("/templates/login.html", modelo);
    }

    private void procesarLogin(Context ctx )
    {
        String username = ctx.formParam("username");
        String password = ctx.formParam("password");

        Usuario usuario = usuarioServicio.validarLogin(username, password );

        if ( usuario == null )
        {
            ctx.sessionAttribute("usuario", usuario );
            ctx.sessionAttribute("rol", usuario.getRol().toString() );

            String redirect = ctx.queryParam("redirect");

            if ( redirect != null )
            {
                ctx.redirect( redirect );
            }
            else
            {
                switch ( usuario.getRol() )
                {
                    case ADMINISTRADOR -> ctx.redirect("/admin/dashboard");
                    case ORGANIZADOR -> ctx.redirect("/organizador/dashboard");
                    default -> ctx.redirect("/eventos");
                }
            }

        }
        else
        {
            ctx.redirect("/login?error=Credenciales invalidas");
        }

    }

    private void mostrarRegistro(Context ctx)
    {
        Map<String, Object> modelo = new HashMap<>();
        modelo.put("error", ctx.queryParam("error"));
        ctx.render("/templates/registro.html", modelo);
    }

    private void procesarRegistro(Context ctx)
    {
        try
        {
            String username = ctx.formParam("username");
            String password = ctx.formParam("password");
            String nombre = ctx.formParam("nombre");
            String email = ctx.formParam("email");

            usuarioServicio.crearUsuario(username, password, nombre, email, Usuario.Rol.PARTICIPANTE);

            ctx.redirect("/login?exito=Registro exitoso. Inicia sesión.");
        } catch (Exception e )
        {
            ctx.redirect("/registro?error=" + e.getMessage());
        }
    }

    private void logout(Context ctx)
    {
        ctx.req().getSession().invalidate();
        ctx.redirect("/login");
    }

    private void requerirAdmin(Context ctx)
    {
        Usuario u = ctx.sessionAttribute("usuario");

        if (u == null || !u.esAdmin())
        {
            ctx.status(403).result("Acceso denegado");
        }
    }

    private void requerirOrganizador(Context ctx)
    {
        Usuario u = ctx.sessionAttribute("usuario");

        if (u == null || !u.esOrganizador())
        {
            ctx.status(403).result("Acceso denegado");
        }
    }

    private boolean esRutaProtegida(String path)
    {
        return path.startsWith("/admin/") ||
                path.startsWith("/organizador/") ||
                path.startsWith("/api/") ||
                path.startsWith("/dashboard");
    }

}