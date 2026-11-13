package com.tradingjournal.api.config;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class RequestLoggingFilterTest {

    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private Logger logger;

    @BeforeEach
    void attach() {
        logger = (Logger) LoggerFactory.getLogger(RequestLoggingFilter.class);
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void detach() {
        logger.detachAppender(appender);
    }

    private String accessLine() {
        return appender.list.stream().map(ILoggingEvent::getFormattedMessage)
                .filter(m -> m.startsWith("event=request ")).findFirst().orElseThrow();
    }

    @Test
    void writesOneAccessLineWithTheAgreedFields() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v2/trades/5540");
        request.addHeader(RequestLoggingFilter.TRACE_ID_HEADER, "trace-123");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(200);

        new RequestLoggingFilter("juanpa").doFilter(request, response, new MockFilterChain());

        String line = accessLine();
        assertThat(line).contains("api=juanpa", "traceId=trace-123", "method=GET",
                "route=/api/v2/trades/5540", "status=200");
        assertThat(line).containsPattern("ms=\\d+$");
    }

    @Test
    void generatesATraceIdWhenNoneArrivesAndEchoesIt() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        new RequestLoggingFilter("juanpa").doFilter(new MockHttpServletRequest("GET", "/actuator/health"), response,
                new MockFilterChain());

        String generated = response.getHeader(RequestLoggingFilter.TRACE_ID_HEADER);
        assertThat(generated).isNotBlank();
        assertThat(accessLine()).contains("traceId=" + generated);
    }

    @Test
    void logsTheAccessLineEvenWhenTheRequestFails() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/trades/1");
        try {
            new RequestLoggingFilter("juanpa").doFilter(request, new MockHttpServletResponse(),
                    (req, res) -> {
                        throw new IllegalStateException("fallo simulado");
                    });
        } catch (Exception expected) {
            // el filtro deja pasar la excepción; lo que importa es que haya quedado la línea de acceso
        }

        assertThat(accessLine()).contains("api=juanpa", "route=/api/trades/1");
    }
}
