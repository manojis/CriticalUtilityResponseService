package edu.gatech.erms.model;

import org.springframework.stereotype.Repository;

/**
 * @author Manoj.Mohanan Nair
 * @Date 7/19/18
 */
@Repository
public class Declaration {

    public String declaration_abbreviation;
    public String declaration_name;

    public String getDeclaration_abbreviation() {
        return declaration_abbreviation;
    }

    public void setDeclaration_abbreviation(String declaration_abbreviation) {
        this.declaration_abbreviation = declaration_abbreviation;
    }

    public String getDeclaration_name() {
        return declaration_name;
    }

    public void setDeclaration_name(String declaration_name) {
        this.declaration_name = declaration_name;
    }
}
