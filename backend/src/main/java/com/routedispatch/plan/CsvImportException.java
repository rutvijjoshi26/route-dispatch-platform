package com.routedispatch.plan;

import java.util.List;

/** The uploaded order file had problems; nothing was imported. */
public class CsvImportException extends RuntimeException {

    private final transient List<CsvOrderParser.RowError> errors;

    public CsvImportException(List<CsvOrderParser.RowError> errors) {
        super("The order file has " + errors.size() + " problem(s); nothing was imported");
        this.errors = List.copyOf(errors);
    }

    public List<CsvOrderParser.RowError> getErrors() {
        return errors;
    }
}
