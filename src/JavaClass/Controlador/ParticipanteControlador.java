package Controlador;

import Modelo.InscripcionUsuario;
import Modelo.Usuario;
import Servicio.InscripcionServicio;
import Servicio.UsuarioServicio;
import io.javalin.Javalin;
import io.javalin.http.Context;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ParticipanteControlador
{
    private final UsuarioServicio usuarioServicio;
    private final InscripcionServicio inscripcionServicio;

    public ParticipanteControlador(UsuarioServicio usuarioServicio, InscripcionServicio inscripcionServicio)
    {
        this.usuarioServicio = usuarioServicio;
        this.inscripcionServicio = inscripcionServicio;
    }

    public void registrarRutas(Javalin app)
    {
        // Proteger todas las rutas de participante
        app.before("/participante/*", ctx -> {
            Usuario u = ctx.sessionAttribute("usuario");
            if (u == null) {
                ctx.redirect("/login");
                return;
            }
            if (u.getRol() != Usuario.Rol.PARTICIPANTE) {
                ctx.status(403).result("Solo participantes pueden acceder a esta sección");
            }
        });

        app.get("/participante/mis-inscripciones", this::misInscripciones);
        app.get("/participante/perfil", this::verPerfil);
        app.post("/participante/perfil", this::guardarPerfil);
    }

    private void misInscripciones(Context ctx)
    {
        Usuario usuario = ctx.sessionAttribute("usuario");

        List<InscripcionUsuario> todas = inscripcionServicio.listarPorParticipante(usuario.getId());

        List<InscripcionUsuario> activas = todas.stream()
                .filter(i -> i.getEstado() == InscripcionUsuario.Estado.ACTIVA)
                .collect(Collectors.toList());

        List<InscripcionUsuario> canceladas = todas.stream()
                .filter(i -> i.getEstado() == InscripcionUsuario.Estado.CANCELADA)
                .collect(Collectors.toList());

        Map<String, Object> modelo = baseModelo(ctx);
        modelo.put("inscripcionesActivas", activas);
        modelo.put("inscripcionesCanceladas", canceladas);
        modelo.put("totalActivas", activas.size());
        modelo.put("totalCanceladas", canceladas.size());
        modelo.put("exito", ctx.queryParam("exito"));
        modelo.put("error", ctx.queryParam("error"));

        ctx.render("/participante/mis-inscripciones.html", modelo);
    }

    private void verPerfil(Context ctx)
    {
        Map<String, Object> modelo = baseModelo(ctx);
        modelo.put("exito", ctx.queryParam("exito"));
        modelo.put("error", ctx.queryParam("error"));
        ctx.render("/participante/perfil.html", modelo);
    }

    private void guardarPerfil(Context ctx)
    {
        Usuario usuario = ctx.sessionAttribute("usuario");
        String seccion  = ctx.formParam("seccion");

        try {
            if ("datos".equals(seccion)) {
                // Actualizar nombre y email
                String nombre = ctx.formParam("nombre");
                String email  = ctx.formParam("email");

                usuarioServicio.editarPerfil(usuario.getId(), nombre, email);

                // Actualizar sesión con los nuevos datos
                Usuario actualizado = usuarioServicio.buscarPorId(usuario.getId());
                ctx.sessionAttribute("usuario", actualizado);

                ctx.redirect("/participante/perfil?exito=Datos actualizados correctamente");

            } else if ("password".equals(seccion)) {
                // Cambiar contraseña
                String actual   = ctx.formParam("passwordActual");
                String nueva    = ctx.formParam("passwordNueva");

                usuarioServicio.cambiarPassword(usuario.getId(), actual, nueva);

                ctx.redirect("/participante/perfil?exito=Contraseña cambiada correctamente");
            }

        } catch (Exception e) {
            ctx.redirect("/participante/perfil?error=" + e.getMessage());
        }
    }

    private Map<String, Object> baseModelo(Context ctx)
    {
        Map<String, Object> modelo = new HashMap<>();
        modelo.put("usuario", ctx.sessionAttribute("usuario"));
        modelo.put("rol", ctx.sessionAttribute("rol"));
        return modelo;
    }
}