package com.tradingjournal.api.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

// Agrega a TODA respuesta (también a errores y a /actuator) la versión exacta y
// el track que atendió la petición, para poder ver en tiempo real si respondió
// stable o canary. Va con la precedencia más alta para que los headers estén
// puestos aunque otro filtro o un controlador falle después.
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class VersionHeaderFilter extends OncePerRequestFilter {

    private final AppInfo appInfo;

    public VersionHeaderFilter(@Value("${app.version:dev}") String version, @Value("${app.track:stable}") String track,
                               @Value("${app.name:juanpa}") String name) {
        this.appInfo = new AppInfo(version, track, name);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        response.setHeader(AppInfo.VERSION_HEADER, appInfo.version());
        response.setHeader(AppInfo.TRACK_HEADER, appInfo.track());
        response.setHeader(AppInfo.NAME_HEADER, appInfo.name());
        filterChain.doFilter(request, response);
    }
}
