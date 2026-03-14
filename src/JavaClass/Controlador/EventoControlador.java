package Controlador;

import Modelo.Evento;
import Modelo.InscripcionUsuario;
import Modelo.Usuario;
import Servicio.InscripcionServicio;
import Servicio.EventoServicio;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.UploadedFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class EventoControlador
{
    private final EventoServicio eventoServicio;
    private final InscripcionServicio inscripcionServicio;
    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");

    // Usar directorio del sistema del usuario — siempre existe y tiene permisos
    private static final String UPLOAD_DIR = System.getProperty("user.home") + File.separator + "evento_uploads";

    public EventoControlador(EventoServicio eventoServicio, InscripcionServicio inscripcionServicio) {
        this.eventoServicio      = eventoServicio;
        this.inscripcionServicio = inscripcionServicio;
        // Crear carpeta si no existe
        File dir = new File(UPLOAD_DIR);
        if (!dir.exists()) {
            dir.mkdirs();
            System.out.println("Carpeta de imágenes creada en: " + dir.getAbsolutePath());
        }
    }

    public void registrarRutas(Javalin app) {
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

        // Ruta para servir imágenes — lee del directorio del sistema
        app.get("/uploads/{filename}", this::servirImagen);
    }

    private void servirImagen(Context ctx) {
        // Sanitizar para evitar path traversal
        String filename = Paths.get(ctx.pathParam("filename")).getFileName().toString();
        File file = new File(UPLOAD_DIR, filename);

        if (!file.exists() || !file.isFile()) {
            ctx.status(404);
            return;
        }
        try {
            String contentType = Files.probeContentType(file.toPath());
            if (contentType == null) contentType = "image/jpeg";
            ctx.contentType(contentType);
            ctx.result(new FileInputStream(file));
        } catch (Exception e) {
            ctx.status(500);
        }
    }

    private String guardarImagen(Context ctx) {
        try {
            UploadedFile archivo = ctx.uploadedFile("imagen");

            // Verificar que se subió algo
            if (archivo == null) {
                System.out.println("No se recibió archivo imagen");
                return null;
            }

            String originalName = archivo.filename();
            System.out.println("Archivo recibido: " + originalName + " | Content-Type: " + archivo.contentType());

            // Verificar que tiene nombre (no está vacío)
            if (originalName == null || originalName.trim().isEmpty()) {
                System.out.println("Nombre de archivo vacío");
                return null;
            }

            // Extensión
            String extension = "";
            int dot = originalName.lastIndexOf('.');
            if (dot >= 0) extension = originalName.substring(dot).toLowerCase();

            // Validar tipo
            if (!extension.matches("\\.(jpg|jpeg|png|gif|webp)")) {
                System.out.println("Extensión no permitida: " + extension);
                return null;
            }

            String nuevoNombre = UUID.randomUUID().toString() + extension;
            File destino = new File(UPLOAD_DIR, nuevoNombre);

            // Leer y escribir el archivo
            try (InputStream in = archivo.content();
                 FileOutputStream out = new FileOutputStream(destino)) {

                byte[] buf = new byte[4096];
                int bytesLeidos;
                long totalBytes = 0;
                while ((bytesLeidos = in.read(buf)) != -1) {
                    out.write(buf, 0, bytesLeidos);
                    totalBytes += bytesLeidos;
                }
                System.out.println("Imagen guardada: " + destino.getAbsolutePath() + " (" + totalBytes + " bytes)");

                if (totalBytes == 0) {
                    destino.delete();
                    System.out.println("Archivo vacío, eliminado");
                    return null;
                }
            }

            return "/uploads/" + nuevoNombre;

        } catch (Exception e) {
            System.err.println("Error guardando imagen: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    private void listarLista(Context ctx) {
        Map<String, Object> modelo = baseModelo(ctx);
        modelo.put("eventos", eventoServicio.listarPublicados());
        ctx.render("/eventos/lista.html", modelo);
    }

    private void listarGrid(Context ctx) {
        Map<String, Object> modelo = baseModelo(ctx);
        modelo.put("eventos", eventoServicio.listarPublicados());
        ctx.render("/eventos/grid.html", modelo);
    }

    private void detalle(Context ctx) {
        Long id = Long.parseLong(ctx.pathParam("id"));
        Evento evento = eventoServicio.buscarPorId(id);
        if (evento == null || evento.getEstado() != Evento.Estado.PUBLICADO) {
            ctx.status(404).result("Evento no encontrado");
            return;
        }
        Map<String, Object> modelo = baseModelo(ctx);
        modelo.put("evento", evento);
        Usuario usuario = ctx.sessionAttribute("usuario");
        if (usuario != null && usuario.getRol() == Usuario.Rol.PARTICIPANTE) {
            InscripcionUsuario inscripcion =
                    InscripcionServicio.buscarPorEventoYParticipantePublico(id, usuario.getId());
            modelo.put("inscripcion", inscripcion);
        }
        ctx.render("/eventos/detalle.html", modelo);
    }

    private void dashboard(Context ctx) {
        Usuario u = ctx.sessionAttribute("usuario");
        Map<String, Object> modelo = baseModelo(ctx);
        modelo.put("eventos", eventoServicio.listarPorOrganizador(u.getId()));
        modelo.put("exito", ctx.queryParam("exito"));
        modelo.put("error", ctx.queryParam("error"));
        ctx.render("/organizador/dashboard.html", modelo);
    }

    private void formularioNuevo(Context ctx) {
        Map<String, Object> modelo = baseModelo(ctx);
        modelo.put("editar", false);
        ctx.render("/eventos/formulario.html", modelo);
    }

    private void crear(Context ctx) {
        try {
            Usuario organizador = ctx.sessionAttribute("usuario");
            String imagenUrl = guardarImagen(ctx);
            eventoServicio.crear(
                    ctx.formParam("titulo"),
                    ctx.formParam("descripcion"),
                    LocalDateTime.parse(ctx.formParam("fechaHora")),
                    ctx.formParam("lugar"),
                    Integer.parseInt(ctx.formParam("cupoMaximo")),
                    organizador,
                    imagenUrl
            );
            ctx.redirect("/organizador/dashboard?exito=Evento creado correctamente");
        } catch (Exception e) {
            Map<String, Object> modelo = baseModelo(ctx);
            modelo.put("error", e.getMessage());
            modelo.put("editar", false);
            ctx.render("/eventos/formulario.html", modelo);
        }
    }

    private void formularioEditar(Context ctx) {
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
        // Formato compatible con datetime-local input
        modelo.put("fechaFormateada", evento.getFechaHora().format(formatter));
        ctx.render("/eventos/formulario.html", modelo);
    }

    private void editar(Context ctx) {
        try {
            Long id = Long.parseLong(ctx.pathParam("id"));
            Usuario u = ctx.sessionAttribute("usuario");
            String imagenUrl = guardarImagen(ctx);
            eventoServicio.editar(
                    id,
                    ctx.formParam("titulo"),
                    ctx.formParam("descripcion"),
                    LocalDateTime.parse(ctx.formParam("fechaHora")),
                    ctx.formParam("lugar"),
                    Integer.parseInt(ctx.formParam("cupoMaximo")),
                    u,
                    imagenUrl
            );
            ctx.redirect("/organizador/dashboard?exito=Evento actualizado");
        } catch (Exception e) {
            ctx.redirect("/organizador/eventos/" + ctx.pathParam("id") + "/editar?error=" + e.getMessage());
        }
    }

    private void publicar(Context ctx) {
        try {
            eventoServicio.publicar(Long.parseLong(ctx.pathParam("id")), ctx.sessionAttribute("usuario"));
            ctx.redirect("/organizador/dashboard?exito=Evento publicado");
        } catch (Exception e) {
            ctx.redirect("/organizador/dashboard?error=" + e.getMessage());
        }
    }

    private void despublicar(Context ctx) {
        try {
            eventoServicio.despublicar(Long.parseLong(ctx.pathParam("id")), ctx.sessionAttribute("usuario"));
            ctx.redirect("/organizador/dashboard?exito=Evento despublicado");
        } catch (Exception e) {
            ctx.redirect("/organizador/dashboard?error=" + e.getMessage());
        }
    }

    private void cancelar(Context ctx) {
        try {
            eventoServicio.cancelar(Long.parseLong(ctx.pathParam("id")), ctx.sessionAttribute("usuario"));
            ctx.redirect("/organizador/dashboard?exito=Evento cancelado");
        } catch (Exception e) {
            ctx.redirect("/organizador/dashboard?error=" + e.getMessage());
        }
    }

    private void verResumen(Context ctx) {
        Long id = Long.parseLong(ctx.pathParam("id"));
        Evento evento = eventoServicio.buscarPorId(id);
        Usuario usuario = ctx.sessionAttribute("usuario");
        if (evento == null || !usuario.puedeGestionarEvento(evento)) {
            ctx.status(403).result("No autorizado");
            return;
        }
        Map<String, Object> modelo = baseModelo(ctx);
        modelo.put("evento", evento);
        ctx.render("/eventos/resumen.html", modelo);
    }

    private void verEscaner(Context ctx) {
        Long id = Long.parseLong(ctx.pathParam("id"));
        Evento evento = eventoServicio.buscarPorId(id);
        Usuario usuario = ctx.sessionAttribute("usuario");
        if (evento == null || !usuario.puedeGestionarEvento(evento)) {
            ctx.status(403).result("No autorizado");
            return;
        }
        Map<String, Object> modelo = baseModelo(ctx);
        modelo.put("evento", evento);
        ctx.render("/organizador/escaner.html", modelo);
    }

    private Map<String, Object> baseModelo(Context ctx) {
        Map<String, Object> modelo = new HashMap<>();
        modelo.put("usuario", ctx.sessionAttribute("usuario"));
        modelo.put("rol", ctx.sessionAttribute("rol"));
        return modelo;
    }
}