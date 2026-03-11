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

    public EventoControlador( EventoServicio eventoServicio ,InscripcionServicio inscripcionServicio )
    {
        this.eventoServicio = eventoServicio;
    }

    public void registrarRutas(Javalin app )
    {
        //Vista pública de eventos
        app.get("/", ctx -> ctx.redirect("/eventos"));
        app.get("/eventos", this::listarLista);
        app.get("/eventos/grid", this::listarGrid);
        app.get("/eventos/{id}", this::detalle);

        //Organizador - CRUD
        app.get("/organizador/dashboard", this::dashboard);
        app.get("/organizador/eventos/nuevo", this::formularioNuevo);
        app.post("/organizador/eventos", this::crear);
        app.get("/organizador/eventos/{id}/editar", this::formularioEditar);
        app.post("/organizador/eventos/{id}/editar", this::editar);
        app.post("/organizador/eventos/{id}/publicar", this::publicar);
        app.post("/organizador/eventos/{id}/despublicar", this::despublicar);
        app.post("/organizador/eventos/{id}/cancelar", this::cancelar);

        //Admin
        app.get("/admin/dashboard", this::adminDashboard);
        app.get("/admin/eventos", this::adminEventos);
        app.post("/admin/eventos/{id}/eliminar", this::eliminar);

    }

    //Vistas públicas:

    private void listarLista(Context ctx)
    {
        Map<String, Object> modelo = baseModelo(ctx);
        modelo.put("eventos", eventoServicio.listarPublicados());
        modelo.put("vista", "lista");
        ctx.render("/templates/eventos/lista.html", modelo);
    }

    private void listarGrid(Context ctx)
    {
        Map<String, Object> modelo = baseModelo(ctx);
        modelo.put("eventos", eventoServicio.listarPublicados());
        modelo.put("vista", "grid");
        ctx.render("/templates/eventos/grid.html", modelo);
    }

    private void detalle(Context ctx)
    {

        Long id = Long.parseLong(ctx.pathParam("id"));
        Evento evento = eventoServicio.buscarPorId(id);

        if (evento == null || evento.getEstado() != Evento.Estado.PUBLICADO)
        {
            ctx.status(404).render("/templates/error/404.html");
            return;
        }

        Map<String, Object> modelo = baseModelo(ctx);
        modelo.put("evento", evento);

        Usuario usuario = ctx.sessionAttribute("usuario");
        if (usuario != null && usuario.getRol() == Usuario.Rol.PARTICIPANTE) {
            InscripcionUsuario inscripcion = InscripcionServicio.buscarPorEventoYParticipante(id, usuario.getId());
            modelo.put("inscripcion", inscripcion);
        }

        ctx.render("/templates/eventos/detalle.html", modelo);



    }

    //Organizador
    private void dashboard(Context ctx)
    {
        Usuario u = ctx.sessionAttribute("usuario");

        Map<String, Object> modelo = baseModelo(ctx);
        modelo.put("eventos", eventoServicio.listarPorOrganizador(u.getId()));
        modelo.put("exito", ctx.queryParam("exito"));
        modelo.put("error", ctx.queryParam("error"));

        ctx.render("/templates/organizador/dashboard.html", modelo);
    }

    private void formularioNuevo(Context ctx)
    {
        Map<String, Object> modelo = baseModelo(ctx);
        modelo.put("editar", false);
        ctx.render("/templates/eventos/formulario.html", modelo);
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
            modelo.put("datos", ctx.formParamMap());
            ctx.render("/templates/eventos/formulario.html", modelo);
        }
    }

    private void formularioEditar(Context ctx)
    {
        Long id = Long.parseLong(ctx.pathParam("id"));
        Evento evento = eventoServicio.buscarPorId(id);
        Usuario u = ctx.sessionAttribute("usuario");

        if (evento == null || !u.puedeGestionarEvento(evento)) {
            ctx.status(403).result("No autorizado");
            return;
        }

        Map<String, Object> modelo = baseModelo(ctx);
        modelo.put("evento", evento);
        modelo.put("editar", true);
        modelo.put("fechaFormateada", evento.getFechaHora().format(formatter));

        ctx.render("/templates/eventos/formulario.html", modelo);
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

        } catch (Exception e) {
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
        } catch (Exception e) {
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
        } catch (Exception e) {
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
        } catch (Exception e) {
            ctx.redirect("/organizador/dashboard?error=" + e.getMessage());
        }
    }

    //Admin
    private void adminDashboard(Context ctx)
    {
        Map<String, Object> modelo = baseModelo(ctx);
        modelo.put("totalUsuarios", 0); // Llenar con datos reales
        modelo.put("totalEventos", eventoServicio.listarTodos().size());
        ctx.render("/templates/admin/dashboard.html", modelo);
    }

    private void adminEventos(Context ctx)
    {
        Map<String, Object> modelo = baseModelo(ctx);
        modelo.put("eventos", eventoServicio.listarTodos());
        ctx.render("/templates/admin/eventos.html", modelo);
    }

    private void eliminar(Context ctx)
    {
        try
        {
            Long id = Long.parseLong(ctx.pathParam("id"));
            Usuario u = ctx.sessionAttribute("usuario");
            eventoServicio.eliminar(id, u);
            ctx.redirect("/admin/eventos?exito=Evento eliminado");
        } catch (Exception e) {
            ctx.redirect("/admin/eventos?error=" + e.getMessage());
        }
    }

    //Utilidad
    private Map<String, Object> baseModelo(Context ctx)
    {
        Map<String, Object> modelo = new HashMap<>();
        modelo.put("usuario", ctx.sessionAttribute("usuario"));
        modelo.put("rol", ctx.sessionAttribute("rol"));
        return modelo;
    }



}