package Controlador;

import Modelo.Usuario;
import Servicio.EventoServicio;
import Servicio.UsuarioServicio;
import io.javalin.Javalin;
import io.javalin.http.Context;

import java.util.HashMap;
import java.util.Map;

public class AdminControlador
{
    private final EventoServicio eventoServicio;
    private final UsuarioServicio usuarioServicio;

    public AdminControlador ( EventoServicio eventoServicio, UsuarioServicio usuarioServicio )
    {
        this.eventoServicio = eventoServicio;
        this.usuarioServicio = usuarioServicio;
    }

    public void registrarRutas( Javalin app )
    {
        app.before("/admin/*", ctx -> {
            Usuario usuario = ctx.sessionAttribute("usuario");

            if ( usuario == null || !usuario.esAdmin() )
            {
                ctx.status( 403 ).result("Acceso denegado");
            }

        });

        //Dashboard admin
        app.get("/admin/dashboard", this::dashboard );

        //Gestion de usuario (Referido al punto 8)
        app.get("/admin/usuarios", this::listarUsuarios);
        app.post("/admin/usuarios/{id}/bloquear", this::bloquearUsuario);
        app.post("/admin/usuarios/{id}/desbloquear", this::desbloquearUsuario);
        app.post("/admin/usuarios/{id}/rol", this::cambiarRolUsuario);

        // Gestión de eventos
        app.get("/admin/eventos", this::listarEventos);
        app.post("/admin/eventos/{id}/eliminar", this::eliminarEvento);

    }

    private void dashboard( Context ctx )
    {
        Map<String, Object> modelo = baseModelo(ctx);
        modelo.put("totalUsuarios", usuarioServicio.listarTodos().size());
        modelo.put("totalEventos", eventoServicio.listarTodos().size());
        modelo.put("totalOrganizadores", usuarioServicio.contarPorRol(Usuario.Rol.ORGANIZADOR));
        modelo.put("totalParticipantes", usuarioServicio.contarPorRol(Usuario.Rol.PARTICIPANTE));
        modelo.put("ultimosEventos", eventoServicio.listarTodos());
        ctx.render("/templates/admin/panel.html", modelo);
    }

    private void listarUsuarios( Context ctx )
    {
        Map<String, Object> modelo = baseModelo(ctx);
        modelo.put("usuarios", usuarioServicio.listarTodos());
        modelo.put("exito", ctx.queryParam("exito"));
        modelo.put("error", ctx.queryParam("error"));
        ctx.render("/templates/admin/usuarios.html", modelo);
    }

    private void bloquearUsuario( Context ctx )
    {
        try
        {
            Long id = Long.parseLong(ctx.pathParam("id"));
            usuarioServicio.cambiarEstado(id, false);
            ctx.redirect("/admin/usuarios?exito=Usuario bloqueado correctamente");
        } catch ( Exception e )
        {
            ctx.redirect("/admin/usuarios?error=" + e.getMessage());
        }
    }

    private void desbloquearUsuario( Context ctx )
    {
        try
        {
            Long id = Long.parseLong(ctx.pathParam("id"));
            usuarioServicio.cambiarEstado(id, true);
            ctx.redirect("/admin/usuarios?exito=Usuario desbloqueado correctamente");
        } catch ( Exception e )
        {
            ctx.redirect("/admin/usuarios?error=" + e.getMessage());
        }
    }

    private void cambiarRolUsuario( Context ctx )
    {
        try
        {
            Long id = Long.parseLong(ctx.pathParam("id"));
            String nuevoRol = ctx.formParam("rol");
            Usuario.Rol rol = Usuario.Rol.valueOf(nuevoRol);
            usuarioServicio.asignarRol(id, rol);
            ctx.redirect("/admin/usuarios?exito=Rol actualizado correctamente");
        } catch ( Exception e )
        {
            ctx.redirect("/admin/usuarios?error=" + e.getMessage());
        }
    }

    //Eventos:
    private void listarEventos( Context ctx )
    {
        Map<String, Object> modelo = baseModelo(ctx);
        modelo.put("eventos", eventoServicio.listarTodos());
        modelo.put("exito", ctx.queryParam("exito"));
        modelo.put("error", ctx.queryParam("error"));
        ctx.render("/templates/admin/eventos.html", modelo);
    }

    private void eliminarEvento( Context ctx )
    {
        try
        {
            Long id = Long.parseLong(ctx.pathParam("id"));
            Usuario u = ctx.sessionAttribute("usuario");
            eventoServicio.eliminar(id, u);
            ctx.redirect("/admin/eventos?exito=Evento eliminado correctamente");
        } catch ( Exception e )
        {
            ctx.redirect("/admin/eventos?error=" + e.getMessage());
        }
    }

    //Para ser util en el software
    private Map<String, Object> baseModelo( Context ctx )
    {
        Map<String, Object> modelo = new HashMap<>();
        modelo.put("usuario", ctx.sessionAttribute("usuario"));
        modelo.put("rol", ctx.sessionAttribute("rol"));
        return modelo;
    }

}
