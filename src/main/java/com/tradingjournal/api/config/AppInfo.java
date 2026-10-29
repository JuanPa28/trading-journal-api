package com.tradingjournal.api.config;

// Identidad de esta instancia dentro de un Canary Deployment: la versión exacta
// del artefacto (APP_VERSION) y el track que la sirve (APP_TRACK: stable o
// canary). Ambos valores los inyecta el despliegue como variables de entorno,
// de modo que la misma imagen inmutable se puede promover sin reconstruirla.
// No es un bean: cada filtro lo arma con @Value para que las pruebas de capa
// web (@WebMvcTest) sigan levantando sin tener que registrarlo.
public class AppInfo {

    public static final String VERSION_HEADER = "X-App-Version";
    public static final String TRACK_HEADER = "X-App-Track";

    private final String version;
    private final String track;

    public AppInfo(String version, String track) {
        this.version = version;
        this.track = track;
    }

    public String version() {
        return version;
    }

    public String track() {
        return track;
    }

    public boolean isCanary() {
        return "canary".equalsIgnoreCase(track);
    }
}
