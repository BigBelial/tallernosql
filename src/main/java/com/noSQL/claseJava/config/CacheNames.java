package com.noSQL.claseJava.config;

// Nombres de las cachés de Redis en un solo lugar, para no equivocarse escribiéndolos
public final class CacheNames {

    public static final String BOOKS = "books";
    public static final String BOOK_DETAILS = "bookDetails";
    public static final String BOOK_QUERIES = "bookQueries";
    public static final String USERS = "users";
    public static final String FAVORITES = "favorites";
    public static final String LOANS = "loans";
    public static final String LOAN_STATS = "loanStats";
    public static final String REVIEWS = "reviews";
    public static final String AUTHORS = "authors";
    public static final String EDITORIALS = "editorials";
    public static final String CATEGORIES = "categories";

    private CacheNames() {
    }
}
