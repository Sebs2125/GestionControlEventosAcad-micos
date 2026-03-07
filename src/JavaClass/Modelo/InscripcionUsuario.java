package Modelo;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table( name = "inscripciones", uniqueConstraints = @UniqueConstraint(columnNames = {"evento_id", "participante_id"}))

public class InscripcionUsuario
{
    public enum Estado {
        ACTIVA, CANCELADA
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "evento_id", nullable = false)
    private Evento evento;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "evento_id", nullable = false)
    private Usuario participante;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Estado estado = Estado.ACTIVA;

    @Column(name = "fecha_inscripcion")
    private LocalDateTime fechaInscripcion = LocalDateTime.now();

    @Column(name = "fecha_asistencia")
    private LocalDateTime fechaAsistencia = LocalDateTime.now();

    private boolean asistio = false;

    @Column(name = "token_qr", unique = true)
    private String tokenQR;

    public InscripcionUsuario() {}

    public InscripcionUsuario( Evento evento, Usuario participante, String tokenQR)
    {
        this.evento = evento;
        this.participante = participante;
        this.tokenQR = tokenQR;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Evento getEvento() {
        return evento;
    }

    public void setEvento(Evento evento) {
        this.evento = evento;
    }

    public Usuario getParticipante() {
        return participante;
    }

    public void setParticipante(Usuario participante) {
        this.participante = participante;
    }

    public Estado getEstado() {
        return estado;
    }

    public void setEstado(Estado estado) {
        this.estado = estado;
    }

    public LocalDateTime getFechaInscripcion() {
        return fechaInscripcion;
    }

    public void setFechaInscripcion(LocalDateTime fechaInscripcion) {
        this.fechaInscripcion = fechaInscripcion;
    }

    public LocalDateTime getFechaAsistencia() {
        return fechaAsistencia;
    }

    public void setFechaAsistencia(LocalDateTime fechaAsistencia) {
        this.fechaAsistencia = fechaAsistencia;
    }

    public boolean isAsistio() {
        return asistio;
    }

    public void setAsistio(boolean asistio) {
        this.asistio = asistio;
    }

    public String getTokenQR() {
        return tokenQR;
    }

    public void setTokenQR(String tokenQR) {
        this.tokenQR = tokenQR;
    }

    public void marcarAsistencia() {
        if ( !this.asistio)
        {
            this.asistio = true;
            this.fechaAsistencia = LocalDateTime.now();
        }
    }

    public void cancelar()
    {
        this.estado = Estado.CANCELADA;
    }

}