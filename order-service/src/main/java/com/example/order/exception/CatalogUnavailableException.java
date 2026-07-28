package com.example.order.exception;

/**
 * book-service could not be reached or answered with a server error or timeout.
 *
 * <p>Kept separate from the catalog's business answers ("no stock", "no such book") because the two
 * need opposite treatment: this one is worth retrying and worth counting towards the circuit breaker,
 * while a business answer is a final result that must not be retried at all.
 */
public class CatalogUnavailableException extends RuntimeException {

    public CatalogUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }

    public CatalogUnavailableException(String message) {
        super(message);
    }
}
