package dev.frenkli.ledger.api;

import dev.frenkli.ledger.service.InstrumentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/instruments")
@Tag(name = "Instruments", description = "Securities that can be traded, and their market prices (entered by hand)")
public class InstrumentController {

    private final InstrumentService instrumentService;

    public InstrumentController(InstrumentService instrumentService) {
        this.instrumentService = instrumentService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create an instrument")
    @ApiResponse(responseCode = "201", description = "Instrument created. It cannot be traded until it has a price.")
    @ApiResponse(responseCode = "400", description = "Missing or invalid field, e.g. a symbol in lower case.",
            content = @Content(mediaType = ApiDocs.PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "409", description = "An instrument with this symbol already exists.",
            content = @Content(mediaType = ApiDocs.PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "422", description = "Currency not supported (v1: EUR only).",
            content = @Content(mediaType = ApiDocs.PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
    public InstrumentResponse create(@Valid @RequestBody CreateInstrumentRequest request) {
        return InstrumentResponse.from(instrumentService.create(request.symbol(), request.name(), request.currency()));
    }

    @GetMapping
    @Operation(summary = "List all instruments with their current prices")
    public List<InstrumentResponse> list() {
        return instrumentService.list().stream().map(InstrumentResponse::from).toList();
    }

    @PostMapping("/{symbol}/prices")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Record a market price",
            description = "Becomes the instrument's current price; earlier prices are kept as history. "
                    + "Stands in for a live price feed.")
    @ApiResponse(responseCode = "201", description = "Price recorded.")
    @ApiResponse(responseCode = "404", description = "Unknown instrument.",
            content = @Content(mediaType = ApiDocs.PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "422", description = "Price ≤ 0.",
            content = @Content(mediaType = ApiDocs.PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
    public PriceResponse recordPrice(@PathVariable String symbol, @Valid @RequestBody PriceRequest request) {
        return PriceResponse.from(instrumentService.recordPrice(symbol, request.price()));
    }
}
