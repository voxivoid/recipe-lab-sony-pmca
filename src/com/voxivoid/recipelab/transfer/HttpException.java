package com.voxivoid.recipelab.transfer;

/** A request that ends in an error status (4xx/5xx), with optional extra response headers. */
public final class HttpException extends Exception {
    public final int status;
    /** Extra headers as name, value pairs. */
    public final String[] headers;

    public HttpException(int status, String message, String... headers) {
        super(message);
        this.status = status;
        this.headers = headers;
    }
}
