package com.stefensharkey.osrsxpstatistics;

public enum DatabaseType {

    MYSQL("mysql"),
    MARIADB("mariadb");

    private final String name;

    DatabaseType(String name) {
        this.name = name;
    }

    public String getName() {
        return this.name;
    }
}
