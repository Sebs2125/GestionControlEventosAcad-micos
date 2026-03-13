package Controlador;

import Modelo.Evento;
import Modelo.InscripcionUsuario;
import Modelo.Usuario;
import Servicio.InscripcionServicio;
import Servicio.EventoServicio;
import io.javalin.Javalin;
import io.javalin.http.Context;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

public class EventoControlador
{
    private final EventoServicio eventoServicio;
    private InscripcionServicio inscripcionServicio;
    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    public EventoControlador( EventoServicio eventoServicio, InscripcionServicio inscripcionServicio )
    {
        this.eventoServicio = eventoServicio;
        this.inscripcionServicio = inscripcionServicio;
    }

    public void registrarRutas(Javalin app)
    {
        app.get("/", ctx -> ctx.redirect("/eventos"));
        app.get("/eventos", this::listarLista);
        app.get("/eventos/grid", this::listarGrid);
        app.get("/eventos/{id}", this::detalle);

        app.get("/organizador/dashboard", this::dashboard);
        app.get("/organizador/eventos/nuevo", this::formularioNuevo);
        app.post("/organizador/eventos", this::crear);
        app.get("/organizador/eventos/{id}/editar", this::formularioEditar);
        app.post("/organizador/eventos/{id}/editar", this::editar);
        app.post("/organizador/eventos/{id}/publicar", this::publicar);
        app.post("/organizador/eventos/{id}/despublicar", this::despublicar);
        app.post("/organizador/eventos/{id}/cancelar", this::cancelar);
        app.get("/organizador/eventos/{id}/resumen", this::verResumen);
        app.get("/organizador/eventos/{id}/escaner", this::verEscaner);
    }

    private void listarLista(Context ctx)
    {
        Map<String, Object> modelo = baseModelo(ctx);
        modelo.put("eventos", eventoServicio.listarPublicados());
        modelo.put("vista", "lista");
        ctx.render("/eventos/lista.html", modelo);
    }

    private void listarGrid(Context ctx)
    {
        Map<String, Object> modelo = baseModelo(ctx);
        modelo.put("eventos", eventoServicio.listarPublicados());
        modelo.put("vista", "grid");
        ctx.render("/eventos/grid.html", modelo);
    }

    private void detalle(Context ctx)
    {
        Long id = Long.parseLong(ctx.pathParam("id"));
        Evento evento = eventoServicio.buscarPorId(id);

        if (evento == null || evento.getEstado() != Evento.Estado.PUBLICADO)
        {
            ctx.status(404).result("Evento no encontrado");
            return;
        }

        Map<String, Object> modelo = baseModelo(ctx);
        modelo.put("evento", evento);

        Usuario usuario = ctx.sessionAttribute("usuario");
        if (usuario != null && usuario.getRol() == Usuario.Rol.PARTICIPANTE)
        {
            InscripcionUsuario inscripcion = InscripcionServicio.buscarPorEventoYParticipantePublico(id, usuario.getId());
            modelo.put("inscripcion", inscripcion);
        }

        ctx.render("/eventos/detalle.html", modelo);
    }

    private void dashboard(Context ctx)
    {
        Usuario u = ctx.sessionAttribute("usuario");
        Map<String, Object> modelo = baseModelo(ctx);
        modelo.put("eventos", eventoServicio.listarPorOrganizador(u.getId()));
        modelo.put("exito", ctx.queryParam("exito"));
        modelo.put("error", ctx.queryParam("error"));
        ctx.render("/organizador/dashboard.html", modelo);
    }

    private void formularioNuevo(Context ctx)
    {
        Map<String, Object> modelo = baseModelo(ctx);
        modelo.put("editar", false);
        ctx.render("/eventos/formulario.html", modelo);
    }

    private void crear(Context ctx)
    {
        try
        {
            Usuario organizador = ctx.sessionAttribute("usuario");
            eventoServicio.crear(
                    ctx.formParam("titulo"),
                    ctx.formParam("descripcion"),
                    LocalDateTime.parse(ctx.formParam("fechaHora")),
                    ctx.formParam("lugar"),
                    Integer.parseInt(ctx.formParam("cupoMaximo")),
                    organizador
            );
            ctx.redirect("/organizador/dashboard?exito=Evento creado correctamente");
        } catch (Exception e)
        {
            Map<String, Object> modelo = baseModelo(ctx);
            modelo.put("error", e.getMessage());
            modelo.put("editar", false);
            ctx.render("/eventos/formulario.html", modelo);
        }
    }

    private void formularioEditar(Context ctx)
    {
        Long id = Long.parseLong(ctx.pathParam("id"));
        Evento evento = eventoServicio.buscarPorId(id);
        Usuario u = ctx.sessionAttribute("usuario");

        if (evento == null || !u.puedeGestionarEvento(evento))
        {
            ctx.status(403).result("No autorizado");
            return;
        }

        Map<String, Object> modelo = baseModelo(ctx);
        modelo.put("evento", evento);
        modelo.put("editar", true);
        modelo.put("fechaFormateada", evento.getFechaHora().format(formatter));
        ctx.render("/eventos/formulario.html", modelo);
    }

    private void editar(Context ctx)
    {
        try
        {
            Long id = Long.parseLong(ctx.pathParam("id"));
            Usuario u = ctx.sessionAttribute("usuario");
            eventoServicio.editar(
                    id,
                    ctx.formParam("titulo"),
                    ctx.formParam("descripcion"),
                    LocalDateTime.parse(ctx.formParam("fechaHora")),
                    ctx.formParam("lugar"),
                    Integer.parseInt(ctx.formParam("cupoMaximo")),
                    u
            );
            ctx.redirect("/organizador/dashboard?exito=Evento actualizado");
        } catch (Exception e)
        {
            ctx.redirect("/organizador/eventos/" + ctx.pathParam("id") + "/editar?error=" + e.getMessage());
        }
    }

    private void publicar(Context ctx)
    {
        try
        {
            Long id = Long.parseLong(ctx.pathParam("id"));
            Usuario u = ctx.sessionAttribute("usuario");
            eventoServicio.publicar(id, u);
            ctx.redirect("/organizador/dashboard?exito=Evento publicado");
        } catch (Exception e)
        {
            ctx.redirect("/organizador/dashboard?error=" + e.getMessage());
        }
    }

    private void despublicar(Context ctx)
    {
        try
        {
            Long id = Long.parseLong(ctx.pathParam("id"));
            Usuario u = ctx.sessionAttribute("usuario");
            eventoServicio.despublicar(id, u);
            ctx.redirect("/organizador/dashboard?exito=Evento despublicado");
        } catch (Exception e)
        {
            ctx.redirect("/organizador/dashboard?error=" + e.getMessage());
        }
    }

    private void cancelar(Context ctx)
    {
        try
        {
            Long id = Long.parseLong(ctx.pathParam("id"));
            Usuario u = ctx.sessionAttribute("usuario");
            eventoServicio.cancelar(id, u);
            ctx.redirect("/organizador/dashboard?exito=Evento cancelado");
        } catch (Exception e)
        {
            ctx.redirect("/organizador/dashboard?error=" + e.getMessage());
        }
    }

    private void verResumen(Context ctx)
    {
        Long id = Long.parseLong(ctx.pathParam("id"));
        Evento evento = eventoServicio.buscarPorId(id);
        Usuario usuario = ctx.sessionAttribute("usuario");

        if (evento == null || !usuario.puedeGestionarEvento(evento))
        {
            ctx.status(403).result("No autorizado");
            return;
        }

        Map<String, Object> modelo = baseModelo(ctx);
        modelo.put("evento", evento);
        ctx.render("/eventos/resumen.html", modelo);
    }

    private void verEscaner(Context ctx)
    {
        Long id = Long.parseLong(ctx.pathParam("id"));
        Evento evento = eventoServicio.buscarPorId(id);
        Usuario usuario = ctx.sessionAttribute("usuario");

        if (evento == null || !usuario.puedeGestionarEvento(evento))
        {
            ctx.status(403).result("No autorizado");
            return;
        }

        Map<String, Object> modelo = baseModelo(ctx);
        modelo.put("evento", evento);
        ctx.render("/organizador/escaner.html", modelo);
    }

    private Map<String, Object> baseModelo(Context ctx)
    {
        Map<String, Object> modelo = new HashMap<>();
        modelo.put("usuario", ctx.sessionAttribute("usuario"));
        modelo.put("rol", ctx.sessionAttribute("rol"));
        return modelo;
    }
}