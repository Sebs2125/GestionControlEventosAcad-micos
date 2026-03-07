package Eventos.Configuracion;

import io.javalin.Javalin;
import io.javalin.rendering.template.JavalinThymeleaf;
import org.eclipse.jetty.server.session.DefaultSessionCache;
import org.eclipse.jetty.server.session.FileSessionDataStore;
import org.eclipse.jetty.server.session.SessionCache;
import org.eclipse.jetty.server.session.SessionHandler;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.io.File;

public class JavalinConfiguracion
{
    public static Javalin crearAplicacion()
    {
        //Configurar Thymeleaf
        TemplateEngine templateEngine = new TemplateEngine();
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setPrefix("/templates/");
        resolver.setSuffix(".html");
        resolver.setCacheable(false);
        resolver.setCharacterEncoding("UTF-8");
        templateEngine.addTemplateResolver(resolver);

        return Javalin.create( configuracion -> {
            configuracion.staticFiles.add("/public");
            configuracion.fileRenderer(new JavalinThymeleaf(templateEngine));
            configuracion.jetty.sessionHandler(() -> {
                SessionHandler sessionHandler = new SessionHandler();

                sessionHandler.getSessionCookieConfig().setHttpOnly(true);
                sessionHandler.getSessionCookieConfig().setSecure(true);
                sessionHandler.getSessionCookieConfig().setName("JSESSIONID");
                sessionHandler.getSessionCookieConfig().setPath("/");
                sessionHandler.setMaxInactiveInterval(1800);

                SessionCache cache = new DefaultSessionCache(sessionHandler);
                FileSessionDataStore store = new FileSessionDataStore();

                File storeDir = new File(System.getProperty("java.io.tmpdir", "eventos-sessions"));

                if ( !storeDir.exists() )
                {
                    storeDir.mkdirs();
                }

                store.setStoreDir(storeDir);
                cache.setSessionDataStore(store);
                sessionHandler.setSessionCache(cache);

                return sessionHandler;

            });

        });

    }

    public static Javalin crearAplicacionSimple()
    {
        TemplateEngine templateEngine = new TemplateEngine();
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setPrefix("/templates/");
        resolver.setSuffix(".html");
        resolver.setCacheable(false);
        resolver.setCharacterEncoding("UTF-8");
        templateEngine.addTemplateResolver(resolver);

        return Javalin.create( configuracion -> {
            configuracion.staticFiles.add("/public");
            configuracion.fileRenderer(new JavalinThymeleaf(templateEngine));

            configuracion.jetty.sessionHandler(() -> {
                SessionHandler handler = new SessionHandler();
                handler.setMaxInactiveInterval(1800);
                return handler;
            });
        });
    }

}

