package dev.frenkli.ledger.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class InstrumentApiTest extends AbstractApiTest {

    @Test
    void latestPriceIsTheCurrentPrice() {
        String symbol = createInstrument(1_000);
        setPrice(symbol, 1_250);

        InstrumentResponse instrument = listInstruments().stream()
                .filter(i -> i.symbol().equals(symbol)).findFirst().orElseThrow();

        assertThat(instrument.lastPrice()).isEqualTo(1_250);
        assertThat(instrument.priceAsOf()).isNotNull();
        // Price history is append-only: both prices are kept.
        assertThat(jdbc.queryForObject("SELECT count(*) FROM instrument_prices WHERE symbol = ?", Integer.class, symbol))
                .isEqualTo(2);
    }

    @Test
    void newInstrumentHasNoPriceYet() {
        rest.postForEntity("/instruments", Map.of("symbol", "NOPRICE1", "name", "No price", "currency", "EUR"), String.class);

        InstrumentResponse instrument = listInstruments().stream()
                .filter(i -> i.symbol().equals("NOPRICE1")).findFirst().orElseThrow();

        assertThat(instrument.lastPrice()).isNull();
    }

    @Test
    void duplicateSymbolIs409() {
        String symbol = createInstrument(100);

        ResponseEntity<Map> response = rest.postForEntity("/instruments",
                Map.of("symbol", symbol, "name", "Again", "currency", "EUR"), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).containsEntry("type", "urn:problem:ledger:instrument-already-exists");
    }

    @Test
    void lowerCaseSymbolIs400() {
        ResponseEntity<Map> response = rest.postForEntity("/instruments",
                Map.of("symbol", "acme", "name", "Acme", "currency", "EUR"), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void unsupportedCurrencyIs422() {
        ResponseEntity<Map> response = rest.postForEntity("/instruments",
                Map.of("symbol", "USDONLY", "name", "Dollar stock", "currency", "USD"), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    void nonPositivePriceIs422() {
        String symbol = createInstrument(100);

        ResponseEntity<Map> response = rest.postForEntity("/instruments/" + symbol + "/prices", Map.of("price", 0), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    void priceForUnknownInstrumentIs404() {
        ResponseEntity<Map> response = rest.postForEntity("/instruments/NOSUCH/prices", Map.of("price", 100), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).containsEntry("type", "urn:problem:ledger:instrument-not-found");
    }

    private List<InstrumentResponse> listInstruments() {
        return rest.exchange("/instruments", HttpMethod.GET, null,
                new ParameterizedTypeReference<List<InstrumentResponse>>() { }).getBody();
    }
}
