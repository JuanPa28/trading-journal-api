package com.tradingjournal.api.config;

import com.oracle.bmc.objectstorage.ObjectStorageClient;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ObjectStorageConfigTest {

    // Llave de prueba generada solo para este test (no es una llave real de
    // ninguna cuenta) — el SDK solo la parsea al construir el cliente, no
    // abre conexión hasta que se haga una llamada real.
    private static final String FAKE_PRIVATE_KEY = """
            -----BEGIN PRIVATE KEY-----
            MIIEvgIBADANBgkqhkiG9w0BAQEFAASCBKgwggSkAgEAAoIBAQCsT+RmLFDrsvDd
            Nm0mB5zdKmbHXKfe7SczjKf3X0uD8Y0uosGuephTEYNKD3viBr2NF6rhiXH1uTW9
            EU7Var4A63PDJkuBK/aXQPqHWNgFVhmHhgg+T8HGjctLwXPrTFD+4XahKsMCERW1
            dsELm+XYseQSlfg+HYS9jXt/YI2uD/1tff5K4mSypcCG6wMaJFjSXAFPhKdL6pZ3
            F/usbGtZlpW/qJriLJN2M08hTAtzMiKuPkAyHO58lDaqBzLUZPnC+SY1RbkNWZyd
            IdvIK+x6xk0OQNTtLoxxW6V/FLVHH5nB+qpp9CLCxEZDpWRL/SrcMlJ1Gmdnb9qJ
            y70Md//xAgMBAAECggEAC3NHpy58thzu+pQ6TpOk7eF6cPcMRLkXqMpJAxw3BW6Q
            xi+UlUaaRrLcOZirz8pI4E5ZUaFzjDOhiY3T4kS0mZUL3UT3MX89ZLGxtDrIKBqf
            mazjcViVdN9JSiBx1on8u+M5od4YlVOTULqye1YBwROZJ08F7wLBJmN3eHBvGcjk
            UQLXQ6v40nkPh8OAzDW1iQWh4V1f4xSOaXkiZyPFKjHhvzdr1TH3pTBdCniNy4N5
            ZUmQmiPHQ441Wup2qPPks+NyUa2rUmFyOKZVoVOWrkhRXa3w6OhOdEMkk1/Dy61m
            +PqBen8sGfTXA3DB8zmlZ+JnW4o3c7zrNR1ogiqRrQKBgQDUZ97lM0pLXUE3Ynzk
            zvSi1tzWxmdgRDUY/3q72siI65jAZtE7TduAfdYiDIN0mHnv4zKheWn6twAyVXdi
            fzKu4sTyVDRAR5VSP4zeVRokVjRQ/5sQM/wc/uJ3745NQkslSzXrOR4MpSaq+mDn
            0KXlqxlaq8YSpwp0jxA2mzebbwKBgQDPrXAOL+qZ7LUCbAagkt8xcUm89tjl5NMW
            eklBct8tu5UV4o9cfIV1ikNMTgDUOElHu+G21wI6DoAJM7Z1jCBEL/a0E9uCSt15
            F6gE71LptyzW9x3EyoZ5Sg3ok+nqfbwjrHfU8Ll7kU3W/kYb9gt2PpHt2v1GHgP/
            hMcRm1XqnwKBgQCw2uvUm6g4qCUtIGwtd8Rp8c1fZ0Ob/C7Z6Z8f20Mt/jzptrJw
            Vpt1mVE6eIDcDDk7bEkz+gwsHUOkpVUKRi7FPzwNKd93xiKmoROB7AogR0a4Spuq
            Gh7JZdDbXDN4UMKRAtaaTKygisJvipo1ap03ZgduX6hEz6D8/EHbtdHPMQKBgH8B
            MykJ9q/hiaUMiVUD3Qjwnka4s1aMhOcXK9uhs4jVUcxLfJ7z6+5A+v0cKved4HrF
            FLlrbgoxOe3muSvT4RuyNW5VQoXA4QagB/nUnmEdy4c/Iu5paKmeNzlH0+K6uCcc
            3z/TY6uE5hSX9io9Yy8R/s52gTOzQYCvI8rJs1ZDAoGBAMkDyIjR33UESAmE198k
            hzCSvOFGS4avbJCPgA0tZ5qE1bjru58uNsfbHP1HvSdTus6mhcQYXKacA/lx5lda
            5uH2o/+Ck+sXdhIR9QWpJWkWCF26qVEz/MM0JNXBeMl9MtPzhQ+Vh75HPYrLtZjn
            USQSIltejKxIz2/qqRrGmw6j
            -----END PRIVATE KEY-----
            """;

    @Test
    void objectStorageClient_buildsSuccessfully_fromConfigValues() {
        ObjectStorageConfig config = new ObjectStorageConfig();

        ObjectStorageClient client = config.objectStorageClient(
                "ocid1.tenancy.oc1..fake",
                "ocid1.user.oc1..fake",
                "aa:bb:cc:dd",
                FAKE_PRIVATE_KEY,
                "sa-bogota-1"
        );

        assertThat(client).isNotNull();
    }
}
