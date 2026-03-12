package Servicio;

import Eventos.Configuracion.BaseDeDatosConfiguracion;
import Modelo.Evento;
import org.hibernate.Session;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class EstadisticaServicio
{
    public Map<String, Object> obtenerResumenEvento( Long eventoId )
    {
        Session session = BaseDeDatosConfiguracion.getSessionFactory().openSession();

        try {
            Evento evento = session.get(Evento.class, eventoId);

            if ( evento == null )
            {
                throw new IllegalArgumentException("Evento no encontrado");
            }

            Map<String, Object> resumen = new HashMap<>();
            resumen.put("eventoId", eventoId );
            resumen.put("titulo", evento.getTitulo() );
            resumen.put("fechaEvento", evento.getFechaHora() );
            resumen.put("cupoMaximo", evento.getCupoMaximo() );
            resumen.put("totalInscritos", evento.getTotalInscritos() );
            resumen.put("totalAsistentes", evento.getTotalAsistentes() );
            resumen.put("porcentajeAsistencia", Math.round(evento.getPorcentajeAsistencia() * 100.0) / 100.0 );
            resumen.put("cuposDisponibles", evento.getCuposDisponibles() );

            return resumen;

        } finally {
            session.close();
        }

    }

    public List<Map<String, Object>> inscripcionesPorDia( Long eventoId )
    {
        Session session = BaseDeDatosConfiguracion.getSessionFactory().openSession();

        try {
            String sql = """ 
                    SELECT
                        CAST(fecha_inscripcion AS DATE) as fecha,
                        COUNT(*) as cantidad
                    FROM inscripciones
                    WHERE evento_id = :eventoId
                    AND estado = 'ACTIVA'
                    GROUP BY CAST (fecha_inscripcion AS DATE)
                    ORDER BY fecha
                    """;

            @SuppressWarnings("unchecked")
            List<Object[]> resultados = session.createNativeQuery(sql)
                    .setParameter("eventoId", eventoId)
                    .list();

            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

            return resultados.stream()
                    .map(fila -> {
                        Map<String, Object> dato = new HashMap<>();
                        java.sql.Date fechaSql = (java.sql.Date) fila[0];
                        LocalDate fecha = fechaSql.toLocalDate();
                        dato.put("fecha", fecha.format(formatter));
                        dato.put("fechaRaw", fecha.toString());
                        dato.put("cantidad", ((Number) fila[1]).intValue());
                        return dato;
                    })

                    .collect(Collectors.toList());

        } finally {
            session.close();
        }

    }

    public List<Map<String, Object>> asistenciasPorHora( Long eventoId )
    {
        Session session = BaseDeDatosConfiguracion.getSessionFactory().openSession();

        try {
            String sql = """
                    SELECT
                        HOUR(fecha_asistencia) as hora,
                        COUNT (*) as cantidad
                    FROM inscripciones
                    WHERE evento_id = :eventoId
                    AND asistio = true
                    AND fecha_asistencia IS NOT NULL
                    GROUP BY HOUR (fecha_asistencia)
                    ORDER BY hora
                    """;

            @SuppressWarnings("unchecked")
            List<Object[]> resultados = session.createNativeQuery(sql)
                    .setParameter("eventoId", eventoId)
                    .list();

            return resultados.stream()
                    .map( fila -> {
                        Map<String, Object> dato = new HashMap<>();
                        int hora = ((Number) fila[0]).intValue();
                        dato.put("hora", String.format("%02d:00", hora));
                        dato.put("horaNumero", hora);
                        dato.put("cantidad", ((Number) fila[1]).intValue());
                        return dato;
                    })
                    .collect(Collectors.toList());
        } finally {
            session.close();
        }

    }

    public Map<String, Object> obtenerEstadisticasCompletas( Long eventoId )
    {
        Map<String, Object> estadisticas = new HashMap<>();
        estadisticas.put("resumen", obtenerResumenEvento( eventoId ) );
        estadisticas.put("inscripcionesPorDia", inscripcionesPorDia( eventoId ) );
        estadisticas.put("asistenciasPorHora", asistenciasPorHora( eventoId ) );
        return estadisticas;
    }

}
