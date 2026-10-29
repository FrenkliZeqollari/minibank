package dev.frenkli.ledger.api;

import static org.assertj.core.api.Assertions.assertThat;

import dev.frenkli.ledger.AbstractIntegrationTest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * The React frontend (frontend/) is built by Maven and served by the same application from the
 * jar's static/ folder. These tests check that the build output really is in place and served.
 */
class FrontendTest extends AbstractIntegrationTest {

    @Autowired
    TestRestTemplate rest;

    @Test
    void rootServesTheApp() {
        ResponseEntity<String> response = rest.getForEntity("/", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType().toString()).startsWith("text/html");
        assertThat(response.getBody()).contains("<title>Minibank</title>", "<div id=\"root\"></div>");
    }

    @Test
    void builtScriptAndStylesheetAreServedWithTheRightContentType() {
        String html = rest.getForObject("/", String.class);
        // Vite names the files after their content hash, e.g. /assets/index-C7OKFP1o.js.
        String script = find(html, "src=\"(/assets/[^\"]+\\.js)\"");
        String stylesheet = find(html, "href=\"(/assets/[^\"]+\\.css)\"");

        ResponseEntity<String> js = rest.getForEntity(script, String.class);
        ResponseEntity<String> css = rest.getForEntity(stylesheet, String.class);

        // Browsers refuse to run an ES module that is not served as JavaScript.
        assertThat(js.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(js.getHeaders().getContentType().toString()).contains("javascript");
        assertThat(css.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(css.getHeaders().getContentType().toString()).startsWith("text/css");
    }

    private static String find(String html, String regex) {
        Matcher matcher = Pattern.compile(regex).matcher(html);
        assertThat(matcher.find()).as("%s in index.html", regex).isTrue();
        return matcher.group(1);
    }
}
