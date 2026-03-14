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
            // FIX 4 (Media): excluir /api/* del before que redirige a /login.
            // Antes, las rutas /api/* eran interceptadas aquí y recibían una
            // redirección HTML 302 en vez del JSON 401 que devuelve ApiControlador,
            // rompiendo a clientes REST. ApiControlador ya maneja su propia auth.
            if (esRutaProtegida(path) && ctx.sessionAttribute("usuario") == null)
            {
                ctx.redirect("/login");
            }
        });

        app.before("/organizador/*", this::requerirOrganizador);
    }

    private void mostrarLogin(Context ctx)
    {
        Map<String, Object> modelo = new HashMap<>();
        modelo.put("error", ctx.queryParam("error"));
        modelo.put("exito", ctx.queryParam("exito"));
        ctx.render("/login.html", modelo);
    }

    private void procesarLogin(Context ctx)
    {
        String username = ctx.formParam("username");
        String password = ctx.formParam("password");

        Usuario usuario = usuarioServicio.validarLogin(username, password);

        if ( usuario != null )
        {
            ctx.sessionAttribute("usuario", usuario);
            ctx.sessionAttribute("rol", usuario.getRol().toString());

            String redirect = ctx.queryParam("redirect");

            if (redirect != null && !redirect.isEmpty())
            {
                ctx.redirect(redirect);
            }
            else
            {
                switch (usuario.getRol())
                {
                    case ADMINISTRADOR -> ctx.redirect("/admin/dashboard");
                    case ORGANIZADOR   -> ctx.redirect("/organizador/dashboard");
                    default            -> ctx.redirect("/eventos");
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
        ctx.render("/registro.html", modelo);
    }

    private void procesarRegistro(Context ctx)
    {
        try
        {
            String username = ctx.formParam("username");
            String password = ctx.formParam("password");
            String nombre   = ctx.formParam("nombre");
            String email    = ctx.formParam("email");

            usuarioServicio.crearUsuario(username, password, nombre, email, Usuario.Rol.PARTICIPANTE);

            ctx.redirect("/login?exito=Registro exitoso. Inicia sesion.");
        } catch (Exception e)
        {
            ctx.redirect("/registro?error=" + e.getMessage());
        }
    }

    private void logout(Context ctx)
    {
        ctx.req().getSession().invalidate();
        ctx.redirect("/login");
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
        // FIX 4: /api/* se excluye aquí porque ApiControlador devuelve JSON 401,
        // no una redirección HTML. Incluirlo aquí sobrescribía esa respuesta.
        return path.startsWith("/admin/") ||
                path.startsWith("/organizador/") ||
                path.startsWith("/dashboard");
        // NOTA: /api/* eliminado intencionalmente — manejado por ApiControlador
    }
}