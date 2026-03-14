package Eventos.Configuracion;

import io.javalin.Javalin;
import io.javalin.rendering.template.JavalinThymeleaf;
import org.eclipse.jetty.server.session.SessionHandler;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

public class JavalinConfiguracion
{
    public static Javalin crearAplicacion()
    {
        TemplateEngine templateEngine = new TemplateEngine();
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setPrefix("/templates/");
        resolver.setSuffix(".html");
        resolver.setCacheable(false);
        resolver.setCharacterEncoding("UTF-8");
        templateEngine.addTemplateResolver(resolver);

        return Javalin.create(configuracion -> {
            configuracion.staticFiles.add("/public");
            configuracion.fileRenderer(new JavalinThymeleaf(templateEngine));

            configuracion.http.maxRequestSize = 50 * 1024 * 1024L;

            configuracion.jetty.sessionHandler(() -> {
                SessionHandler handler = new SessionHandler();
                handler.getSessionCookieConfig().setHttpOnly(true);
                handler.getSessionCookieConfig().setName("JSESSIONID");
                handler.getSessionCookieConfig().setPath("/");
                handler.setMaxInactiveInterval(1800);
                return handler;
            });
        });
    }
}