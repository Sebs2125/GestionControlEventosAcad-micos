package Controlador;

import Modelo.Evento;
import Modelo.InscripcionUsuario;
import Modelo.Usuario;
import Servicio.EstadisticaServicio;
import Servicio.EventoServicio;
import Servicio.InscripcionServicio;
import io.javalin.Javalin;
import io.javalin.http.Context;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ApiControlador
{
    private final InscripcionServicio inscripcionServicio;
    private final EventoServicio eventoServicio;
    private final EstadisticaServicio estadisticaServicio;

    public ApiControlador(InscripcionServicio inscripcionServicio,
                          EventoServicio eventoServicio,
                          EstadisticaServicio estadisticaServicio)
    {
        this.inscripcionServicio = inscripcionServicio;
        this.eventoServicio = eventoServicio;
        this.estadisticaServicio = estadisticaServicio;
    }

    public void registrarRutas(Javalin app)
    {
        app.get("/api/eventos/{id}/cupos", this::obtenerCupos);


        app.before("/api/*", ctx -> {
            String path = ctx.path();
            // Excluir rutas públicas
            if (path.equals("/api/asistencia/validar") ||
                    path.contains("/cupos")) {
                return;
            }
            if (ctx.sessionAttribute("usuario") == null) {
                throw new io.javalin.http.UnauthorizedResponse("No autenticado");
            }
        });

        // Devolver la excepción como JSON para que los clientes REST reciban JSON 401
        app.exception(io.javalin.http.UnauthorizedResponse.class, (e, ctx) ->
                ctx.status(401).json(Map.of("error", e.getMessage()))
        );

        // Inscripciones
        app.post("/api/inscripciones", this::crearInscripcion);
        app.post("/api/inscripciones/{id}/cancelar", this::cancelarInscripcion);

        // Validacion QR
        app.post("/api/asistencia/validar", this::validarAsistencia);

        // Estadisticas (requiere auth)
        app.get("/api/eventos/{id}/estadisticas", this::obtenerEstadisticas);

        // Lista inscritos (requiere auth)
        app.get("/api/eventos/{id}/inscritos", this::listarInscritos);
    }

    private void obtenerCupos(Context ctx)
    {
        Long eventoId = Long.parseLong(ctx.pathParam("id"));
        Evento evento = eventoServicio.buscarPorId(eventoId);

        if (evento == null) {
            ctx.status(404).json(Map.of("error", "Evento no encontrado"));
            return;
        }

        ctx.json(Map.of(
                "eventoId", eventoId,
                "cupoMaximo", evento.getCupoMaximo(),
                "inscritos", evento.getTotalInscritos(),
                "cuposDisponibles", evento.getCuposDisponibles(),
                "tieneCupos", evento.tieneCuposDisponibles()
        ));
    }

    private void crearInscripcion(Context ctx)
    {
        try {
            Usuario usuario = ctx.sessionAttribute("usuario");
            if (usuario == null) {
                ctx.status(401).json(Map.of("error", "No autenticado"));
                return;
            }

            if (usuario.getRol() != Usuario.Rol.PARTICIPANTE) {
                ctx.status(403).json(Map.of("error", "Solo los participantes pueden inscribirse"));
                return;
            }

            Map<String, Object> body = ctx.bodyAsClass(Map.class);
            Long eventoId = ((Number) body.get("eventoId")).longValue();

            InscripcionUsuario inscripcion = inscripcionServicio.inscribir(eventoId, usuario.getId());

            Map<String, Object> respuesta = new HashMap<>();
            respuesta.put("success", true);
            respuesta.put("id", inscripcion.getId());
            respuesta.put("tokenQR", inscripcion.getTokenQR());
            respuesta.put("fechaInscripcion", inscripcion.getFechaInscripcion().toString());

            ctx.status(201).json(respuesta);

        } catch (IllegalArgumentException | IllegalStateException e) {
            ctx.status(400).json(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            ctx.status(500).json(Map.of("error", "Error interno del servidor"));
        }
    }

    private void cancelarInscripcion(Context ctx)
    {
        try {
            Usuario usuario = ctx.sessionAttribute("usuario");
            if (usuario == null) {
                ctx.status(401).json(Map.of("error", "No autenticado"));
                return;
            }

            Long inscripcionId = Long.parseLong(ctx.pathParam("id"));
            inscripcionServicio.cancelarInscripcion(inscripcionId, usuario.getId());
            ctx.json(Map.of("success", true, "message", "Inscripcion cancelada"));

        } catch (SecurityException e) {
            ctx.status(403).json(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException | IllegalStateException e) {
            ctx.status(400).json(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            ctx.status(500).json(Map.of("error", "Error interno"));
        }
    }

    private void validarAsistencia(Context ctx)
    {
        try {
            Map<String, Object> body = ctx.bodyAsClass(Map.class);
            String tokenQR = (String) body.get("token");
            inscripcionServicio.marcarAsistencia(tokenQR);
            ctx.json(Map.of("success", true, "message", "Asistencia registrada"));
        } catch (IllegalArgumentException | IllegalStateException e) {
            ctx.status(400).json(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            ctx.status(500).json(Map.of("error", "Error interno"));
        }
    }

    private void obtenerEstadisticas(Context ctx)
    {
        try {
            Usuario usuario = ctx.sessionAttribute("usuario");
            if (usuario == null) {
                ctx.status(401).json(Map.of("error", "No autenticado"));
                return;
            }

            Long eventoId = Long.parseLong(ctx.pathParam("id"));
            Evento evento = eventoServicio.buscarPorId(eventoId);

            if (evento == null) {
                ctx.status(404).json(Map.of("error", "Evento no encontrado"));
                return;
            }

            if (!usuario.puedeGestionarEvento(evento)) {
                ctx.status(403).json(Map.of("error", "No autorizado"));
                return;
            }

            Map<String, Object> stats = estadisticaServicio.obtenerEstadisticasCompletas(eventoId);
            ctx.json(stats);

        } catch (Exception e) {
            ctx.status(500).json(Map.of("error", e.getMessage()));
        }
    }

    private void listarInscritos(Context ctx)
    {
        try {
            Usuario usuario = ctx.sessionAttribute("usuario");
            if (usuario == null) {
                ctx.status(401).json(Map.of("error", "No autenticado"));
                return;
            }

            Long eventoId = Long.parseLong(ctx.pathParam("id"));
            Evento evento = eventoServicio.buscarPorId(eventoId);

            if (evento == null) {
                ctx.status(404).json(Map.of("error", "Evento no encontrado"));
                return;
            }

            if (!usuario.puedeGestionarEvento(evento)) {
                ctx.status(403).json(Map.of("error", "No autorizado"));
                return;
            }

            List<InscripcionUsuario> inscripciones = inscripcionServicio.listarPorEvento(eventoId);

            List<Map<String, Object>> resultado = inscripciones.stream()
                    .map(i -> {
                        Map<String, Object> dato = new HashMap<>();
                        dato.put("nombre", i.getParticipante().getNombre());
                        dato.put("email", i.getParticipante().getEmail());
                        dato.put("fechaInscripcion", i.getFechaInscripcion().toString());
                        dato.put("estado", i.getEstado().toString());
                        dato.put("asistio", i.isAsistio());
                        dato.put("fechaAsistencia", i.isAsistio() && i.getFechaAsistencia() != null
                                ? i.getFechaAsistencia().toString() : null);
                        return dato;
                    })
                    .collect(Collectors.toList());

            ctx.json(resultado);

        } catch (Exception e) {
            ctx.status(500).json(Map.of("error", e.getMessage()));
        }
    }
}