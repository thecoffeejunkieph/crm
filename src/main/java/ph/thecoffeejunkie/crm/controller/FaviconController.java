package ph.thecoffeejunkie.crm.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * This is a pure REST API with no browsable UI, but browsers still fetch /favicon.ico
 * automatically for any tab pointed at this origin (e.g. someone opening an invoice PDF
 * URL directly to preview it). Answering with a plain 204 here keeps that routine request
 * from falling through to JWT auth and getting logged as if it were something noteworthy.
 */
@RestController
public class FaviconController {

    @GetMapping("/favicon.ico")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void favicon() {
    }
}
