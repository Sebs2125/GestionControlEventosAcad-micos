package Servicio;

import Eventos.Configuracion.BaseDeDatosConfiguracion;
import Modelo.Evento;
import Modelo.InscripcionUsuario;
import Modelo.Usuario;
import org.hibernate.Session;
import org.hibernate.Transaction;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public class InscripcionServicio {

    public InscripcionUsuario inscribir(long eventoId, long participanteId)
    {
        Session session = BaseDeDatosConfiguracion.getSessionFactory().openSession();
        Transaction tx = null;

        try {
            tx = session.beginTransaction();

            Evento evento = session.get(Evento.class, eventoId);
            if (evento == null || evento.getEstado() != Evento.Estado.PUBLICADO) {
                throw new IllegalArgumentException("Evento no disponible");
            }

            if (!evento.tieneCuposDisponibles()) {
                throw new IllegalStateException("No hay cupos disponibles");
            }

            if (evento.getFechaHora().isBefore(LocalDateTime.now())) {
                throw new IllegalStateException("El evento ya pasó");
            }

            InscripcionUsuario existente = buscarPorEventoYParticipante(session, eventoId, participanteId);
            if (existente != null && existente.getEstado() == InscripcionUsuario.Estado.ACTIVA) {
                throw new IllegalStateException("Ya estás inscrito en este evento");
            }

            Usuario participante = session.get(Usuario.class, participanteId);
            if (participante == null) {
                throw new IllegalArgumentException("Usuario no encontrado");
            }

            String tokenQR = UUID.randomUUID().toString();

            InscripcionUsuario inscripcion;
            if (existente != null) {
                existente.setEstado(InscripcionUsuario.Estado.ACTIVA);
                existente.setFechaInscripcion(LocalDateTime.now());
                existente.setAsistio(false);
                existente.setTokenQR(tokenQR);
                session.update(existente);
                inscripcion = existente;
            } else {
                inscripcion = new InscripcionUsuario(evento, participante, tokenQR);
                session.save(inscripcion);
            }

            tx.commit();
            return inscripcion;

        } catch (Exception e) {
            if (tx != null) tx.rollback();
            throw new RuntimeException(e.getMessage());
        } finally {
            session.close();
        }
    }

    public void cancelarInscripcion(long inscripcionId, long participanteId)
    {
        Session session = BaseDeDatosConfiguracion.getSessionFactory().openSession();
        Transaction tx = null;

        try {
            tx = session.beginTransaction();

            InscripcionUsuario inscripcion = session.get(InscripcionUsuario.class, inscripcionId);

            if (inscripcion == null) {
                throw new IllegalArgumentException("Inscripción no encontrada");
            }

            if (inscripcion.getParticipante().getId() != participanteId) {
                throw new SecurityException("No puedes cancelar esta inscripción");
            }

            if (inscripcion.getEstado() != InscripcionUsuario.Estado.ACTIVA) {
                throw new IllegalStateException("La inscripción ya está cancelada");
            }

            if (inscripcion.getEvento().getFechaHora().isBefore(LocalDateTime.now())) {
                throw new IllegalStateException("No puedes cancelar inscripciones de eventos pasados");
            }

            inscripcion.cancelar();
            session.update(inscripcion);

            tx.commit();

        } catch (Exception e) {
            if (tx != null) tx.rollback();
            throw new RuntimeException(e.getMessage());
        } finally {
            session.close();
        }
    }

    public static InscripcionUsuario buscarPorEventoYParticipantePublico(long eventoId, long participanteId)
    {
        Session session = BaseDeDatosConfiguracion.getSessionFactory().openSession();
        try {
            return session.createQuery(
                            "FROM InscripcionUsuario i WHERE i.evento.id = :eventoId AND i.participante.id = :participanteId",
                            InscripcionUsuario.class)
                    .setParameter("eventoId", eventoId)
                    .setParameter("participanteId", participanteId)
                    .uniqueResult();
        } finally {
            session.close();
        }
    }

    private InscripcionUsuario buscarPorEventoYParticipante(Session session, long eventoId, long participanteId)
    {
        return session.createQuery(
                        "FROM InscripcionUsuario i WHERE i.evento.id = :eventoId AND i.participante.id = :participanteId",
                        InscripcionUsuario.class)
                .setParameter("eventoId", eventoId)
                .setParameter("participanteId", participanteId)
                .uniqueResult();
    }

    public InscripcionUsuario buscarPorId(long id)
    {
        Session session = BaseDeDatosConfiguracion.getSessionFactory().openSession();
        try {
            return session.get(InscripcionUsuario.class, id);
        } finally {
            session.close();
        }
    }

    // FIX 2 (Alta): marcarAsistencia ahora busca la inscripción dentro de la misma
    // sesión/transacción activa, evitando NonUniqueObjectException que ocurría cuando
    // buscarPorTokenQR abría una segunda sesión y devolvía una entidad adjunta a ella;
    // al hacer session.update() con la entidad de otra sesión Hibernate lanzaba la excepción.
    public void marcarAsistencia(String tokenQR)
    {
        Session session = BaseDeDatosConfiguracion.getSessionFactory().openSession();
        Transaction tx = null;

        try {
            tx = session.beginTransaction();

            // Buscar dentro de la sesión activa (no llamar a buscarPorTokenQR que abre otra)
            InscripcionUsuario inscripcion = session.createQuery(
                            "FROM InscripcionUsuario i WHERE i.tokenQR = :token",
                            InscripcionUsuario.class)
                    .setParameter("token", tokenQR)
                    .uniqueResult();

            if (inscripcion == null) {
                throw new IllegalArgumentException("QR no válido");
            }

            if (inscripcion.getEstado() != InscripcionUsuario.Estado.ACTIVA) {
                throw new IllegalStateException("Inscripción no activa");
            }

            if (inscripcion.isAsistio()) {
                throw new IllegalStateException("Asistencia ya registrada");
            }

            inscripcion.marcarAsistencia();
            session.update(inscripcion);

            tx.commit();

        } catch (Exception e) {
            if (tx != null) tx.rollback();
            throw new RuntimeException(e.getMessage());
        } finally {
            session.close();
        }
    }

    // Método auxiliar conservado para otros usos que no impliquen una tx activa
    public InscripcionUsuario buscarPorTokenQR(String token)
    {
        Session session = BaseDeDatosConfiguracion.getSessionFactory().openSession();
        try {
            return session.createQuery(
                            "FROM InscripcionUsuario i WHERE i.tokenQR = :token",
                            InscripcionUsuario.class)
                    .setParameter("token", token)
                    .uniqueResult();
        } finally {
            session.close();
        }
    }

    public List<InscripcionUsuario> listarPorEvento(long eventoId)
    {
        Session session = BaseDeDatosConfiguracion.getSessionFactory().openSession();
        try {
            return session.createQuery(
                            "FROM InscripcionUsuario i WHERE i.evento.id = :eventoId ORDER BY i.fechaInscripcion DESC",
                            InscripcionUsuario.class)
                    .setParameter("eventoId", eventoId)
                    .list();
        } finally {
            session.close();
        }
    }

    public List<InscripcionUsuario> listarPorParticipante(long participanteId)
    {
        Session session = BaseDeDatosConfiguracion.getSessionFactory().openSession();
        try {
            return session.createQuery(
                            "FROM InscripcionUsuario i WHERE i.participante.id = :participanteId ORDER BY i.fechaInscripcion DESC",
                            InscripcionUsuario.class)
                    .setParameter("participanteId", participanteId)
                    .list();
        } finally {
            session.close();
        }
    }
}