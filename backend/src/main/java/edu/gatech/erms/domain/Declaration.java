package edu.gatech.erms.domain;

/** An incident declaration type, e.g. {@code MD} = Major Disaster. */
public record Declaration(String abbreviation, String name) {}
