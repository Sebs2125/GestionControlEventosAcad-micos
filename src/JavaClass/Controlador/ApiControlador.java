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
import java.util.Map;

public class ApiControlador
{
    private final InscripcionServicio inscripcionServicio;
    private final EventoServicio eventoServicio;
    private final EstadisticaServicio estadisticaServicio;

    public ApiControlador(InscripcionServicio inscripcionServicio, EventoServicio eventoServicio, EstadisticaServicio estadisticaServicio ) {
        this.inscripcionServicio = inscripcionServicio;
        this.eventoServicio = eventoServicio;
        this.estadisticaServicio = new EstadisticaServicio();

    }

    public void registrarRutas(Javalin app)
    {

        // Endpoint público para consultar cupos disponibles (Punto 5)
        app.get("/api/eventos/{id}/estadisticas", this::obtenerEstadisticas );
        app.get("/api/eventos/{id}/cupos", ctx -> {
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
        });

        // Todas las rutas API requieren autenticación
        app.before("/api/*", this::requerirAutenticacion);

        // Inscripciones
        app.post("/api/inscripciones", this::crearInscripcion);
        app.post("/api/inscripciones/{id}/cancelar", this::cancelarInscripcion);

        // Validación QR (para escáner de asistencia)
        app.post("/api/asistencia/validar", this::validarAsistencia);
    }

    private void requerirAutenticacion(Context ctx) {
        if (ctx.sessionAttribute("usuario") == null) {
            ctx.status(401).json(Map.of("error", "No autenticado"));
        }
    }

    private void crearInscripcion(Context ctx) {
        try {
            Usuario usuario = ctx.sessionAttribute("usuario");

            // Solo participantes pueden inscribirse
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

    private void cancelarInscripcion(Context ctx) {
        try {
            Usuario usuario = ctx.sessionAttribute("usuario");
            Long inscripcionId = Long.parseLong(ctx.pathParam("id"));

            inscripcionServicio.cancelarInscripcion(inscripcionId, usuario.getId());

            ctx.json(Map.of("success", true, "message", "Inscripción cancelada correctamente"));

        } catch (SecurityException e) {
            ctx.status(403).json(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException | IllegalStateException e) {
            ctx.status(400).json(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            ctx.status(500).json(Map.of("error", "Error interno del servidor"));
        }
    }

    private void validarAsistencia(Context ctx) {
        try {
            Map<String, Object> body = ctx.bodyAsClass(Map.class);
            String tokenQR = (String) body.get("token");

            inscripcionServicio.marcarAsistencia(tokenQR);

            ctx.json(Map.of(
                    "success", true,
                    "message", "Asistencia registrada correctamente"
            ));

        } catch (IllegalArgumentException | IllegalStateException e) {
            ctx.status(400).json(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            ctx.status(500).json(Map.of("error", "Error interno del servidor"));
        }
    }

    private void obtenerEstadisticas(Context ctx)
    {
        try {
            Usuario usuario = ctx.sessionAttribute("usuario");
            Long eventoId = Long.parseLong(ctx.pathParam("id"));

            Evento evento = eventoServicio.buscarPorId(eventoId);

            if ( evento == null )
            {
                ctx.status(404).json(Map.of("error", "No evento encontrado"));
                return;
            }

            if ( !usuario.puedeGestionarEvento(evento))
            {
                ctx.status(404).json(Map.of("error", "No autorizado"));
                return;
            }

            Map<String, Object> stats = estadisticaServicio.obtenerEstadisticasCompletas(eventoId);
            ctx.json(stats);

        } catch ( Exception e )
        {
            ctx.status(500).json(Map.of("error", e.getMessage()));
        }
    }

}