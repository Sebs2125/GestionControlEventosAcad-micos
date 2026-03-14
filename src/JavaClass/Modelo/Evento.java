package Modelo;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import javax.persistence.*;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table( name = "eventos" )
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Evento
{
    public enum Estado {
        BORRADOR, PUBLICADO, CANCELADO
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String titulo;

    @Column
    private String descripcion;

    @Column(name = "fecha_hora", nullable = false)
    private LocalDateTime fechaHora;

    @Column(nullable = false)
    private String lugar;

    @Column(name = "cupo_maximo", nullable = false)
    private Integer cupoMaximo;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "organizador_id", nullable = false)
    @JsonIgnoreProperties({"inscripciones", "eventosOrganizados", "password", "hibernateLazyInitializer", "handler"})
    private Usuario organizador;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Estado estado = Estado.BORRADOR;

    // FIX: @JsonIgnore en la colección y en todos los métodos calculados que la usan.
    // Jackson serializa getters públicos automáticamente — al llamar getTotalInscritos(),
    // getTotalAsistentes(), etc., estos iteran sobre inscripciones (lazy) con sesión cerrada
    // → LazyInitializationException. Ignorarlos en JSON corta el problema de raíz.
    @JsonIgnore
    @OneToMany(mappedBy = "evento", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private Set<InscripcionUsuario> inscripciones = new HashSet<>();

    @Column(name = "fecha_creacion")
    private LocalDateTime fechaCreacion = LocalDateTime.now();

    public Evento() {}

    public Evento( String titulo, String descripcion, LocalDateTime fechaHora, String lugar, Integer cupoMaximo, Usuario organizador)
    {
        this.titulo = titulo;
        this.descripcion = descripcion;
        this.fechaHora = fechaHora;
        this.lugar = lugar;
        this.cupoMaximo = cupoMaximo;
        this.organizador = organizador;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTitulo() { return titulo; }
    public void setTitulo(String titulo) { this.titulo = titulo; }
    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }
    public LocalDateTime getFechaHora() { return fechaHora; }
    public void setFechaHora(LocalDateTime fechaHora) { this.fechaHora = fechaHora; }
    public String getLugar() { return lugar; }
    public void setLugar(String lugar) { this.lugar = lugar; }
    public Integer getCupoMaximo() { return cupoMaximo; }
    public void setCupoMaximo(Integer cupoMaximo) { this.cupoMaximo = cupoMaximo; }
    public Usuario getOrganizador() { return organizador; }
    public void setOrganizador(Usuario organizador) { this.organizador = organizador; }
    public Estado getEstado() { return estado; }
    public void setEstado(Estado estado) { this.estado = estado; }

    @JsonIgnore
    public Set<InscripcionUsuario> getInscripciones() { return inscripciones; }
    public void setInscripciones(Set<InscripcionUsuario> inscripciones) { this.inscripciones = inscripciones; }

    public LocalDateTime getFechaCreacion() { return fechaCreacion; }
    public void setFechaCreacion(LocalDateTime fechaCreacion) { this.fechaCreacion = fechaCreacion; }

    // Todos los métodos que acceden a la colección lazy deben ignorarse en JSON
    @JsonIgnore
    public long getTotalInscritos() {
        return inscripciones.stream()
                .filter(i -> i.getEstado() == InscripcionUsuario.Estado.ACTIVA)
                .count();
    }

    @JsonIgnore
    public long getTotalAsistentes() {
        return inscripciones.stream()
                .filter(i -> i.getEstado() == InscripcionUsuario.Estado.ACTIVA && i.isAsistio())
                .count();
    }

    @JsonIgnore
    public int getCuposDisponibles() {
        return cupoMaximo - (int) getTotalInscritos();
    }

    @JsonIgnore
    public boolean tieneCuposDisponibles() {
        return getCuposDisponibles() > 0;
    }

    @JsonIgnore
    public double getPorcentajeAsistencia() {
        long inscritos = getTotalInscritos();
        return inscritos > 0 ? (getTotalAsistentes() * 100.0 / inscritos) : 0.0;
    }

    @JsonIgnore
    public boolean estaActivo() {
        return estado == Estado.PUBLICADO && fechaHora.isAfter(LocalDateTime.now());
    }
}