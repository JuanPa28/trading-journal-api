package com.tradingjournal.api.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class VersionHeaderFilterTest {

    @Test
    void addsVersionAndTrackHeadersToTheResponse() throws Exception {
        VersionHeaderFilter filter = new VersionHeaderFilter(new AppInfo("3.1.0", "stable"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(new MockHttpServletRequest("GET", "/api/traders"), response, new MockFilterChain());

        assertThat(response.getHeader(AppInfo.VERSION_HEADER)).isEqualTo("3.1.0");
        assertThat(response.getHeader(AppInfo.TRACK_HEADER)).isEqualTo("stable");
    }

    @Test
    void reportsTheCanaryTrack() throws Exception {
        AppInfo canary = new AppInfo("4.0.0", "canary");
        VersionHeaderFilter filter = new VersionHeaderFilter(canary);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(new MockHttpServletRequest("GET", "/actuator/health"), response, new MockFilterChain());

        assertThat(canary.isCanary()).isTrue();
        assertThat(response.getHeader(AppInfo.TRACK_HEADER)).isEqualTo("canary");
    }
}
