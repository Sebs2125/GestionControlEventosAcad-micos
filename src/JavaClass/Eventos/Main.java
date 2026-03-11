package Eventos;

import Controlador.ApiControlador;
import Controlador.AutorizacionControlador;
import Controlador.EventoControlador;
import Eventos.Configuracion.BaseDeDatosConfiguracion;
import Eventos.Configuracion.JavalinConfiguracion;
import Servicio.EventoServicio;
import Servicio.InscripcionServicio;
import Servicio.UsuarioServicio;
import io.javalin.Javalin;

public class Main
{
    public static void main(String[] args)
    {
        BaseDeDatosConfiguracion.getSessionFactory();

        UsuarioServicio usuarioServicio = new UsuarioServicio();
        EventoServicio eventoServicio = new EventoServicio();
        InscripcionServicio inscripcionServicio = new InscripcionServicio();

        usuarioServicio.inicializarAdmin();

        Javalin app = JavalinConfiguracion.crearAplicacion();



        new AutorizacionControlador(usuarioServicio).registrarRutas(app);
        new EventoControlador(eventoServicio,inscripcionServicio).registrarRutas(app);


        ApiControlador apiControlador=new ApiControlador(inscripcionServicio);
        apiControlador.registrarRutas(app);


        app.get("/health", ctx -> ctx.json(new Object() {
            public final String status = "OK";
            public final String version = "1.0.0";
        }));

        int puerto = Integer.parseInt(System.getenv(). getOrDefault("PORT", "7000"));
        app.start(puerto);



        Runtime.getRuntime().addShutdownHook(new Thread(BaseDeDatosConfiguracion::shutdown));

        System.out.println("🚀 Servidor iniciado en puerto: " + puerto);
        System.out.println("🌐 URL: http://localhost:" + puerto);

    }
}