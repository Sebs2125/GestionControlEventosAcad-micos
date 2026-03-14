package Eventos.Configuracion;

import Modelo.Evento;
import Modelo.InscripcionUsuario;
import Modelo.Usuario;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.hibernate.cfg.Environment;

import java.util.Properties;

public class BaseDeDatosConfiguracion
{
    private static SessionFactory sessionFactory;

    public static SessionFactory getSessionFactory()
    {
        if (sessionFactory == null)
        {
            synchronized (BaseDeDatosConfiguracion.class)
            {
                if (sessionFactory == null)
                {
                    try {
                        Configuration configuration = new Configuration();
                        Properties settings = new Properties();

                        // Punto 10a-3: H2 en MODO SERVIDOR (tcp://)
                        // En desarrollo local: jdbc:h2:tcp://localhost/~/eventos_academicos
                        // En Docker se sobreescribe con la variable DB_URL del Compose
                        String dbUrl  = System.getenv().getOrDefault("DB_URL",
                                "jdbc:h2:tcp://localhost/~/eventos_academicos");
                        String dbUser = System.getenv().getOrDefault("DB_USER", "sa");
                        String dbPass = System.getenv().getOrDefault("DB_PASS", "");

                        settings.put(Environment.DRIVER,  "org.h2.Driver");
                        settings.put(Environment.URL,     dbUrl);
                        settings.put(Environment.USER,    dbUser);
                        settings.put(Environment.PASS,    dbPass);
                        settings.put(Environment.DIALECT, "org.hibernate.dialect.H2Dialect");

                        // Punto 10a-4: creación automática de tablas
                        String hbm2ddl = System.getProperty("dev.mode") != null ? "create-drop" : "update";
                        settings.put(Environment.HBM2DDL_AUTO, hbm2ddl);
                        settings.put(Environment.SHOW_SQL,   "false");
                        settings.put(Environment.FORMAT_SQL, "true");

                        // Connection pool con HikariCP
                        settings.put(Environment.CONNECTION_PROVIDER,
                                "org.hibernate.hikaricp.internal.HikariCPConnectionProvider");
                        settings.put("hibernate.hikari.maximumPoolSize", "10");
                        settings.put("hibernate.hikari.minimumIdle",     "2");
                        settings.put("hibernate.hikari.idleTimeout",     "300000");

                        configuration.setProperties(settings);

                        configuration.addAnnotatedClass(Usuario.class);
                        configuration.addAnnotatedClass(Evento.class);
                        configuration.addAnnotatedClass(InscripcionUsuario.class);

                        sessionFactory = configuration.buildSessionFactory();

                    } catch (Exception e) {
                        System.err.println("Error inicializando SessionFactory: " + e.getMessage());
                        throw new RuntimeException(e);
                    }
                }
            }
        }
        return sessionFactory;
    }

    public static void shutdown()
    {
        if (sessionFactory != null && !sessionFactory.isClosed())
        {
            sessionFactory.close();
        }
    }
}