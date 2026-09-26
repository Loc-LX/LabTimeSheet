package com.lab.labtimesheet.platform.authorization;

/** A §5.2 actor column resolved by the module that owns the protected record. */
public enum AuthorizationColumn {
    /** An active global Admin. */
    ADMIN("Admin"),
    /** The Mentor who owns the relevant Project or responsible-Mentor scope. */
    OWNING_MENTOR("Owning Mentor"),
    /** An Intern with a current leadership term for the relevant Project. */
    CURRENT_LEADER("Current Leader"),
    /** An active member, assignee, or owner of the relevant record. */
    ACTIVE_MEMBER_ASSIGNEE("Active member / assignee");

    private final String matrixHeading;

    AuthorizationColumn(String matrixHeading) {
        this.matrixHeading = matrixHeading;
    }

    /** @return the exact §5.2 heading for this actor column */
    public String matrixHeading() {
        return matrixHeading;
    }
}
