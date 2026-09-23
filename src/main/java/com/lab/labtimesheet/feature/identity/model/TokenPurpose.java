package com.lab.labtimesheet.feature.identity.model;

/** Purpose discriminator preventing one bearer-token class from serving another workflow. */
public enum TokenPurpose {
    ACTIVATION,
    PASSWORD_RESET
}
