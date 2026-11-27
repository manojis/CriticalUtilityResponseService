package edu.gatech.erms.domain;

/** One row of the resources-by-ESF report. */
public record EsfReportRow(
    int esfNumber, String esfDescription, long totalResources, long resourcesInUse) {}
