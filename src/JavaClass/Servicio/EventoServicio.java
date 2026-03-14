package Servicio;

import Eventos.Configuracion.BaseDeDatosConfiguracion;
import Modelo.Evento;
import Modelo.Usuario;
import org.hibernate.Session;
import org.hibernate.Transaction;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public class EventoServicio
{
    public Evento crear (String titulo, String descripcion, LocalDateTime fechaHora, String lugar, Integer cupoMaximo, Usuario organizador )
    {
        validarEvento( titulo, fechaHora, lugar, cupoMaximo );

        Session session = BaseDeDatosConfiguracion.getSessionFactory().openSession();
        Transaction tx = null;

        try {
            tx = session.beginTransaction();

            Evento evento = new Evento( titulo, descripcion, fechaHora, lugar, cupoMaximo, organizador );
            session.save(evento);

            tx.commit();
            return evento;

        } catch (Exception e)
        {
            if ( tx != null ) tx.rollback();
            throw e;
        } finally {
            session.close();
        }

    }

    public Evento editar ( Long id, String titulo, String descripcion, LocalDateTime fechaHora, String lugar, Integer cupoMaximo, Usuario solicitante )
    {
        Session session = BaseDeDatosConfiguracion.getSessionFactory().openSession();
        Transaction tx = null;

        try {
            tx = session.beginTransaction();
            Evento evento = (Evento) session.get(Evento.class, id);

            if ( evento == null ) throw new IllegalArgumentException("No existe el evento con el id: " + id);

            if ( !solicitante.puedeGestionarEvento(evento))
            {
                throw new SecurityException("No tiene permisos para editar este evento");
            }

            if ( evento.getFechaHora().isBefore(LocalDateTime.now()) )
            {
                throw new IllegalStateException("No se puede editar un evento pasado");
            }

            validarEvento( titulo, fechaHora, lugar, cupoMaximo );

            long inscritos = evento.getTotalInscritos();

            if ( cupoMaximo < inscritos )
            {
                throw new IllegalArgumentException("No puede reducir el cupo por debajo de " +inscritos + "inscritos");
            }

            evento.setTitulo(titulo);
            evento.setDescripcion(descripcion);
            evento.setFechaHora(fechaHora);
            evento.setLugar(lugar);
            evento.setCupoMaximo(cupoMaximo);

            session.update(evento);
            tx.commit();

            return evento;

        } catch (Exception e )
        {
            if ( tx != null ) tx.rollback();
            throw e;
        } finally {
            session.close();
        }

    }

    public void cancelar (Long id, Usuario solicitante )
    {
        cambiarEstado( id, Evento.Estado.CANCELADO, solicitante );
    }

    public void publicar ( Long id, Usuario solicitante )
    {
        Session session = BaseDeDatosConfiguracion.getSessionFactory().openSession();
        Transaction tx = null;

        try {
            tx = session.beginTransaction();
            Evento evento = (Evento) session.get(Evento.class, id);

            if ( evento == null ) throw new IllegalArgumentException("No existe el evento con el id: " + id);

            if ( !solicitante.puedeGestionarEvento(evento))
            {
                throw new SecurityException("No tiene permisos para publicar");
            }

            if ( evento.getFechaHora().isBefore(LocalDateTime.now()) )
            {
                throw new IllegalStateException("No tiene permisos para publicar");
            }

            evento.setEstado(Evento.Estado.PUBLICADO);
            session.update(evento);
            tx.commit();

        } catch ( Exception e )
        {
            if ( tx != null ) tx.rollback();
            throw e;
        } finally {
            session.close();
        }

    }

    public void despublicar(Long id, Usuario solicitante)
    {
        cambiarEstado(id, Evento.Estado.BORRADOR, solicitante);
    }

    private void cambiarEstado( Long id, Evento.Estado estado, Usuario solicitante )
    {
        Session session = BaseDeDatosConfiguracion.getSessionFactory().openSession();
        Transaction tx = null;

        try {
            tx = session.beginTransaction();
            Evento evento = (Evento) session.get(Evento.class, id);

            if ( evento == null ) throw new IllegalArgumentException("No existe el evento con el id: " + id);

            if ( !solicitante.puedeGestionarEvento(evento))
            {
                throw new SecurityException("No tiene permisos para publicar");
            }

            evento.setEstado(estado);
            session.update(evento);
            tx.commit();

        } catch ( Exception e )
        {
            if ( tx != null ) tx.rollback();
            throw e;
        } finally {
            session.close();
        }
    }

    public void eliminar( Long id, Usuario admin )
    {
        if ( !admin.esAdmin())
        {
            throw new SecurityException("Solo administradores pueden eliminar");
        }

        Session session = BaseDeDatosConfiguracion.getSessionFactory().openSession();
        Transaction tx = null;

        try {
            tx = session.beginTransaction();
            Evento evento = (Evento) session.get(Evento.class, id);

            if ( evento != null ) session.delete( evento );
            tx.commit();

        } catch ( Exception e )
        {
            if ( tx != null ) tx.rollback();
            throw e;
        } finally {
            session.close();
        }

    }

    public Evento buscarPorId(Long id )
    {
        Session session = BaseDeDatosConfiguracion.getSessionFactory().openSession();
        try {
            Evento evento = session.get(Evento.class, id);
            if (evento != null) {
                // FIX: inicializar colección LAZY antes de cerrar sesión
                org.hibernate.Hibernate.initialize(evento.getInscripciones());
            }
            return evento;
        } finally {
            session.close();
        }

    }

    public List<Evento> listarPublicados()
    {
        Session session = BaseDeDatosConfiguracion.getSessionFactory().openSession();

        try {
            return session.createQuery(
                            "FROM Evento e WHERE e.estado = 'PUBLICADO' " +
                                    "AND e.fechaHora > :ahora ORDER BY e.fechaHora ASC", Evento.class)
                    .setParameter("ahora", LocalDateTime.now())  // <-- este es el cambio
                    .list();

        } finally {
            session.close();
        }
    }

    public List<Evento> listarPorOrganizador(Long organizadorId)
    {

        Session session = BaseDeDatosConfiguracion.getSessionFactory().openSession();

        try
        {
            return session.createQuery(
                            "FROM Evento e WHERE e.organizador.id = :id " +
                                    "ORDER BY e.fechaCreacion DESC", Evento.class)
                    .setParameter("id", organizadorId)
                    .list();
        } finally {
            session.close();
        }
    }

    public List<Evento> listarTodos()
    {

        Session session = BaseDeDatosConfiguracion.getSessionFactory().openSession();

        try
        {
            return session.createQuery(
                            "FROM Evento ORDER BY fechaCreacion DESC", Evento.class)
                    .list();
        } finally {
            session.close();
        }
    }

    private void validarEvento(String titulo, LocalDateTime fechaHora, String lugar, Integer cupoMaximo)
    {

        if (titulo == null || titulo.trim().isEmpty())
        {
            throw new IllegalArgumentException("Título obligatorio");
        }

        if (fechaHora == null || fechaHora.isBefore(LocalDateTime.now()))
        {
            throw new IllegalArgumentException("Fecha debe ser futura");
        }

        if (lugar == null || lugar.trim().isEmpty())
        {
            throw new IllegalArgumentException("Lugar obligatorio");
        }

        if (cupoMaximo == null || cupoMaximo < 1)
        {
            throw new IllegalArgumentException("Cupo mínimo 1");
        }
    }



}