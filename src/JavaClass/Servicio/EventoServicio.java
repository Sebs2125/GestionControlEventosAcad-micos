package Servicio;

import Eventos.Configuracion.BaseDeDatosConfiguracion;
import Modelo.Evento;
import Modelo.Usuario;
import org.hibernate.Session;
import org.hibernate.Transaction;

import java.time.LocalDateTime;
import java.util.List;

public class EventoServicio
{
    public Evento crear(String titulo, String descripcion, LocalDateTime fechaHora,
                        String lugar, Integer cupoMaximo, Usuario organizador, String imagenUrl)
    {
        validarEvento(titulo, fechaHora, lugar, cupoMaximo);
        Session session = BaseDeDatosConfiguracion.getSessionFactory().openSession();
        Transaction tx = null;
        try {
            tx = session.beginTransaction();
            Evento evento = new Evento(titulo, descripcion, fechaHora, lugar, cupoMaximo, organizador);
            evento.setImagenUrl(imagenUrl);
            session.save(evento);
            tx.commit();
            return evento;
        } catch (Exception e) {
            if (tx != null) tx.rollback();
            throw e;
        } finally {
            session.close();
        }
    }

    public Evento crear(String titulo, String descripcion, LocalDateTime fechaHora,
                        String lugar, Integer cupoMaximo, Usuario organizador)
    {
        return crear(titulo, descripcion, fechaHora, lugar, cupoMaximo, organizador, null);
    }

    public Evento editar(Long id, String titulo, String descripcion, LocalDateTime fechaHora,
                         String lugar, Integer cupoMaximo, Usuario solicitante, String imagenUrl)
    {
        Session session = BaseDeDatosConfiguracion.getSessionFactory().openSession();
        Transaction tx = null;
        try {
            tx = session.beginTransaction();
            Evento evento = session.get(Evento.class, id);

            if (evento == null) throw new IllegalArgumentException("No existe el evento con id: " + id);
            if (!solicitante.puedeGestionarEvento(evento))
                throw new SecurityException("No tiene permisos para editar este evento");
            if (evento.getFechaHora().isBefore(LocalDateTime.now()))
                throw new IllegalStateException("No se puede editar un evento pasado");

            validarEvento(titulo, fechaHora, lugar, cupoMaximo);

            // Inicializar para poder llamar getTotalInscritos()
            org.hibernate.Hibernate.initialize(evento.getInscripciones());
            long inscritos = evento.getTotalInscritos();
            if (cupoMaximo < inscritos)
                throw new IllegalArgumentException("No puede reducir el cupo por debajo de " + inscritos + " inscritos");

            evento.setTitulo(titulo);
            evento.setDescripcion(descripcion);
            evento.setFechaHora(fechaHora);
            evento.setLugar(lugar);
            evento.setCupoMaximo(cupoMaximo);
            if (imagenUrl != null && !imagenUrl.isEmpty()) {
                evento.setImagenUrl(imagenUrl);
            }

            session.update(evento);
            tx.commit();
            return evento;
        } catch (Exception e) {
            if (tx != null) tx.rollback();
            throw e;
        } finally {
            session.close();
        }
    }

    public Evento editar(Long id, String titulo, String descripcion, LocalDateTime fechaHora,
                         String lugar, Integer cupoMaximo, Usuario solicitante)
    {
        return editar(id, titulo, descripcion, fechaHora, lugar, cupoMaximo, solicitante, null);
    }

    public void cancelar(Long id, Usuario solicitante) {
        cambiarEstado(id, Evento.Estado.CANCELADO, solicitante);
    }

    public void publicar(Long id, Usuario solicitante) {
        Session session = BaseDeDatosConfiguracion.getSessionFactory().openSession();
        Transaction tx = null;
        try {
            tx = session.beginTransaction();
            Evento evento = session.get(Evento.class, id);
            if (evento == null) throw new IllegalArgumentException("No existe el evento con id: " + id);
            if (!solicitante.puedeGestionarEvento(evento)) throw new SecurityException("No tiene permisos");
            if (evento.getFechaHora().isBefore(LocalDateTime.now()))
                throw new IllegalStateException("No se puede publicar un evento pasado");
            evento.setEstado(Evento.Estado.PUBLICADO);
            session.update(evento);
            tx.commit();
        } catch (Exception e) {
            if (tx != null) tx.rollback();
            throw e;
        } finally {
            session.close();
        }
    }

    public void despublicar(Long id, Usuario solicitante) {
        cambiarEstado(id, Evento.Estado.BORRADOR, solicitante);
    }

    private void cambiarEstado(Long id, Evento.Estado estado, Usuario solicitante) {
        Session session = BaseDeDatosConfiguracion.getSessionFactory().openSession();
        Transaction tx = null;
        try {
            tx = session.beginTransaction();
            Evento evento = session.get(Evento.class, id);
            if (evento == null) throw new IllegalArgumentException("No existe el evento con id: " + id);
            if (!solicitante.puedeGestionarEvento(evento)) throw new SecurityException("No tiene permisos");
            evento.setEstado(estado);
            session.update(evento);
            tx.commit();
        } catch (Exception e) {
            if (tx != null) tx.rollback();
            throw e;
        } finally {
            session.close();
        }
    }

    public void eliminar(Long id, Usuario admin) {
        if (!admin.esAdmin()) throw new SecurityException("Solo administradores pueden eliminar");
        Session session = BaseDeDatosConfiguracion.getSessionFactory().openSession();
        Transaction tx = null;
        try {
            tx = session.beginTransaction();
            Evento evento = session.get(Evento.class, id);
            if (evento != null) session.delete(evento);
            tx.commit();
        } catch (Exception e) {
            if (tx != null) tx.rollback();
            throw e;
        } finally {
            session.close();
        }
    }

    public Evento buscarPorId(Long id) {
        Session session = BaseDeDatosConfiguracion.getSessionFactory().openSession();
        try {
            Evento evento = session.get(Evento.class, id);
            if (evento != null) {
                org.hibernate.Hibernate.initialize(evento.getInscripciones());
            }
            return evento;
        } finally {
            session.close();
        }
    }

    /**
     * FIX: inicializar inscripciones para cada evento ANTES de cerrar la sesión,
     * así los templates pueden llamar tieneCuposDisponibles(), cuposDisponibles, etc.
     */
    public List<Evento> listarPublicados() {
        Session session = BaseDeDatosConfiguracion.getSessionFactory().openSession();
        try {
            List<Evento> eventos = session.createQuery(
                            "FROM Evento e WHERE e.estado = 'PUBLICADO' AND e.fechaHora > :ahora ORDER BY e.fechaHora ASC",
                            Evento.class)
                    .setParameter("ahora", LocalDateTime.now())
                    .list();
            // Inicializar la colección lazy de cada evento mientras la sesión está abierta
            for (Evento e : eventos) {
                org.hibernate.Hibernate.initialize(e.getInscripciones());
            }
            return eventos;
        } finally {
            session.close();
        }
    }

    public List<Evento> listarPorOrganizador(Long organizadorId) {
        Session session = BaseDeDatosConfiguracion.getSessionFactory().openSession();
        try {
            List<Evento> eventos = session.createQuery(
                            "FROM Evento e WHERE e.organizador.id = :id ORDER BY e.fechaCreacion DESC",
                            Evento.class)
                    .setParameter("id", organizadorId)
                    .list();
            for (Evento e : eventos) {
                org.hibernate.Hibernate.initialize(e.getInscripciones());
            }
            return eventos;
        } finally {
            session.close();
        }
    }

    public List<Evento> listarTodos() {
        Session session = BaseDeDatosConfiguracion.getSessionFactory().openSession();
        try {
            List<Evento> eventos = session.createQuery(
                            "FROM Evento ORDER BY fechaCreacion DESC", Evento.class)
                    .list();
            for (Evento e : eventos) {
                org.hibernate.Hibernate.initialize(e.getInscripciones());
            }
            return eventos;
        } finally {
            session.close();
        }
    }

    private void validarEvento(String titulo, LocalDateTime fechaHora, String lugar, Integer cupoMaximo) {
        if (titulo == null || titulo.trim().isEmpty())
            throw new IllegalArgumentException("Título obligatorio");
        if (fechaHora == null || fechaHora.isBefore(LocalDateTime.now()))
            throw new IllegalArgumentException("Fecha debe ser futura");
        if (lugar == null || lugar.trim().isEmpty())
            throw new IllegalArgumentException("Lugar obligatorio");
        if (cupoMaximo == null || cupoMaximo < 1)
            throw new IllegalArgumentException("Cupo mínimo 1");
    }
}